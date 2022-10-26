package com.bnt.soundmeter;

import java.io.UnsupportedEncodingException;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import java.util.HashMap;
import java.util.Map;

public class Eventos {

  public String id;
  public String initialDate;
  public String lastDate;
  public int eventos = 0;
  public Double maxDb = 0.0;
  public Float Frec = 0.0f;

  public void setInit(){
    this.id = "0";
    this.initialDate = null;
    this.lastDate = null;
    this.eventos = 0;
    this.maxDb = 0.0;
    this.Frec = 0.0f;
  }

  public void setMaxDb(Double db)   { this.maxDb = db;  }
  public void setFrec(Float frec){ this.Frec = frec; }
  public void setId(String id) {
    if(id != null && !id.isEmpty() && id.length() > 3){
      this.id = sha256Hash(id+"BNT_EA_1234*");
    }else{
      this.id = "0";
    }
  }
  public void setInitialDate(String initialDate){
    if(this.initialDate == null || this.initialDate.isEmpty()){
      this.initialDate = initialDate;
    }
  }
  public void setLastDate(String lastDate){ this.lastDate = lastDate; }

  public String getId()           {   return id;            }
  public String getInitialDate()  {   return initialDate;   }
  public String getLastDate()     {   return lastDate;      }
  public int getEventos()         {   return eventos;       }
  public Double getMaxDb()        {   return maxDb;         }
  public Float getFrec()          {   return Frec;          }


  public void addEventos() {    this.eventos += 1;  }

  public Map<String, String> getData(){
    Map<String, String> data = new HashMap<String,String>();
    data.put("ID", this.id);
    data.put("InitialDate", this.initialDate);
    data.put("LastDate", this.lastDate);
    data.put("Events", String.valueOf(this.eventos));
    return data;
  }

  //FUNCION PARA TRANSFORMAR LA CADENA DE TEXTO EN SHA256
  //...
  public String sha256Hash( String toHash ) {
    String hash = null;
    try{
      MessageDigest digest = MessageDigest.getInstance( "SHA-256" );
      byte[] bytes = toHash.getBytes("UTF-8");
      digest.update(bytes, 0, bytes.length);
      bytes = digest.digest();
      // This is ~55x faster than looping and String.formating()
      hash = bytesToHex( bytes );
    } catch( NoSuchAlgorithmException e )    {
      e.printStackTrace();
    }
    catch( UnsupportedEncodingException e )    {
      e.printStackTrace();
    }
    return hash;
  }

  // http://stackoverflow.com/questions/9655181/convert-from-byte-array-to-hex-string-in-java
  final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();
  public static String bytesToHex( byte[] bytes )  {
    char[] hexChars = new char[ bytes.length * 2 ];
    for( int j = 0; j < bytes.length; j++ )    {
      int v = bytes[ j ] & 0xFF;
      hexChars[ j * 2 ] = hexArray[ v >>> 4 ];
      hexChars[ j * 2 + 1 ] = hexArray[ v & 0x0F ];
    }
    return new String( hexChars );
  }
  //...

  public String toString(){
    return "id = " + this.id + " - initial date = " + this.initialDate + " - last date = " +
      this.lastDate + " - total events = " + this.eventos + " - Max Db SPL = " + this.maxDb +
      " - with Frequency = " + this.Frec;
  }

  public boolean hasData(){
    return (!this.id.equals("0")) &&  (this.eventos > 0) &&  (this.initialDate != null) && (this.lastDate != null);
  }

  public boolean hasMaxs(){
    return (this.maxDb > 0) && (this.Frec > 0.0f);
  }

}
