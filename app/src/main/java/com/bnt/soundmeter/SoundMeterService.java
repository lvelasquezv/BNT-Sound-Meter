package com.bnt.soundmeter;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import java.io.IOException;

import androidx.annotation.Nullable;

import androidx.core.app.NotificationCompat;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;



public class SoundMeterService extends Service{

  String TAG = "SMS";

  // Binder given to clients
  private final IBinder binder = new LocalBinder();

  public static String ACTION_START = "SMS_START";
  public static String ACTION_STOP = "SMS_STOP";

  Intent intent = new Intent(ACTION_SMS_BROADCAST);

  /////////////////////////////////////////////////////////////////
  // PRIVATE CONSTANTS
  private static final double MAX_REPORTABLE_AMP = 32767.0;
  private static final double MAX_REPORTABLE_DB = 90.3087;

  private static final int RECORDING_RATE = 44100;
  private static final int ENCODING = AudioFormat.ENCODING_PCM_16BIT;

  private MediaRecorder mRecorder;
  private AudioRecord audioRecord;

  private static String fileName = null;

  Thread runner;
  final Runnable splDb = new Runnable() {
    public void run() {
      if(runner != null){
        if(!runner.isInterrupted()){
          startRecorder();
          startAudioRecorder();
          soundDb(MAX_REPORTABLE_AMP);
          getBufferRead();
          sendBroadCast();
        }
      }
    }
  };
  final Handler mHandler = new Handler();

  private static double mEMA = 0.0;
  static final private double EMA_FILTER = 0.6;

  public static final String
    ACTION_SMS_BROADCAST = SoundMeterService.class.getName() + "SMSBroadcast",
    EXTRA_SPLDB = "EXTRA_SPLDB",
    EXTRA_FREQ = "EXTRA_FREQ";

  @Override
  public void onCreate() {
    super.onCreate();
    createNotification();
    fileName = getExternalCacheDir().getAbsolutePath();
    fileName += null;
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    createNotification();
    fileName = getExternalCacheDir().getAbsolutePath();
    fileName += null;
    int intToReturn = 0;
    if (intent.getAction().equals(ACTION_START)) {
      Log.d(TAG, "SMS INIT");
      if(runner == null){
        Log.d(TAG, "SMS START RUNNER THREAD");
        runner = new Thread(new Runnable() {
          @Override
          public void run() {
            try{
              //https://stackoverflow.com/questions/54607665/why-audiorecord-is-not-recording-sound-when-device-fall-asleep-even-when-i-use-w/54608175#54608175
              android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);
              while (runner != null && !runner.isInterrupted()) {
                try {
                  Thread.sleep(1000);
                  Log.i(TAG, "Tock");
                } catch (InterruptedException e) {
                  Log.d(TAG, "InterruptedException: " + android.util.Log.getStackTraceString(e));
                }
                mHandler.post(splDb);
              }
            }catch(Exception e){
              Log.d(TAG, "Error iniciando el audio recorder" + e.getMessage());
            }
          }
        });
        runner.start();
        Log.d(TAG, "start runner()");
        //intToReturn = START_STICKY;
        intToReturn = START_NOT_STICKY;
      }
    }else if(intent.getAction().equals(ACTION_STOP)){
      Log.d(TAG, "SMS FINISH");
      stopRecorder();
      stopForeground(true);
      stopSelfResult(startId);
      intToReturn = START_NOT_STICKY;
    }
    return intToReturn;
  }

  @Override
  public void onDestroy() {
    Log.d(TAG, "onDestroy Called, STOPING SERVICES");
    try {
      //Thread.sleep(5000);
      stopRecorder();
      stopForeground(true);
      stopSelf();
    } catch (Exception e) {
      e.printStackTrace();
    }

    super.onDestroy();
  }

  public class LocalBinder extends Binder {
    SoundMeterService getService() {
      // Return this instance of LocalService so clients can call public methods
      return SoundMeterService.this;
    }
  }
  @Nullable
  @Override
  public IBinder onBind(Intent intent) {
    return binder;
  }


  @Override
  public void onTaskRemoved(Intent rootIntent) {
    Log.d(TAG, "OnTaskRemoved Called, STOPING SERVICES");
    stopRecorder();
    stopSelf();
    super.onTaskRemoved(rootIntent);
  }


  private void createNotification() {

    String NOTIFICATION_CHANNEL_ID  = "com.bnt.soundmeter";
    String NOTIFICATION_CHANNEL_NAME = "SoundMeter Foreground Service";
    NotificationChannel channel = new NotificationChannel(
      NOTIFICATION_CHANNEL_ID,
      NOTIFICATION_CHANNEL_NAME,
      NotificationManager.IMPORTANCE_DEFAULT);

    ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).createNotificationChannel(channel);

    Intent notificationIntent = new Intent(this, MainActivity.class);
    PendingIntent pendingIntent = PendingIntent.getActivity(this,
      0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

    NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID);
    Notification notification = notificationBuilder.setOngoing(true)
      .setContentTitle("Sound Meter")
      .setContentText("")
      .setSmallIcon(R.drawable.notification_icon)
      //.setContentIntent(pendingIntent)
      .setTicker("")
      .build();

    startForeground(1, notification);

  }

  public void startRecorder(){
    if(mRecorder == null){
      mRecorder = new MediaRecorder();
      AudioManager aManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
      String audioSignal = aManager.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED);
      if(audioSignal == null){
        mRecorder.setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION);  //does not employ AGC or noise suppression
      }else{
        mRecorder.setAudioSource(MediaRecorder.AudioSource.UNPROCESSED);
      }

      mRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
      mRecorder.setOutputFile(fileName);
      mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
      mRecorder.setAudioEncodingBitRate(ENCODING);
      mRecorder.setAudioSamplingRate(RECORDING_RATE);

      try {
        mRecorder.prepare();
      }catch (IOException ioe) {
        Log.d(TAG, "IOException: " + android.util.Log.getStackTraceString(ioe));

      }catch (SecurityException e) {
        Log.d(TAG, "SecurityException: " + android.util.Log.getStackTraceString(e));
      }

      try{
        mRecorder.start();
      }catch (SecurityException e) {
        Log.d(TAG, "SecurityException: " + android.util.Log.getStackTraceString(e));
      }
    }
  }

  public void stopRecorder() {
    if (mRecorder != null) {
      Log.d(TAG, "SMS STOP RUNNER THREAD");
      try{
        runner.interrupt();
        runner = null;
        mRecorder.stop();     // stop recording
        mRecorder.reset();    // set state to idle
        mRecorder.release();  // release resources back to the system
        mRecorder = null;
        stopAudioRecorder();
      }catch(Exception e){
        Log.d(TAG, e.getMessage());
      }
    }
  }

  public double getAmplitude() {
    if (mRecorder != null) {
      return (mRecorder.getMaxAmplitude());
    }else {
      return 0;
    }
  }

  public double getAmplitudeEMA() {
    double amp =  getAmplitude();
    mEMA = EMA_FILTER * amp + (1.0 - EMA_FILTER) * mEMA;
    Log.d(TAG, "mEMA: " + mEMA);
    return mEMA;
  }

  //https://www.acoustic-glossary.co.uk/sound-pressure.htm
  //https://stackoverflow.com/questions/9597767/decibel-sound-meter-for-android/9598848#9598848
  //https://stackoverflow.com/questions/10668470/what-is-the-unit-of-the-returned-amplitude-of-getmaxamplitude-method
  /*The MediaRecorder.getMaxAmplitude() function returns unsigned 16-bit integer values (0-32767).
   *Those values are probably calculated by using abs() on -32768 … +32767, similar to the normal CD-quality sample values.
   *Negative amplitudes are just mirrored and therefore the amplitude is always positive.
   */
  //https://en.wikipedia.org/wiki/Sound_pressure
  //reference sound pressure = 0.00002
  //https://stackoverflow.com/questions/41798097/android-audiorecord-how-to-get-average-volume-for-a-minute
  // calculating the pascal pressure based on the idea that the max amplitude (between 0 and 32767) is
  // relative to the pressure
  //https://acousticstoday.org/wp-content/uploads/2017/06/2-faber.pdf
  public void soundDb(double ampl){
    double p = getAmplitude();
    Log.d("SoundDb", "Max Amplitude of recorder = " + p + " / reference sound pressure = " + ampl);
    double splDb = MAX_REPORTABLE_DB  + 20 * Math.log10(p / ampl);
    intent.putExtra(EXTRA_SPLDB, splDb);
  }

  public void sendBroadCast(){
    LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
  }


  //FREQUENCIES
  //https://stackoverflow.com/questions/16982623/android-app-to-record-sound-in-real-time-and-identify-frequency
  int blockSize = 2048;// = 256;
  private static final int RECORDER_SAMPLERATE = 8000;
  private static final int RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_MONO;
  private static final int RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
  int BufferElements2Rec = 1024; // want to play 2048 (2K) since 2 bytes we use only 1024
  int BytesPerElement = 2;

  final short[] buffer = new short[blockSize];
  final double[] toTransform = new double[blockSize];

  public void startAudioRecorder(){
    if(audioRecord == null){
      try{
        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC,
          RECORDER_SAMPLERATE, RECORDER_CHANNELS,
          RECORDER_AUDIO_ENCODING, BufferElements2Rec * BytesPerElement);
        audioRecord.startRecording();
      }catch(Exception e){
        e.printStackTrace();
      }
    }
  }

  public void stopAudioRecorder(){
    if(audioRecord != null){
      try{
        audioRecord.stop();
        audioRecord.release();
        audioRecord = null;
      }catch (Exception e){
        e.printStackTrace();
      }
    }
  }

  public void getBufferRead(){
    audioRecord.read(buffer, 0, blockSize);
    bufferToCalculate(buffer);
  }

  public void bufferToCalculate(short[] ...buffer){
    float freq = calculate(RECORDER_SAMPLERATE, buffer[0]);
    intent.putExtra(EXTRA_FREQ, freq);
    Log.d(TAG,"FREQUENCIES = " + freq);
  }


  public static float calculate(int sampleRate, short [] audioData) {
    int numSamples = audioData.length;
    int numCrossing = 0;
    for (int p = 0; p < numSamples-1; p++) {
      if ((audioData[p] > 0 && audioData[p + 1] <= 0) ||
        (audioData[p] < 0 && audioData[p + 1] >= 0)) {
        numCrossing++;
      }
    }
    float numSecondsRecorded = (float)numSamples/(float)sampleRate;
    float numCycles = numCrossing/2;
    float frequency = numCycles/numSecondsRecorded;

    return frequency;
  }
}
