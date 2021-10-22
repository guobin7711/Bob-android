package com.example.hiwin.teacher_version_bob.activity;


import android.annotation.SuppressLint;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.*;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Base64;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;
import com.example.hiwin.teacher_version_bob.R;
import com.example.hiwin.teacher_version_bob.communication.bluetooth.concrete.ReadLineStrategy;
import com.example.hiwin.teacher_version_bob.communication.bluetooth.concrete.SerialSocket;
import com.example.hiwin.teacher_version_bob.communication.bluetooth.framework.SerialListener;
import com.example.hiwin.teacher_version_bob.communication.service.SerialService;
import com.example.hiwin.teacher_version_bob.data.concrete.pack.Base64Package;
import com.example.hiwin.teacher_version_bob.data.concrete.pack.LinePackage;
import com.example.hiwin.teacher_version_bob.data.framework.pack.Package;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public abstract class DetectActivity extends AppCompatActivity {
    private static final String BT_LOG_TAG = "BluetoothInfo";


    private enum Connected {False, Pending, True}

    private Connected connected = Connected.False;
    private boolean isDetecting;
    private MenuItem item_connection, item_detect;
    private String deviceAddress;
    private SerialService serialService;


    protected abstract void receive(byte[] data);

    protected abstract void showDefault();

    protected boolean isConnected() {
        return connected == Connected.True;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detect);
        Toolbar toolbar = (Toolbar) findViewById(R.id.dectect_toolbar);
        setSupportActionBar(toolbar);

        boolean sus = bindService(new Intent(this, SerialService.class), serviceConnection, Context.BIND_AUTO_CREATE);
        Log.d("BindService", sus + "");
        Intent it = getIntent();
        deviceAddress = it.getStringExtra("address");
        showDefault();
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // 設置要用哪個menu檔做為選單
        getMenuInflater().inflate(R.menu.menu_main, menu);
        item_connection = menu.getItem(0);
        item_detect = menu.getItem(1);
        setConnectionMenuItem(false);
        return true;
    }

    @SuppressLint("NonConstantResourceId")
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        try {
            if (item.getItemId() == R.id.menu_main_connection) {
                if (connected == Connected.False)
                    connect();
                else if (connected == Connected.True)
                    disconnect();
            } else if (item.getItemId() == R.id.menu_main_detect) {
                if (connected == Connected.False)
                    throw new RuntimeException("Not Connected.");

                if (isDetecting) {
                    detect_pause();
                    isDetecting = false;
                } else {
                    detect_start();
                    isDetecting = true;
                }
            }
        } catch (Exception e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
            Log.d(BT_LOG_TAG, e.getMessage());
        }

        return super.onOptionsItemSelected(item);
    }

    protected void detect_pause() {
        if (connected == Connected.False)
            throw new RuntimeException("Not Connected.");
        setDetectMenuItem(false);
        sendMessage("PAUSE_DETECT");
    }

    protected void detect_start() {
        if (connected == Connected.False)
            throw new RuntimeException("Not Connected.");
        setDetectMenuItem(true);
        sendMessage("START_DETECT");
    }


    @Override
    public void onDestroy() {
        if (connected == Connected.True)
            disconnect();
        stopService(new Intent(this, SerialService.class));
        super.onDestroy();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (serialService != null)
            serialService.attach(serialDataListener);
        else
            startService(new Intent(this, SerialService.class)); // prevents service destroy on unbind from recreated activity caused by orientation change
    }

    @Override
    public void onStop() {
        if (serialService != null && !this.isChangingConfigurations())
            serialService.detach();
        super.onStop();
    }


    private void connect() throws IOException {
        try {
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);
            Log.d(BT_LOG_TAG, "connecting...");
            SerialSocket socket = new SerialSocket(this, device, new ReadLineStrategy());
            serialService.connect(socket);
            connected = Connected.Pending;

        } catch (Exception e) {
            serialDataListener.onSerialConnectError(e);
            throw e;
        }
    }

    private void disconnect() {
        Log.d(BT_LOG_TAG, "disconnect");
        if (connected == Connected.True) {
            detect_pause();
        }

        connected = Connected.False;
        serialService.disconnect();
        setConnectionMenuItem(false);
    }

    private void setConnectionMenuItem(boolean connected) {
        if (connected) {
            item_connection.setIcon(R.drawable.link_off);
            item_connection.setTitle("Disconnect");
        } else {
            item_connection.setIcon(R.drawable.link);
            item_connection.setTitle("Connect");
        }
    }

    private void setDetectMenuItem(boolean isDetecting) {
        if (isDetecting) {
            item_detect.setTitle("Pause");
        } else {
            item_detect.setTitle("Start");
        }
    }

    public void sendMessage(String msg) {
        Package pack = new LinePackage(new Base64Package(msg.getBytes(StandardCharsets.UTF_8), Base64.DEFAULT));
        try {
            serialService.write(pack.getEncoded());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    protected void postFragment(Fragment fragment, String id) {
        FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();
        fragmentTransaction.setReorderingAllowed(true);
        fragmentTransaction.replace(R.id.detect_frame, fragment, id);
        fragmentTransaction.commit();
    }

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            serialService = ((SerialService.SerialBinder) binder).getService();
            serialService.attach(serialDataListener);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serialService = null;
        }
    };

    private final SerialListener serialDataListener = new SerialListener() {
        @Override
        public void onSerialConnect() {
            connected = Connected.True;
            Log.d(BT_LOG_TAG, "Bluetooth device connected");
            Toast.makeText(DetectActivity.this, "Bluetooth device connected", Toast.LENGTH_SHORT).show();
            setConnectionMenuItem(true);
            detect_pause();
        }

        @Override
        public void onSerialConnectError(Exception e) {
            Log.e(BT_LOG_TAG, "Connection Error");
            Log.e(BT_LOG_TAG, e.getMessage());
            Toast.makeText(DetectActivity.this, "Connection Error:" + e.getMessage(), Toast.LENGTH_SHORT).show();
            disconnect();
        }

        @Override
        public void onSerialRead(byte[] data) {
            receive(data);
        }

        @Override
        public void onSerialIoError(Exception e) {
            Log.e(BT_LOG_TAG, "IO Error");
            Log.e(BT_LOG_TAG, e.getMessage());
            Toast.makeText(DetectActivity.this, "IO Error:" + e.getMessage(), Toast.LENGTH_SHORT).show();
            disconnect();
        }
    };

}
