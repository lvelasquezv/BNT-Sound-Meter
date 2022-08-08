package com.bnt.soundmeter;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.AlignmentSpan;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import static com.bnt.soundmeter.SoundMeterService.ACTION_START;
import static com.bnt.soundmeter.SoundMeterService.ACTION_STOP;


//https://stackoverflow.com/questions/8252813/android-media-player-decibel-reading
//https://stackoverflow.com/questions/9597767/decibel-sound-meter-for-android/9598848#9598848
//https://acousticstoday.org/wp-content/uploads/2017/06/2-faber.pdf
public class MainActivity extends AppCompatActivity {


  String TAG = "MAIN";
  TextView textViewDB;
  TextView historyTV;

  Thread thread;

  String momentoEvento;
  String historiaEvento;
  int eventos = 0;

  Context context;

  SoundMeterService soundMeterService = null;
  SoundMeterService mService;
  boolean mBound = false;

  //PERMISOS REQUERIDOS PARA LA APP
  private boolean permissionToRecordAccepted = false;
  public static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
  String[] PERMISSIONS = {
    Manifest.permission.RECORD_AUDIO,
    Manifest.permission.MANAGE_EXTERNAL_STORAGE,
  };

  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    switch (requestCode){
      case REQUEST_RECORD_AUDIO_PERMISSION:
        permissionToRecordAccepted  = (grantResults[0] == PackageManager.PERMISSION_GRANTED);
        break;
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

    context = this.getApplicationContext();
    MainViewModel viewModel = new ViewModelProvider(this).get(MainViewModel.class);

    //SI NO SE HAN CONCEDIDO LOS PERMISOS REQUERIDOS ENTONCES SOLICITELOS
    ActivityCompat.requestPermissions(this, PERMISSIONS,  REQUEST_RECORD_AUDIO_PERMISSION);
    Log.d(TAG, "PERMISOS CONECEDIDOS");

    textViewDB = findViewById(R.id.MainTVDecibelesValue);
    historyTV = findViewById(R.id.MainTVhistoria);

    LocalBroadcastManager.getInstance(context).registerReceiver(new BroadcastReceiver() {
      @SuppressLint("DefaultLocale")
      @Override
      public void onReceive(Context context, Intent intent) {

        Double splDb = intent.getDoubleExtra(SoundMeterService.EXTRA_SPLDB, 0);
        if(splDb.isNaN() || splDb.isInfinite()){splDb = 0.0;}
        //EVENTO DE RUIDO
        if(splDb >= 80){
          eventos ++;
          momentoEvento = "Evento " + eventos + ": " + String.format("%.0f", splDb) + " dB SPL " + getDate();
          Log.d("SMS",momentoEvento);
          if(historiaEvento == null || historiaEvento.isEmpty()){
            historiaEvento = momentoEvento;
          }else{
            historiaEvento += "\n" + momentoEvento;
          }
          makeToast(momentoEvento, context);
          historyTV.setText(historiaEvento);
        }
        Log.d("updateTv", String.format("%.0f", splDb) + " dB SPL");
        textViewDB.setText(String.format("%.0f", splDb));

      }
    }, new IntentFilter(SoundMeterService.ACTION_SMS_BROADCAST));

    bindService(new Intent(this,
      SoundMeterService.class), mConnection, Context.BIND_AUTO_CREATE);

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
      mBound = false;
      soundMeterService = null;
    }
  };


  @Override
  public void onBackPressed(){
    super.onPause();
    //super.onBackPressed();
  }

  public void onResume(){
    super.onResume();
    startSMS();
  }

  public void onPause(){
    super.onPause();
  }

  public void onDestroy(){
    unbindService(mConnection);
    mBound = false;
    stopSMS();
    deleteCache(this.getApplicationContext());
    super.onDestroy();
  }


  public void startSMS(){

    if (thread == null) {
      thread = new Thread(new Runnable() {
        @Override
        public void run() {
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
      });
      thread.start();
      Log.d(TAG, "start runner()");
    }
  }

  public void stopSMS(){
    Intent stopServiceIntent = new Intent(context, SoundMeterService.class);
    stopServiceIntent.putExtra("EXTRA_INTENT_CALLER", "MAIN_ACTIVITY");
    stopServiceIntent.setAction(ACTION_STOP);
    this.stopService(stopServiceIntent);
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
  //TODO CHANGE GTM FOR ANOTHER TIME ZONES
  public static String getDate(){
    Calendar calendar = new GregorianCalendar(TimeZone.getTimeZone("GMT-5"));
    int ano = calendar.get(Calendar.YEAR);
    int mes = calendar.get(Calendar.MONTH) + 1;
    String mes_string;
    if(mes <= 9){mes_string = "0"+ mes;}else{mes_string = String.valueOf(mes);}
    int dia = calendar.get(Calendar.DAY_OF_MONTH);
    String dia_string;
    if(dia <= 9){dia_string = "0"+ dia;}else{dia_string = String.valueOf(dia);}
    int hora = calendar.get(Calendar.HOUR_OF_DAY);
    int min = calendar.get(Calendar.MINUTE);
    String min_string;
    if(min <= 9){min_string = "0"+ min;}else{min_string = String.valueOf(min);}
    int sec = calendar.get(Calendar.SECOND);
    String sec_string;
    if(sec <= 9){sec_string = "0"+ sec;}else{sec_string = String.valueOf(sec);}
    return dia_string+"/"+mes_string+"/"+ano+" "+hora+":"+min_string+":"+sec_string;
  }

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
      for (int i = 0; i < children.length; i++) {
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

}


