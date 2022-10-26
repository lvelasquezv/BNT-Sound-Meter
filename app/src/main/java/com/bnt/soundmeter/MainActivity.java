package com.bnt.soundmeter;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.text.InputFilter;
import android.text.InputType;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.AlignmentSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.File;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

import static com.bnt.soundmeter.SoundMeterService.ACTION_START;
import static com.bnt.soundmeter.SoundMeterService.ACTION_STOP;


//https://stackoverflow.com/questions/8252813/android-media-player-decibel-reading
//https://stackoverflow.com/questions/9597767/decibel-sound-meter-for-android/9598848#9598848
//https://acousticstoday.org/wp-content/uploads/2017/06/2-faber.pdf
public class MainActivity extends AppCompatActivity {


  String TAG = "MAIN";

  ConstraintLayout constraintLayout;

  TextView textViewDB;
  TextView historyTV;
  TextView frequencyTV;
  EditText etLimite;
  Boolean cambiarLimite = true;

  Thread thread;

  String momentoEvento;
  String historiaEvento;
  int evento = 0;
  Double limite = 50.0;
  String fecha_sin, fecha_con;


  Eventos eventos = new Eventos();

  Context context;

  SoundMeterService soundMeterService = null;
  SoundMeterService mService;
  boolean mBound = false;

  //PERMISOS REQUERIDOS PARA LA APP
  private boolean permissionToRecordAccepted = false;
  public static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
  String[] PERMISSIONS = {
    Manifest.permission.RECORD_AUDIO,
//    Manifest.permission.MANAGE_EXTERNAL_STORAGE,
  };

  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
      permissionToRecordAccepted = (grantResults[0] == PackageManager.PERMISSION_GRANTED);
    }
    if (!permissionToRecordAccepted) {
      finish();
    }
  }

  @SuppressLint("SetTextI18n")
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    Log.d(TAG, "onCreate INIT");

    context = this.getApplicationContext();

    //MainViewModel viewModel = new ViewModelProvider(this).get(MainViewModel.class);

    //SI NO SE HAN CONCEDIDO LOS PERMISOS REQUERIDOS ENTONCES SOLICITELOS
    ActivityCompat.requestPermissions(this, PERMISSIONS,  REQUEST_RECORD_AUDIO_PERMISSION);
    Log.d(TAG, "PERMISOS CONECEDIDOS");

    textViewDB = findViewById(R.id.MainTVDecibelesValue);
    frequencyTV = findViewById(R.id.MainTVHErtzValue);
    historyTV = findViewById(R.id.MainTVhistoria);
    etLimite = findViewById(R.id.MainETLimite);
    etLimite.setFilters(new InputFilter[]{new InputFilterMinMax("1", "85")});
    etLimite.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        EditText et = (EditText) view;
        if( !cambiarLimite ){
          String mensaje = "Detenga la aplicación para poder cambiar el límite de eventos";
          makeToast(mensaje);
        }
      }
    });

    constraintLayout = findViewById(R.id.constraintLayout);
    setupUI(constraintLayout);

    //OBTENER ID DE LOGIN
    /*
    Intent intent = getIntent();
    String fromIntent = intent.getStringExtra("EXTRA_FROM_INTENT");
    if(fromIntent.equals("LOGIN")){
      Log.d(TAG, "From Intent = " + fromIntent);
      Log.d(TAG, "User Identification = " + intent.getStringExtra("EXTRA_ID"));
      eventos.setId(Objects.requireNonNull(intent.getStringExtra("EXTRA_ID")));
    }
     */

    LocalBroadcastManager.getInstance(context).registerReceiver(soundMeterReceiver,
      new IntentFilter(SoundMeterService.ACTION_SMS_BROADCAST));

    bindService(new Intent(this, SoundMeterService.class), mConnection, Context.BIND_AUTO_CREATE);
  }

  //RECEIVER PARA SPL & Hz
  public BroadcastReceiver soundMeterReceiver = new BroadcastReceiver() {
    @SuppressLint("DefaultLocale")
    @Override
    public void onReceive(Context context, Intent intent) {
      Double splDb = intent.getDoubleExtra(SoundMeterService.EXTRA_SPLDB, 0);
      float frequency = intent.getFloatExtra(SoundMeterService.EXTRA_FREQ, 0);
      if(splDb.isNaN() || splDb.isInfinite()){splDb = 0.0;}
      //EVENTO DE RUIDO
      if(splDb >= limite && frequency <= 1500.0f){

        if(eventos.maxDb < splDb){ eventos.setMaxDb(splDb);  eventos.setFrec(frequency); }

        fecha_sin = getDate(2);
        fecha_con = getDate(1);
        eventos.setInitialDate(fecha_con);
        eventos.setLastDate(fecha_con);
        evento ++;
        eventos.addEventos();

        momentoEvento = evento + ": " + String.format("%.0f", splDb) + "dB, " + String.format("%.0f", frequency) + "Hz, " + fecha_sin;
        Log.d("SMS",momentoEvento);
        if(historiaEvento == null || historiaEvento.isEmpty()){
          historiaEvento = momentoEvento;
        }else{
          historiaEvento += "\n" + momentoEvento;
        }
        makeToast(momentoEvento);
        historyTV.setText(historiaEvento);
      }
      Log.d("updateTv", String.format("%.0f", splDb) + " dB SPL & " + String.format("%.0f", frequency) + " Hz");
      textViewDB.setText(String.format("%.0f", splDb));
      frequencyTV.setText(String.format("%.0f", frequency));
    }
  };

  public void destroyReceiver(){
    Log.d(TAG, "destroyReceiver INIT");
    LocalBroadcastManager.getInstance(this).unregisterReceiver(soundMeterReceiver);
  }

  private ServiceConnection mConnection = new ServiceConnection() {
    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
      // We've bound to LocalService, cast the IBinder and get LocalService instance
      SoundMeterService.LocalBinder binder = (SoundMeterService.LocalBinder) service;
      mService = binder.getService();
      mBound = true;
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
      Log.d(TAG, "onServiceDisconnected INIT");
      mBound = false;
      soundMeterService = null;
    }
  };

  @Override
  public void onBackPressed(){
    Log.d(TAG, "onBackPressed INIT");
    //super.onBackPressed();
    moveTaskToBack(true);
  }


  @Override
  public void onResume(){
    Log.d(TAG, "OnResume INIT");
    super.onResume();
  }

  @Override
  public void onPause(){
    Log.d(TAG, "OnPause INIT");
    super.onPause();
    if (this.isFinishing()){
      Log.d(TAG, "App is finishing");
    }else{
      Log.d(TAG, "App is not finishing");
    }
  }

  @Override
  protected void onStop() {
    super.onStop();
    Log.d(TAG, "onStop Init");
  }

  @Override
  protected void onRestart() {
    super.onRestart();
    Log.d(TAG, "onRestart Init");
  }

  @Override
  public void onDestroy(){
    Log.d(TAG, "OnDestroy INIT");
    detenerApp();
    super.onDestroy();
  }

  //INICIO DE FUNCIONES PARA DETENER LA APLICACION
  //DETENER EL MAIN THREAD
  public void stopMainThread(){
    if(thread != null && !thread.isInterrupted()){
      try{
        Log.d(TAG, "StopMainThread INIT");
        thread.interrupt();
        thread = null;
      }catch(Exception e){
        Log.d(TAG, "ERROR DETENIENDO THREAD " + Arrays.toString(e.getStackTrace()));
      }
    }
  }

  //UNBIND EL SERVICIO DE SOUND METER
  public void unbindService(){
    if(mBound){
      Log.d(TAG, "unbindService INIT");
      try{
        unbindService(mConnection);
      }catch(Exception e){
        e.printStackTrace();
      }

    }
  }

  //DETENER EL SOUND METER
  public void stopSoundMeter(){
    if(mBound) {
      try {
        Log.d(TAG, "stopSoundMeter INIT");
        stopSMS();
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }

  //LLAMADO A FUNCIONES PARA DETENER SERVICIOS
  public void detenerApp(){
    stopSoundMeter();
    unbindService();
    stopMainThread();
    destroyReceiver();
    logMaxEventsFirebase(); //ENVIAR EVENTOS MAXIMOS AL LOG DE FIREBASE
    eventos.setInit();
    //deleteCache(this.getApplicationContext());
  }

  //INICIAR SOUND METER
  public void startSMS(){
    if (thread == null) {
      thread = new Thread(new Runnable() {
        @Override
        public void run() {
          if(!thread.isInterrupted()){
            Log.d(TAG, "SE INICIA SERVICE INTENT");
            Intent startServiceIntent = new Intent(context, SoundMeterService.class);
            startServiceIntent.putExtra("EXTRA_INTENT_CALLER", "MAIN_ACTIVITY");
            startServiceIntent.setAction(ACTION_START);
            try {
              //https://issuetracker.google.com/issues/76112072
              context.startService(startServiceIntent);
            }catch (Exception e){
              Log.d(TAG,"ERROR EN INICIO DE SERVICIO " + e.getMessage());
            }
          }
        }
      });
      thread.start();
      Log.d(TAG, "start runner()");
    }
  }

  //ENVIAR SEÑAL PARA DETENER SOUND METER
  public void stopSMS(){
    Intent stopServiceIntent = new Intent(context, SoundMeterService.class);
    stopServiceIntent.putExtra("EXTRA_INTENT_CALLER", "MAIN_ACTIVITY");
    stopServiceIntent.setAction(ACTION_STOP);
    context.startService(stopServiceIntent);
  }

  //FUNCION PARA ESCRIBIR TOAST CON TEXTO ALINEADO AL CENTRO
  public void makeToast(String mensaje){
    Spannable centeredText = new SpannableString(mensaje);
    centeredText.setSpan(
      new AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER),
      0, mensaje.length() - 1,
      Spannable.SPAN_INCLUSIVE_INCLUSIVE
    );
    Toast toast = Toast.makeText(this,centeredText,Toast.LENGTH_SHORT);
    toast.show();
  }

  //ACTUAL DATE
  //https://stackoverflow.com/questions/6014903/getting-gmt-time-with-android
  public static String getDate(int option){
    ZonedDateTime today = ZonedDateTime.now();
    Log.d("MAIN", "ZDT : " + today);
    String ano_string = String.valueOf(today.getYear());
    int mes = today.getMonthValue();
    String mes_string;
    if(mes <= 9){ mes_string = "0"+ mes;}else{  mes_string = String.valueOf(mes);}
    int dia = today.getDayOfMonth();
    String dia_string;
    if(dia <= 9){dia_string = "0"+ dia;}else{dia_string = String.valueOf(dia);}
    String hora_string = String.valueOf(today.getHour());
    int min = today.getMinute();
    String min_string;
    if(min <= 9){min_string = "0"+ min;}else{min_string = String.valueOf(min);}
    int sec = today.getSecond();
    String sec_string;
    if(sec <= 9){sec_string = "0"+ sec;}else{sec_string = String.valueOf(sec);}
    if(option == 1){
      return dia_string+"/"+mes_string+"/"+ano_string+" "+hora_string+":"+min_string+":"+sec_string;
    }else{
      return dia_string+"/"+mes_string+" "+hora_string+":"+min_string+":"+sec_string;
    }
  }

  /*
  //INTENTO DE BORRAR EL CACHE DE LA APLICACION
  public void deleteCache(Context context) {
    try {
      File dir = context.getCacheDir();
      if(deleteDir(dir)){
       Log.d(TAG,"CACHE BORRADO");
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public boolean deleteDir(File dir) {
    if (dir != null && dir.isDirectory()) {
      String[] children = dir.list();
      for (int i = 0; i < Objects.requireNonNull(children).length; i++) {
        boolean success = deleteDir(new File(dir, children[i]));
        if (!success) {
          return false;
        }
      }
      return dir.delete();
    } else if(dir!= null && dir.isFile()) {
      return dir.delete();
    } else {
      return false;
    }
  }
   */

  public void readLimit(){

    limite = Double.parseDouble(etLimite.getText().toString());
  }

  //FUNCIONES DE LOS BOTONES
  //INICIAR
  public void smsStart(View view) {
    Log.d(TAG, "Boton iniciar");
    readLimit();
    startSMS();

    //SE PREVIENE LA EDICION DEL LIMITE PARA EVENTOS
    cambiarLimite = false;
    etLimite.setInputType(InputType.TYPE_NULL);
  }

  //DETENER
  public void smsPause(View view) {
    Log.d(TAG, "Boton Pause");
    stopSMS();
    stopMainThread();

    //SE PERMITE LA EDICION DEL LIMITE PARA EVENTOS
    cambiarLimite = true;
    etLimite.setInputType(InputType.TYPE_CLASS_NUMBER);
  }

  //BORRAR
  public void logErase(View view) {
    Log.d(TAG, "Boton Borrar");
    borrarRegistro();
  }

  public void borrarRegistro(){
    evento = 0;
    historiaEvento = null;
    historyTV.setText("");
  }

  //ENVIAR & SALIR
  public void buttonSend(View view){
    //PAUSAR
    stopSMS();
    stopMainThread();
    //ENVIAR DATOS A FIREBASE
    askForID();
    //BORRAR REGISTROS
    borrarRegistro();
  }

  //SALIR
  public void buttonExit(View view){
    //SALIR DE LA APP
    detenerApp();
    moveTaskToBack(true);
  }

  //GO TO LOG IN ACTIVITY
  public void goToLogIn(){
    Intent intentLogIn = new Intent(this, login.class);
    intentLogIn.putExtra("EXTRA_FROM_INTENT", "MAIN");
    startActivity(intentLogIn);
  }

  @Override
  public void onPointerCaptureChanged(boolean hasCapture) {
    super.onPointerCaptureChanged(hasCapture);
  }

  //FILTRO PARA EL INGRESO DEL LIMITE DE MEDICION DE SPL
  public static class InputFilterMinMax implements InputFilter {
    private int min, max;
    public InputFilterMinMax(int min, int max) {
      this.min = min;
      this.max = max;
    }
    public InputFilterMinMax(String min, String max) {
      this.min = Integer.parseInt(min);
      this.max = Integer.parseInt(max);
    }

    @Override
    public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
      try {
        int input = Integer.parseInt(dest.toString() + source.toString());
        if (isInRange(min, max, input))
          return null;
      } catch (NumberFormatException nfe) {
        nfe.printStackTrace();
      }
      return "";
    }

    private boolean isInRange(int a, int b, int c) {
      return b > a ? c >= a && c <= b : c >= b && c <= a;
    }
  }

  public void goToBNTLink(View view){
    String url = "https://www.bionanotechsas.com/";
    Intent browse = new Intent( Intent.ACTION_VIEW , Uri.parse( url ) );
    startActivity( browse );
  }


  public void askForID() {
    Log.d(TAG, eventos.toString());

    AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.MyDialogTheme);
    // Get the layout inflater
    LayoutInflater inflater = this.getLayoutInflater();
    View mView = inflater.inflate(R.layout.dialog_signin, null);
    final EditText editTextID = (EditText) mView.findViewById(R.id.idUser);
    // Inflate and set the layout for the dialog
    // Pass null as the parent view because its going in the dialog layout
    builder.setView(mView)
      // Add action buttons
      .setPositiveButton("ENVIAR", new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int id) {
          String userID = editTextID.getText().toString();
          eventos.setId(userID);
          sendDocToFirebase();
        }
      })
      .setNegativeButton("CANCELAR", new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int id) {
          Log.d("ALERT DIALOG", "CANCEL");
          //SALIR DE LA APP
          detenerApp();
          moveTaskToBack(true);
        }
      });
    builder.show();
  }


  public void sendDocToFirebase(){
    //ENVIAR DOCUMENTO SI HAY DATOS
    if(eventos.hasData()) {
      Log.d("FIREBASE", "EVIANDO DATOS A SERVIDOR...");
      FirebaseFirestore db = FirebaseFirestore.getInstance();
      Map<String, String> allDataValues = eventos.getData();
      db.collection("DATOS_SM")
        .add(allDataValues)
        .addOnSuccessListener(new OnSuccessListener<DocumentReference>() {
          @Override
          public void onSuccess(DocumentReference documentReference) {
            Log.d("FIREBASE", "DocumentSnapshot written with ID: " + documentReference.getId());
          }
        })
        .addOnFailureListener(new OnFailureListener() {
          @Override
          public void onFailure(@NonNull Exception e) {
            Log.d("FIREBASE", "Error adding document", e);
          }
        });
    }else{
      Log.d("FIREBASE", "SIN DATOS PARA EL ENVIO");
    }
    //SALIR DE LA APP
    detenerApp();
    moveTaskToBack(true);
  }

  //ENVIAR EVENTOS MAXIMOS (dB SPL maximo Y FRECUENCIA EN ESE MOMENTO) A FIREBASE
  public void logMaxEventsFirebase(){
    if(eventos.hasMaxs()){
      Log.d("FIREBASE", "ENVIANDO EVENTOS MAXIMOS");
      String db = String.valueOf(eventos.getMaxDb());
      String frec = String.valueOf(eventos.getFrec());
      FirebaseAnalytics mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);
      Bundle params = new Bundle();
      params.putString("dB", db);
      params.putString("Frec", frec);
      mFirebaseAnalytics.logEvent("max_values", params);
    }else{
      Log.d("FIREBASE","SIN EVENTOS MAXIMOS");
    }
  }


  //La siguientes dos funciones esconden el teclado cuando la pantalla es tocada
  //...
  public static void hideSoftKeyboard(Activity activity) {
    InputMethodManager inputMethodManager =
      (InputMethodManager) activity.getSystemService(
        Activity.INPUT_METHOD_SERVICE);
    if (inputMethodManager.isAcceptingText()) {
      inputMethodManager.hideSoftInputFromWindow(
        activity.getCurrentFocus().getWindowToken(),
        0);
    }
  }

  @SuppressLint("ClickableViewAccessibility")
  public void setupUI(View view) {
    // Set up touch listener for non-text box views to hide keyboard.
    if (!(view instanceof EditText)) {
      view.setOnTouchListener((v, event) -> {
        hideSoftKeyboard(MainActivity.this);
        return false;
      });
    }
    //If a layout container, iterate over children and seed recursion.
    if (view instanceof ViewGroup) {
      for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
        View innerView = ((ViewGroup) view).getChildAt(i);
        setupUI(innerView);
      }
    }
  }
  //...


}


