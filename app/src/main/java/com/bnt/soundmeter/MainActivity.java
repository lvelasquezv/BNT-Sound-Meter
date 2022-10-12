package com.bnt.soundmeter;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.text.InputFilter;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.AlignmentSpan;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Objects;

import static com.bnt.soundmeter.SoundMeterService.ACTION_START;
import static com.bnt.soundmeter.SoundMeterService.ACTION_STOP;


//https://stackoverflow.com/questions/8252813/android-media-player-decibel-reading
//https://stackoverflow.com/questions/9597767/decibel-sound-meter-for-android/9598848#9598848
//https://acousticstoday.org/wp-content/uploads/2017/06/2-faber.pdf
public class MainActivity extends AppCompatActivity {


  String TAG = "MAIN";
  TextView textViewDB;
  TextView historyTV;
  TextView frequencyTV;
  EditText etLimite;

  Thread thread;

  String momentoEvento;
  String historiaEvento;
  int eventos = 0;
  Double limite = 50.0;

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
        eventos ++;
        momentoEvento = eventos + ": " + String.format("%.0f", splDb) + "dB, " + String.format("%.0f", frequency) + "Hz, " +getDate();
        Log.d("SMS",momentoEvento);
        if(historiaEvento == null || historiaEvento.isEmpty()){
          historiaEvento = momentoEvento;
        }else{
          historiaEvento += "\n" + momentoEvento;
        }
        makeToast(momentoEvento, context);
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


  public void onResume(){
    Log.d(TAG, "OnResume INIT");
    super.onResume();
  }

  public void onPause(){
    Log.d(TAG, "OnPause INIT");
    super.onPause();
    if (this.isFinishing()){
      Log.d(TAG, "App is finishing");
    }else{
      Log.d(TAG, "App is not finishing");
    }
  }

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
      unbindService(mConnection);
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

  public void detenerApp(){
    stopSoundMeter();
    unbindService();
    stopMainThread();
    destroyReceiver();
    deleteCache(this.getApplicationContext());
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
  public static void makeToast(String mensaje, Context ctx){
    Spannable centeredText = new SpannableString(mensaje);
    centeredText.setSpan(
      new AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER),
      0, mensaje.length() - 1,
      Spannable.SPAN_INCLUSIVE_INCLUSIVE
    );
    Toast toast = Toast.makeText(ctx,centeredText,Toast.LENGTH_SHORT);
    toast.show();
  }

  //ACTUAL DATE
  //https://stackoverflow.com/questions/6014903/getting-gmt-time-with-android
  public static String getDate(){
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
    //return dia_string+"/"+mes_string+"/"+ano_string+" "+hora_string+":"+min_string+":"+sec_string;
    return dia_string+"/"+mes_string+" "+hora_string+":"+min_string+":"+sec_string;
  }

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

  //FUNCIONES DE LOS BOTONES
  //INICIAR
  public void smsStart(View view) {
    Log.d(TAG, "Boton iniciar");
    limite = Double.parseDouble(etLimite.getText().toString());
    startSMS();
  }

  //DETENER
  public void smsPause(View view) {
    Log.d(TAG, "Boton Pause");
    stopSMS();
    stopMainThread();
  }

  //BORRAR
  public void logErase(View view) {
    Log.d(TAG, "Boton Borrar");
    eventos = 0;
    historiaEvento = null;
    historyTV.setText("");
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

}


