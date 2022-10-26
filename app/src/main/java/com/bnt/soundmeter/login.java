package com.bnt.soundmeter;

import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;

import android.app.Activity;

import android.content.Intent;

import android.os.Bundle;

import android.util.Log;

import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

public class login extends AppCompatActivity {

  String TAG = "LogInAct";

  EditText idEditText;
  String id;

  public login() {
    super();
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_login);
    Log.d(TAG, "onCreate Init");

    setupUI(findViewById(R.id.constraintLayout));

    idEditText = findViewById(R.id.id);

  }

  @Override
  protected void onStart() {
    super.onStart();
    Log.d(TAG, "onStart Init");
  }

  @Override
  protected void onResume() {
    super.onResume();
    Log.d(TAG, "onResume Init");
  }

  @Override
  protected void onPause() {
    super.onPause();
    Log.d(TAG, "onPause Init");
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
  protected void onDestroy() {
    super.onDestroy();
    Log.d(TAG, "onDestroy Init");
  }

  @Override
  public void onBackPressed() {
    super.onBackPressed();
  }

  public void loginButton(View view){
    verifyId();
  }

  public void verifyId(){
    if(idEditText.getText() == null || idEditText.getText().toString().isEmpty()){
      id = "0";
    }else{
      id = idEditText.getText().toString();
    }
    id = id.replace(" " , "");
    id = id.replace("  ", "");
    id = id.replace("," , "");
    id = id.replace("." , "");

    Log.d(TAG, "user identification = " + id);

    Intent mainIntent = new Intent(this, MainActivity.class);
    mainIntent.putExtra("EXTRA_FROM_INTENT", "LOGIN");
    mainIntent.putExtra("EXTRA_ID", id);
    startActivity(mainIntent);

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
        hideSoftKeyboard(login.this);
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
  /*
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
   */

}