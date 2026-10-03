package com.leaden1.bluetoothvolumediagnosticv8;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.media.*;
import android.os.*;
import android.view.KeyEvent;
import android.widget.*;
import androidx.core.content.ContextCompat;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity {
 private static final int REQ=801;
 private BluetoothAdapter adapter; private BluetoothA2dp a2dp; private AudioManager audio; private Handler h=new Handler();
 private TextView status,dev,out,vol,route,key,last,hist; private int lastVol=-1; private boolean registered;
 private final BluetoothProfile.ServiceListener profile=new BluetoothProfile.ServiceListener(){
  public void onServiceConnected(int p,BluetoothProfile proxy){if(p==BluetoothProfile.A2DP){a2dp=(BluetoothA2dp)proxy;log("A2DP_PROXY_CONECTADO");refresh();}}
  public void onServiceDisconnected(int p){if(p==BluetoothProfile.A2DP){a2dp=null;log("A2DP_PROXY_DESCONECTADO");refresh();}}
 };
 private final BroadcastReceiver rx=new BroadcastReceiver(){public void onReceive(Context c,Intent i){
  String a=i.getAction(); if(a==null)return;
  if(AudioManager.VOLUME_CHANGED_ACTION.equals(a)||"android.media.STREAM_VOLUME_CHANGED_ACTION".equals(a)){
   int s=i.getIntExtra(AudioManager.EXTRA_VOLUME_STREAM_TYPE,-1),n=i.getIntExtra(AudioManager.EXTRA_VOLUME_STREAM_VALUE,-1),p=i.getIntExtra(AudioManager.EXTRA_PREV_VOLUME_STREAM_VALUE,-1);
   log("VOLUME_BROADCAST stream="+s+" "+p+" -> "+n+" | MUSIC="+music()); refresh();
  } else if(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED.equals(a)){log("A2DP_CONNECTION_STATE="+i.getIntExtra(BluetoothProfile.EXTRA_STATE,-1));refresh();}
  else if(BluetoothAdapter.ACTION_STATE_CHANGED.equals(a)){log("BT_STATE="+i.getIntExtra(BluetoothAdapter.EXTRA_STATE,-1));refresh();}
 }};
 private final AudioDeviceCallback audioCb=new AudioDeviceCallback(){
  public void onAudioDevicesAdded(AudioDeviceInfo[] ds){for(AudioDeviceInfo d:ds)log("AUDIO_DEVICE_ADDED "+desc(d));refresh();}
  public void onAudioDevicesRemoved(AudioDeviceInfo[] ds){for(AudioDeviceInfo d:ds)log("AUDIO_DEVICE_REMOVED "+desc(d));refresh();}
 };
 private final Runnable poll=new Runnable(){public void run(){int v=music();if(v>=0&&lastVol>=0&&v!=lastVol)log("VOLUME_POLL "+lastVol+" -> "+v+(v>lastVol?" (UP)":" (DOWN)"));lastVol=v;refresh();h.postDelayed(this,500);}};
 private boolean btOK(){return Build.VERSION.SDK_INT<31||checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED;}
 private int music(){try{return audio.getStreamVolume(AudioManager.STREAM_MUSIC);}catch(Exception e){return -1;}}
 private int max(){try{return audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);}catch(Exception e){return -1;}}
 private String desc(AudioDeviceInfo d){String n="";try{n=String.valueOf(d.getProductName());}catch(Exception ignored){}String a="";try{a=d.getAddress();}catch(Exception ignored){}return "type="+d.getType()+" "+type(d.getType())+" name="+n+" addr="+a;}
 private String type(int t){if(t==AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)return"BLUETOOTH_A2DP";if(t==AudioDeviceInfo.TYPE_BLUETOOTH_SCO)return"BLUETOOTH_SCO";if(t==AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)return"BUILTIN_SPEAKER";return"TYPE_"+t;}
 private String bn(BluetoothDevice d){try{return d.getName();}catch(Exception e){return"?";}}
 private String ba(BluetoothDevice d){try{return d.getAddress();}catch(Exception e){return"?";}}
 private void refresh(){
  if(status==null)return;
  if(adapter==null)status.setText("BLUETOOTH: NO DISPONIBLE");else if(!btOK())status.setText("BLUETOOTH: FALTA PERMISO CONNECT");else status.setText("BLUETOOTH: "+(adapter.isEnabled()?"ENCENDIDO":"APAGADO"));
  StringBuilder b=new StringBuilder("DISPOSITIVOS A2DP:\\n");boolean found=false;
  if(a2dp!=null&&btOK())try{for(BluetoothDevice d:a2dp.getConnectedDevices()){found=true;b.append("• ").append(bn(d)).append(" | ").append(ba(d)).append(" | STATE=").append(a2dp.getConnectionState(d)).append("\\n");}}catch(Exception e){b.append("ERROR ").append(e.getClass().getSimpleName());}
  if(!found)b.append("— ninguno");dev.setText(b.toString());
  boolean a2=false;StringBuilder o=new StringBuilder();
  try{AudioDeviceInfo[] ds=audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS);o.append("SALIDAS (").append(ds.length).append("):\\n");for(AudioDeviceInfo d:ds){o.append("• ").append(desc(d)).append("\\n");if(d.getType()==AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)a2=true;}}catch(Exception e){o.append("ERROR ").append(e.getClass().getSimpleName());}
  out.setText(o.toString()); vol.setText("STREAM_MUSIC: "+music()+" / "+max()); route.setText("AVRCP/ABSOLUTE VOLUME: NO API PÚBLICA DIRECTA\\nV8 observa A2DP/AudioDeviceInfo/AudioManager; no accede al estado AVRCP privado."); 
  if(a2){} 
 }
 public boolean dispatchKeyEvent(KeyEvent e){if(e.getAction()==KeyEvent.ACTION_DOWN){int c=e.getKeyCode();if(c==KeyEvent.KEYCODE_VOLUME_UP||c==KeyEvent.KEYCODE_VOLUME_DOWN||c==KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE||c==KeyEvent.KEYCODE_MEDIA_PLAY||c==KeyEvent.KEYCODE_MEDIA_PAUSE){String n=KeyEvent.keyCodeToString(c);key.setText("ÚLTIMO KEY EVENT: "+n);log("KEY_DOWN: "+n+" deviceId="+e.getDeviceId()+" source=0x"+Integer.toHexString(e.getSource()));refresh();}}return super.dispatchKeyEvent(e);}
 private void log(String s){String x=new SimpleDateFormat("HH:mm:ss.SSS").format(new Date())+"  "+s;if(last==null)return;last.setText("ÚLTIMO EVENTO: "+s);if(hist!=null){String old=hist.getText().toString();hist.setText(x+"\\n"+old);}}
 private TextView t(String s,int size){TextView x=new TextView(this);x.setText(s);x.setTextSize(size);x.setPadding(8,6,8,6);return x;}
 private void ui(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(16,16,16,16);
  TextView title=t("LEADEN1 - AVRCP VOLUME DIAGNOSTIC V8",21);title.setTypeface(null,1);l.addView(title);
  status=t("● V8 — MONITOR AVRCP/A2DP",16);l.addView(status);dev=t("DISPOSITIVOS A2DP:",16);l.addView(dev);out=t("SALIDAS:",14);l.addView(out);vol=t("STREAM_MUSIC: —",16);l.addView(vol);route=t("AVRCP/ABSOLUTE VOLUME: —",14);l.addView(route);key=t("ÚLTIMO KEY EVENT: —",16);l.addView(key);last=t("ÚLTIMO EVENTO: —",14);l.addView(last);
  Button r=new Button(this);r.setText("ACTUALIZAR DIAGNÓSTICO");r.setOnClickListener(v->{log("MANUAL_REFRESH");refresh();});l.addView(r);
  Button c=new Button(this);c.setText("LIMPIAR HISTORIAL");c.setOnClickListener(v->hist.setText(""));l.addView(c);
  hist=t("",12);ScrollView s=new ScrollView(this);s.addView(hist);l.addView(s,new LinearLayout.LayoutParams(-1,0,1));setContentView(l);
 }
 protected void onCreate(Bundle b){super.onCreate(b);audio=(AudioManager)getSystemService(AUDIO_SERVICE);ui();
  if(Build.VERSION.SDK_INT>=31&&!btOK())requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT,Manifest.permission.BLUETOOTH_SCAN},REQ);
  try{adapter=BluetoothAdapter.getDefaultAdapter();if(adapter!=null&&btOK())adapter.getProfileProxy(this,profile,BluetoothProfile.A2DP);}catch(Exception e){log("BT_INIT_ERROR "+e);}
  try{IntentFilter f=new IntentFilter();f.addAction(AudioManager.VOLUME_CHANGED_ACTION);f.addAction("android.media.STREAM_VOLUME_CHANGED_ACTION");f.addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED);f.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);ContextCompat.registerReceiver(this,rx,f,ContextCompat.RECEIVER_EXPORTED);registered=true;log("RECEIVER_REGISTRADO_OK");}catch(Exception e){log("REGISTER_RECEIVER_ERROR "+e.getClass().getSimpleName()+": "+e.getMessage());}
  try{audio.registerAudioDeviceCallback(audioCb,h);}catch(Exception e){log("AUDIO_CALLBACK_ERROR "+e);}
  log("APP_INICIADA_V8");h.post(poll);
 }
 protected void onDestroy(){h.removeCallbacksAndMessages(null);try{audio.unregisterAudioDeviceCallback(audioCb);}catch(Exception ignored){}if(registered)try{unregisterReceiver(rx);}catch(Exception ignored){}if(a2dp!=null&&adapter!=null&&btOK())try{adapter.closeProfileProxy(BluetoothProfile.A2DP,a2dp);}catch(Exception ignored){}super.onDestroy();}
}