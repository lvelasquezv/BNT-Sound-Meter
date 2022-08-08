package com.bnt.soundmeter;

import android.app.Application;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;

import static com.bnt.soundmeter.SoundMeterService.ACTION_STOP;

public class MainViewModel extends AndroidViewModel {


  public MainViewModel(@NonNull Application application) {
    super(application);
  }

  @Override
  protected void onCleared() {
    // Do your task here
    Log.d("MainViewModel", "OnCleared mainViewModel");
    /*
    Intent startServiceIntent = new Intent(getApplication().getApplicationContext(), SoundMeterService.class);
    startServiceIntent.putExtra("EXTRA_INTENT_CALLER", "MAIN_ACTIVITY");
    startServiceIntent.setAction(ACTION_STOP);

     */
    super.onCleared();
  }
}
