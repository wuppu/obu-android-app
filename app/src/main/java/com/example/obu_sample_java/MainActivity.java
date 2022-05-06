package com.example.obu_sample_java;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.lang.Integer;

public class MainActivity extends AppCompatActivity {
    TextView mTvBluetoothStatus;
    TextView mTvReceiveData;
    TextView mTvSendData;
    TextView logView;
    TextView btRssi;
    Button mBtnBluetoothOn;
    Button mBtnBluetoothOff;
    Button mBtnConnect;
    Button mBtnSendData;

    BluetoothAdapter mBluetoothAdapter;
    Set<BluetoothDevice> mPairedDevices;
    List<String> mListPairedDevices;

    Handler mBluetoothHandler;
    ConnectedBluetoothThread mThreadConnectedBluetooth;
    GetRssiThread mThreadGetRssi;
    BluetoothDevice mBluetoothDevice;
    BluetoothSocket mBluetoothSocket;
    BroadcastReceiver mReceiver;

    // message format
    RefMessage refMessageFormat;

    final static int BT_REQUEST_ENABLE = 1;
    final static int BT_MESSAGE_READ = 2;
    final static int BT_CONNECTING_STATUS = 3;
    final static UUID BT_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mTvBluetoothStatus = (TextView) findViewById(R.id.tvBluetoothStatus);
        mTvReceiveData = (TextView) findViewById(R.id.tvReceiveData);
        btRssi = (TextView) findViewById(R.id.btRssi);
        mTvSendData = (EditText) findViewById(R.id.tvSendData);
        mBtnBluetoothOn = (Button) findViewById(R.id.btnBluetoothOn);
        mBtnBluetoothOff = (Button) findViewById(R.id.btnBluetoothOff);
        mBtnConnect = (Button) findViewById(R.id.btnConnect);
        mBtnSendData = (Button) findViewById(R.id.btnSendData);
        logView = (TextView) findViewById(R.id.logView);
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        refMessageFormat = new RefMessage();

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 1);
            return;
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH}, 1);
            return;
        }

        // Acquire a reference to the system Location Manager
        LocationManager locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);

        // GPS 프로바이더 사용가능여부
        boolean isGPSEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        // 네트워크 프로바이더 사용가능여부
        boolean isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

        Log.d("Main", "isGPSEnabled=" + isGPSEnabled);
        Log.d("Main", "isNetworkEnabled=" + isNetworkEnabled);

        LocationListener locationListener = new LocationListener() {
            public void onLocationChanged(Location location) {
                double lat = location.getLatitude();
                double lng = location.getLongitude();

                logView.setText("latitude: " + lat + "\nlongitude: " + lng);
            }

//            public void onStatusChanged(String provider, int status, Bundle extras) {
//                logView.setText("onStatusChanged");
//            }

            public void onProviderEnabled(String provider) {
                logView.setText("onProviderEnabled");
            }

            public void onProviderDisabled(String provider) {
                logView.setText("onProviderDisabled");
            }
        };

        // Register the listener with the Location Manager to receive location updates
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            logView.setText("location permission error");
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 1);
            return;
        }
        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, locationListener);
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);

        // 수동으로 위치 구하기
        String locationProvider = LocationManager.GPS_PROVIDER;
        Location lastKnownLocation = locationManager.getLastKnownLocation(locationProvider);
        if (lastKnownLocation != null) {
            double lng = lastKnownLocation.getLatitude();
            double lat = lastKnownLocation.getLatitude();
            Log.d("Main", "longtitude=" + lng + ", latitude=" + lat);
        }

        mBtnBluetoothOn.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View view) {
                bluetoothOn();
            }
        });
        mBtnBluetoothOff.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View view) {
                bluetoothOff();
            }
        });
        mBtnConnect.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View view) {
                listPairedDevices();
            }
        });
        mBtnSendData.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mThreadConnectedBluetooth != null) {
                    mThreadConnectedBluetooth.write(mTvSendData.getText().toString());
                    mTvSendData.setText("");
                }
            }
        });
        mBluetoothHandler = new Handler() {
            public void handleMessage(android.os.Message msg) {
                Toast.makeText(getApplicationContext(), "handleMessage", Toast.LENGTH_LONG).show();

                if (msg.what == BT_MESSAGE_READ) {
                    Toast.makeText(getApplicationContext(), "BT_MESSAGE_READ", Toast.LENGTH_LONG).show();

                    String readMessage = null;
                    try {
//                        RefMessage tmpMessage = new RefMessage();
//                        byte[] id = new byte[]{0x48, 0x59, 0x45, 0x53};
//                        tmpMessage.id = id;
//
//                        byte[] type = new byte[]{0x00, 0x00, 0x00, 0x01};
//                        tmpMessage.type = type;
//
//                        byte[] body_len = new byte[]{0x00, 0x00, 0x00, 0x08};
//                        tmpMessage.body_len = body_len;
//
//                        byte[] latitude = new byte[]{0x35, (byte) 0xA4, (byte) 0xE9, 0x01};
//                        tmpMessage.latitude = latitude;
//
//                        byte[] longitude = new byte[]{0x6b, 0x49, (byte) 0xd2, 0x01};
//                        tmpMessage.longitude = longitude;
//
//                        List<Byte> msgFormat = new ArrayList(Arrays.asList(id));
//                        msgFormat.addAll(new ArrayList(Arrays.asList(type)));
//                        msgFormat.addAll(new ArrayList(Arrays.asList(body_len)));
//                        msgFormat.addAll(new ArrayList(Arrays.asList(latitude)));
//                        msgFormat.addAll(new ArrayList(Arrays.asList(longitude)));
//
//                        mTvReceiveData.setText(msgFormat.toString() + "\n");
//                        msg.obj = msgFormat.toArray();

                        // refMessageFormat = (RefMessage) msg.obj;
                        refMessageFormat.id = Arrays.copyOfRange((byte[]) msg.obj, 0, 4);
                        for (int i = 0; i < 4; i++) refMessageFormat.id[i] = (byte) (refMessageFormat.id[i] & 0xff);

                        refMessageFormat.type = Arrays.copyOfRange((byte[])msg.obj, 4, 8);
                        for (int i = 0; i < 4; i++) refMessageFormat.type[i] = (byte) (refMessageFormat.type[i] & 0xff);

                        refMessageFormat.body_len = Arrays.copyOfRange((byte[])msg.obj, 8, 12);
                        for (int i = 0; i < 4; i++) refMessageFormat.body_len[i] = (byte) (refMessageFormat.body_len[i] & 0xff);

                        refMessageFormat.latitude = Arrays.copyOfRange((byte[])msg.obj, 12, 16);
                        for (int i = 0; i < 4; i++) refMessageFormat.latitude[i] = (byte) (refMessageFormat.latitude[i] & 0xff);

                        refMessageFormat.longitude = Arrays.copyOfRange((byte[])msg.obj, 16, 20);
                        for (int i = 0; i < 4; i++) refMessageFormat.longitude[i] = (byte) (refMessageFormat.longitude[i] & 0xff);

                        mTvReceiveData.setText("id: " + refMessageFormat.id[0] + " " + refMessageFormat.id[1] + " " + refMessageFormat.id[2] + " " + refMessageFormat.id[3] + "\n");
                        mTvReceiveData.setText(mTvReceiveData.getText() + "type: " + refMessageFormat.type[0] + " " + refMessageFormat.type[1] + " " + refMessageFormat.type[2] + " " + refMessageFormat.type[3] + "\n");
                        mTvReceiveData.setText(mTvReceiveData.getText() + "body_len: " +  refMessageFormat.body_len[0] + " " + refMessageFormat.body_len[1] + " " + refMessageFormat.body_len[2] + " " + refMessageFormat.body_len[3] + "\n");
                        mTvReceiveData.setText(mTvReceiveData.getText() + "latitude: " +  refMessageFormat.latitude[0] + " " + (refMessageFormat.latitude[1] & 0xff) + " " + refMessageFormat.latitude[2] + " " + refMessageFormat.latitude[3] + "\n");
                        mTvReceiveData.setText(mTvReceiveData.getText() + "longitude: " +  refMessageFormat.longitude[0] + " " + refMessageFormat.longitude[1] + " " + refMessageFormat.longitude[2] + " " + refMessageFormat.longitude[3] + "\n");

//                        mTvReceiveData.setText("id: " + new String(refMessageFormat.id) + "\n");
//                        mTvReceiveData.setText(mTvReceiveData.getText() + "type: " + new String(String.valueOf(ByteBuffer.wrap(refMessageFormat.type).getInt())) + "\n");
//                        mTvReceiveData.setText(mTvReceiveData.getText() + "body_len: " + new String(String.valueOf(ByteBuffer.wrap(refMessageFormat.type).getInt())) + "\n");
//                        mTvReceiveData.setText(mTvReceiveData.getText() + "latitude: " + new String(String.valueOf(ByteBuffer.wrap(refMessageFormat.latitude).getInt())) + "\n");
//                        mTvReceiveData.setText(mTvReceiveData.getText() + "longitude: " + new String(String.valueOf(ByteBuffer.wrap(refMessageFormat.longitude).getInt())) + "\n");

                        readMessage = new String((byte[]) msg.obj, "UTF-8");
                    } catch (Exception e) {
                        Toast.makeText(getApplicationContext(), "Fail to parse rx message - e: " + e.toString(), Toast.LENGTH_LONG).show();
                        e.printStackTrace();
                    }
                    // mTvReceiveData.setText(readMessage);
                }
            }
        };
    }

    private final BroadcastReceiver receiver = new BroadcastReceiver(){
        @Override
        public void onReceive(Context context, Intent intent) {

            try {
                String action = intent.getAction();
                if(BluetoothDevice.ACTION_FOUND.equals(action)) {
                    int rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI,Short.MIN_VALUE);
                    String name = intent.getStringExtra(BluetoothDevice.EXTRA_NAME);
                    TextView rssi_msg = (TextView) findViewById(R.id.btRssi);
                    if (name == null) {
                        return;
                    }
                    if (mBluetoothDevice.getName().equals(name)) {
                        rssi_msg.setText(name + ": " + rssi + "dBm");
                        mBluetoothAdapter.cancelDiscovery();
                    }
                }
            }
            catch (Exception e) {
                Toast.makeText(getApplicationContext(), "Fail to get rssi - e: " + e.toString(), Toast.LENGTH_LONG).show();
            }
        }
    };

    void bluetoothOn() {
        if (mBluetoothAdapter == null) {
            Toast.makeText(getApplicationContext(), "블루투스를 지원하지 않는 기기입니다.", Toast.LENGTH_LONG).show();
        } else {
            if (mBluetoothAdapter.isEnabled()) {
                Toast.makeText(getApplicationContext(), "블루투스가 이미 활성화 되어 있습니다.", Toast.LENGTH_LONG).show();
                mTvBluetoothStatus.setText("active");
            } else {
                Toast.makeText(getApplicationContext(), "블루투스가 활성화 되어 있지 않습니다.", Toast.LENGTH_LONG).show();
                Intent intentBluetoothEnable = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(intentBluetoothEnable, BT_REQUEST_ENABLE);
            }
        }
    }

    void bluetoothOff() {
        if (mBluetoothAdapter.isEnabled()) {
            mBluetoothAdapter.disable();
            Toast.makeText(getApplicationContext(), "블루투스가 비활성화 되었습니다.", Toast.LENGTH_SHORT).show();
            mTvBluetoothStatus.setText("inactive");
        } else {
            Toast.makeText(getApplicationContext(), "블루투스가 이미 비활성화 되어 있습니다.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case BT_REQUEST_ENABLE:
                if (resultCode == RESULT_OK) { // 블루투스 활성화를 확인을 클릭하였다면
                    Toast.makeText(getApplicationContext(), "블루투스 활성화", Toast.LENGTH_LONG).show();
                    mTvBluetoothStatus.setText("active");
                } else if (resultCode == RESULT_CANCELED) { // 블루투스 활성화를 취소를 클릭하였다면
                    Toast.makeText(getApplicationContext(), "취소", Toast.LENGTH_LONG).show();
                    mTvBluetoothStatus.setText("inactive");
                }
                break;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    void listPairedDevices() {
        if (mBluetoothAdapter.isEnabled()) {

            mPairedDevices = mBluetoothAdapter.getBondedDevices();
            if (mPairedDevices.size() > 0) {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("장치 선택");

                mListPairedDevices = new ArrayList<String>();
                for (BluetoothDevice device : mPairedDevices) {
                    mListPairedDevices.add(device.getName());
                    //mListPairedDevices.add(device.getName() + "\n" + device.getAddress());
                }
                final CharSequence[] items = mListPairedDevices.toArray(new CharSequence[mListPairedDevices.size()]);
                mListPairedDevices.toArray(new CharSequence[mListPairedDevices.size()]);

                builder.setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int item) {
                        connectSelectedDevice(items[item].toString());
                    }
                });

                AlertDialog alert = builder.create();
                alert.show();
            } else {
                Toast.makeText(getApplicationContext(), "페어링된 장치가 없습니다.", Toast.LENGTH_LONG).show();
            }
        } else {
            Toast.makeText(getApplicationContext(), "블루투스가 비활성화 되어 있습니다.", Toast.LENGTH_SHORT).show();
        }
    }

    void connectSelectedDevice(String selectedDeviceName) {
        for (BluetoothDevice tempDevice : mPairedDevices) {
            if (selectedDeviceName.equals(tempDevice.getName())) {
                mBluetoothDevice = tempDevice;
                break;
            }
        }
        try {
            mBluetoothSocket = mBluetoothDevice.createInsecureRfcommSocketToServiceRecord(BT_UUID);
            mBluetoothSocket.connect();

            mThreadConnectedBluetooth = new ConnectedBluetoothThread(mBluetoothSocket);
            mThreadConnectedBluetooth.start();

            if (mThreadGetRssi == null) {
                mThreadGetRssi = new GetRssiThread();
                mThreadGetRssi.start();
            }
            mBluetoothHandler.obtainMessage(BT_CONNECTING_STATUS, 1, -1).sendToTarget();

        } catch (IOException e) {
            Toast.makeText(getApplicationContext(), "블루투스 연결 중 오류가 발생했습니다." + e.toString(), Toast.LENGTH_LONG).show();
        }
    }

    private class RefMessage {
         byte[] id;
         byte[] type;
         byte[] body_len;
         byte[] latitude;
         byte[] longitude;

         public RefMessage() {
             id = new byte[4];
             type = new byte[4];
             body_len = new byte[4];
             latitude = new byte[4];
             longitude = new byte[4];
         }
    }
    private class GetRssiThread extends Thread {
        public GetRssiThread() {
            // Register for broadcasts when a device is discovered
            registerReceiver(receiver, new IntentFilter(BluetoothDevice.ACTION_FOUND));
            mBluetoothAdapter.startDiscovery();
        }
        public void run() {
            while (true) {
                if (mBluetoothAdapter.isDiscovering() == false) {
                    mBluetoothAdapter.startDiscovery();
                }
            }
        }
    }


    private class ConnectedBluetoothThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedBluetoothThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Toast.makeText(getApplicationContext(), "소켓 연결 중 오류가 발생했습니다.", Toast.LENGTH_LONG).show();
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }
        public void run() {
            byte[] buffer = new byte[1024];
            int bytes;

            while (true) {
                try {
                    bytes = mmInStream.available();
                    if (bytes != 0) {
                        SystemClock.sleep(100);
                        bytes = mmInStream.available();
                        bytes = mmInStream.read(buffer, 0, bytes);
                        mBluetoothHandler.obtainMessage(BT_MESSAGE_READ, bytes, -1, buffer).sendToTarget();
                    }
                } catch (IOException e) {
                    break;
                }
            }
        }
        public void write(String str) {
            byte[] bytes = str.getBytes();
            try {
                mmOutStream.write(bytes);
            } catch (IOException e) {
                Toast.makeText(getApplicationContext(), "데이터 전송 중 오류가 발생했습니다.", Toast.LENGTH_LONG).show();
            }
        }
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Toast.makeText(getApplicationContext(), "소켓 해제 중 오류가 발생했습니다.", Toast.LENGTH_LONG).show();
            }
        }
    }
}