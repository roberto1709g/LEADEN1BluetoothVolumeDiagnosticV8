package com.leaden1.bluetoothdiagnostic.v8;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothClass;
import android.os.ParcelUuid;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.graphics.Color;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQ_BT = 6001;
    private static final String ACTION_VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION";
    private static final String EXTRA_VOLUME_STREAM_TYPE = "android.media.EXTRA_VOLUME_STREAM_TYPE";
    private static final String EXTRA_VOLUME_STREAM_VALUE = "android.media.EXTRA_VOLUME_STREAM_VALUE";
    private static final String EXTRA_PREV_VOLUME_STREAM_VALUE = "android.media.EXTRA_PREV_VOLUME_STREAM_VALUE";

    private AudioManager audioManager;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothA2dp a2dp;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView status, btInfo, audioInfo, volumeInfo, keyInfo, lastInfo, history;
    private final List<String> logs = new ArrayList<>();
    private int lastVolume = -1;
    private boolean receiverRegistered = false;

    private final BluetoothProfile.ServiceListener profileListener = new BluetoothProfile.ServiceListener() {
        @Override public void onServiceConnected(int profile, BluetoothProfile proxy) {
            if (profile == BluetoothProfile.A2DP) {
                a2dp = (BluetoothA2dp) proxy;
                log("A2DP_PROXY_CONECTADO");
                refreshAll();
            }
        }
        @Override public void onServiceDisconnected(int profile) {
            if (profile == BluetoothProfile.A2DP) {
                a2dp = null;
                log("A2DP_PROXY_DESCONECTADO");
                refreshAll();
            }
        }
    };

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_VOLUME_CHANGED.equals(action)) {
                int stream = intent.getIntExtra(EXTRA_VOLUME_STREAM_TYPE, -1);
                int vol = intent.getIntExtra(EXTRA_VOLUME_STREAM_VALUE, -1);
                int prev = intent.getIntExtra(EXTRA_PREV_VOLUME_STREAM_VALUE, -1);
                log("VOLUME_BROADCAST stream=" + stream + " " + prev + " -> " + vol + " | MUSIC=" + audioManager.getStreamVolume(AudioManager.STREAM_MUSIC));
                lastVolume = vol;
                updateVolume();
            } else if (BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED.equals(action)) {
                BluetoothDevice d = getDevice(intent);
                int state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1);
                int prev = intent.getIntExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE, -1);
                log("A2DP_STATE " + stateName(prev) + " -> " + stateName(state) + " | " + deviceLabel(d));
                refreshAll();
            } else if (BluetoothA2dp.ACTION_PLAYING_STATE_CHANGED.equals(action)) {
                BluetoothDevice d = getDevice(intent);
                int state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1);
                log("A2DP_PLAYING_STATE=" + state + " | " + deviceLabel(d));
                refreshAll();
            } else if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action) || BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
                BluetoothDevice d = getDevice(intent);
                log((BluetoothDevice.ACTION_ACL_CONNECTED.equals(action) ? "ACL_CONNECTED " : "ACL_DISCONNECTED ") + deviceLabel(d));
                refreshAll();
            }
        }
    };

    private final AudioDeviceCallback audioDeviceCallback = new AudioDeviceCallback() {
        @Override public void onAudioDevicesAdded(AudioDeviceInfo[] added) {
            for (AudioDeviceInfo d : added) log("AUDIO_DEVICE_ADDED type=" + d.getType() + " name=" + safeName(d) + " addr=" + safeAddress(d));
            refreshAll();
        }
        @Override public void onAudioDevicesRemoved(AudioDeviceInfo[] removed) {
            for (AudioDeviceInfo d : removed) log("AUDIO_DEVICE_REMOVED type=" + d.getType() + " name=" + safeName(d) + " addr=" + safeAddress(d));
            refreshAll();
        }
    };

    private final Runnable poller = new Runnable() {
        @Override public void run() {
            updateVolume();
            refreshAudioDevicesOnly();
            handler.postDelayed(this, 500);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        buildUi();
        requestBluetoothPermissionsIfNeeded();
        registerReceivers();
        if (Build.VERSION.SDK_INT >= 23) audioManager.registerAudioDeviceCallback(audioDeviceCallback, handler);
        if (bluetoothAdapter != null && hasBtPermission()) {
            try { bluetoothAdapter.getProfileProxy(this, profileListener, BluetoothProfile.A2DP); } catch (Exception e) { log("A2DP_PROXY_ERROR " + e); }
        }
        log("APP_INICIADA_V8");
        refreshAll();
        handler.post(poller);
    }

    private boolean hasBtPermission() {
        return Build.VERSION.SDK_INT < 31 || ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestBluetoothPermissionsIfNeeded() {
        if (Build.VERSION.SDK_INT >= 31 && !hasBtPermission()) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN}, REQ_BT);
        }
    }

    private void registerReceivers() {
        IntentFilter f = new IntentFilter();
        f.addAction(ACTION_VOLUME_CHANGED);
        f.addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED);
        f.addAction(BluetoothA2dp.ACTION_PLAYING_STATE_CHANGED);
        f.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        f.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        try {
            ContextCompat.registerReceiver(this, receiver, f, ContextCompat.RECEIVER_EXPORTED);
            receiverRegistered = true;
            log("RECEIVER_REGISTRADO_OK");
        } catch (Exception e) { log("RECEIVER_ERROR " + e.getMessage()); }
    }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (receiverRegistered) try { unregisterReceiver(receiver); } catch (Exception ignored) {}
        if (Build.VERSION.SDK_INT >= 23) try { audioManager.unregisterAudioDeviceCallback(audioDeviceCallback); } catch (Exception ignored) {}
        if (bluetoothAdapter != null && a2dp != null) try { bluetoothAdapter.closeProfileProxy(BluetoothProfile.A2DP, a2dp); } catch (Exception ignored) {}
        super.onDestroy();
    }

    @SuppressLint("MissingPermission")
    private void refreshAll() {
        updateBluetooth();
        refreshA2dp();
        refreshAudioDevicesOnly();
        updateVolume();
    }

    @SuppressLint("MissingPermission")
    private void updateBluetooth() {
        if (bluetoothAdapter == null) { btInfo.setText("BLUETOOTH: NO DISPONIBLE"); return; }
        if (!hasBtPermission()) { btInfo.setText("BLUETOOTH: FALTA PERMISO CONNECT"); return; }
        String state = bluetoothAdapter.isEnabled() ? "ENCENDIDO" : "APAGADO";
        String name = "—";
        try { name = bluetoothAdapter.getName(); } catch (Exception ignored) {}
        btInfo.setText("BLUETOOTH: " + state + "\nTELÉFONO BT: " + name);
    }

    @SuppressLint("MissingPermission")
    private void refreshA2dp() {
        if (!hasBtPermission()) {
            audioInfo.setText("A2DP: permiso BLUETOOTH_CONNECT pendiente");
            return;
        }
        if (a2dp == null) {
            audioInfo.setText("A2DP: proxy no disponible");
            return;
        }
        try {
            List<BluetoothDevice> devices = a2dp.getConnectedDevices();
            StringBuilder sb = new StringBuilder("A2DP CONECTADOS: ").append(devices.size()).append("\n");
            if (devices.isEmpty()) sb.append("—");
            for (BluetoothDevice d : devices) {
                sb.append(deviceLabel(d));
                try { sb.append("\n  PLAYING: ").append(a2dp.isA2dpPlaying(d)); } catch (Exception ignored) {}
                try { sb.append("\n  TYPE: ").append(d.getType()); } catch (Exception ignored) {}
                try { sb.append("\n  BOND: ").append(d.getBondState()); } catch (Exception ignored) {}
                try {
                    BluetoothClass bc = d.getBluetoothClass();
                    if (bc != null) sb.append("\n  CLASS: ").append(bc.getDeviceClass());
                } catch (Exception ignored) {}
                try {
                    ParcelUuid[] uuids = d.getUuids();
                    sb.append("\n  UUIDS: ").append(uuids == null ? 0 : uuids.length);
                    if (uuids != null) for (ParcelUuid u : uuids) sb.append("\n    ").append(u.getUuid());
                } catch (Exception ignored) {}
                sb.append("\n");
            }
            audioInfo.setText(sb.toString().trim());
        } catch (Exception e) {
            audioInfo.setText("A2DP_ERROR: " + e.getMessage());
        }
    }

    @SuppressLint("MissingPermission")
    private void refreshAudioDevicesOnly() {
        if (audioManager == null) return;
        try {
            AudioDeviceInfo[] outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
            StringBuilder sb = new StringBuilder("SALIDAS DE AUDIO: ").append(outputs.length).append("\n");
            boolean a2dp = false;
            for (AudioDeviceInfo d : outputs) {
                String type = typeName(d.getType());
                sb.append("TYPE=").append(d.getType())
                  .append(" | ").append(type)
                  .append(" | NAME=").append(safeName(d))
                  .append(" | ADDR=").append(safeAddress(d))
                  .append("\n");
                if (d.getType() == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP) a2dp = true;
            }
            sb.append("\nA2DP_AUDIO_OUTPUT=").append(a2dp ? "SI" : "NO");
            audioInfo.setText(sb.toString().trim());
        } catch (Exception e) {
            audioInfo.setText("AUDIO_DEVICES_ERROR: " + e.getMessage());
        }
    }

    private void updateVolume() {
        if (audioManager == null) return;
        int v = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        volumeInfo.setText("STREAM_MUSIC: " + v + " / " + max);
        if (lastVolume < 0) {
            lastVolume = v;
        } else if (v != lastVolume) {
            String direction = v > lastVolume ? "UP" : "DOWN";
            log("VOLUME_POLL " + lastVolume + " -> " + v + " (" + direction + ")");
            lastInfo.setText("ÚLTIMO EVENTO: VOLUME_" + direction + " " + lastVolume + " -> " + v);
            lastVolume = v;
        }
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN && (event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_UP || event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_DOWN || event.getKeyCode() == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)) {
            String n = KeyEvent.keyCodeToString(event.getKeyCode());
            keyInfo.setText("ÚLTIMO KEY EVENT: " + n);
            log("KEY_DOWN " + n + " source=" + event.getSource());
        }
        return super.dispatchKeyEvent(event);
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(24,24,24,24);
        TextView title = tv("LEADEN1 - BLUETOOTH VOLUME DIAGNOSTIC V8", 20, true); root.addView(title);
        status = tv("● V8 — MONITOR AVRCP/A2DP", 16, true); root.addView(status);
        btInfo = tv("BLUETOOTH: —", 15, false); root.addView(btInfo);
        audioInfo = tv("A2DP: —", 14, false); root.addView(audioInfo);
        volumeInfo = tv("STREAM_MUSIC: —", 16, true); root.addView(volumeInfo);
        keyInfo = tv("ÚLTIMO KEY EVENT: —", 14, false); root.addView(keyInfo);
        lastInfo = tv("ÚLTIMO EVENTO: —", 14, false); root.addView(lastInfo);
        Button refresh = new Button(this); refresh.setText("ACTUALIZAR BLUETOOTH / AUDIO"); refresh.setOnClickListener(v -> { log("MANUAL_REFRESH"); refreshAll(); }); root.addView(refresh);
        Button clear = new Button(this); clear.setText("LIMPIAR HISTORIAL"); clear.setOnClickListener(v -> { logs.clear(); renderHistory(); }); root.addView(clear);
        TextView h = tv("HISTORIAL", 17, true); root.addView(h);
        history = tv("—", 13, false); ScrollView sv = new ScrollView(this); sv.addView(history); root.addView(sv, new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root);
    }

    private TextView tv(String s, int size, boolean bold) { TextView t = new TextView(this); t.setText(s); t.setTextSize(size); t.setTextColor(Color.BLACK); t.setPadding(0,8,0,8); if (bold) t.setTypeface(null, android.graphics.Typeface.BOLD); return t; }
    private void log(String s) { String line = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(new Date()) + "  " + s; logs.add(0,line); if (logs.size()>80) logs.remove(logs.size()-1); if (lastInfo!=null) lastInfo.setText("ÚLTIMO EVENTO: " + s); renderHistory(); }
    private void renderHistory() { if (history!=null) history.setText(logs.isEmpty()?"—":android.text.TextUtils.join("\n",logs)); }
    @SuppressLint("MissingPermission") private String deviceLabel(BluetoothDevice d) { if (d==null) return "device=null"; String n="?"; try{n=d.getName();}catch(Exception ignored){} return n + " | " + d.getAddress(); }
    @SuppressLint("MissingPermission") private BluetoothDevice getDevice(Intent i) { try{return i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);}catch(Exception e){return null;} }
    private String stateName(int s){ switch(s){case BluetoothProfile.STATE_CONNECTED:return "CONNECTED";case BluetoothProfile.STATE_CONNECTING:return "CONNECTING";case BluetoothProfile.STATE_DISCONNECTED:return "DISCONNECTED";case BluetoothProfile.STATE_DISCONNECTING:return "DISCONNECTING";default:return ""+s;} }
    private String safeName(AudioDeviceInfo d){try{return d.getProductName()==null?"—":d.getProductName().toString();}catch(Exception e){return "?";}}
    private String safeAddress(AudioDeviceInfo d){try{return d.getAddress();}catch(Exception e){return "?";}}
    private String typeName(int t){switch(t){case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP:return "BLUETOOTH_A2DP";case AudioDeviceInfo.TYPE_BLUETOOTH_SCO:return "BLUETOOTH_SCO";case 26:return "BLE_HEADSET";case 27:return "BLE_SPEAKER";default:return "TYPE_"+t;}}
}
