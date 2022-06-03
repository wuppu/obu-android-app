package com.example.obu_sample_java;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
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
    TextView accLogView;
    TextView btRssi;
    Switch gpsSwitch;
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
    SendNotiMessageThread mThreadSendNoti;
    BluetoothDevice mBluetoothDevice;
    BluetoothSocket mBluetoothSocket;

    // message format
    RefMessage refMessageFormat;
    RefMessage alertMessageFormat;
    NotiMessage notiMessageFormat;

    final static int BT_REQUEST_ENABLE = 1;
    final static int BT_MESSAGE_READ = 2;
    final static int BT_CONNECTING_STATUS = 3;
    final static UUID BT_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    final static int UNAVAILABLE_LATITUDE = 900000001;
    final static int UNAVAILABLE_LONGITUDE = 1800000001;
    final static int UNAVAILABLE_RSSI = -255;
    final static int ACC_NONE = -999;

    int currentLatitude = UNAVAILABLE_LATITUDE;
    int currentLongitude = UNAVAILABLE_LONGITUDE;
    int currentRssi = UNAVAILABLE_RSSI;

    int refLatitude = UNAVAILABLE_LATITUDE;
    int refLongitude = UNAVAILABLE_LONGITUDE;

    double currentAccX = ACC_NONE;
    double currentAccY = ACC_NONE;
    double currentAccZ = ACC_NONE;

    double prevAccX = 0;
    double prevAccY = 0;

    double prevValX = 0;
    double prevValY = 0;

    double prevPosX = 0;
    double prevPosY = 0;

    // IMU 센서
    private SensorManager mSensorManager = null;
    private SensorEventListener mAccLis;
    private Sensor mAccelometerSensor = null;
    private Sensor mMagneticSensor = null;
    boolean isSensorRunnging = false;

    float[] accMat = new float[3];
    float[] magMat = new float[3];
    float[] earth = new float[3];
    float[] rotMat = new float[9];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mTvBluetoothStatus = (TextView) findViewById(R.id.tvBluetoothStatus);
        mTvReceiveData = (TextView) findViewById(R.id.tvReceiveData);
        btRssi = (TextView) findViewById(R.id.btRssi);
        mTvSendData = (TextView) findViewById(R.id.tvSendData);
        mBtnBluetoothOn = (Button) findViewById(R.id.btnBluetoothOn);
        mBtnBluetoothOff = (Button) findViewById(R.id.btnBluetoothOff);
        mBtnConnect = (Button) findViewById(R.id.btnConnect);
        mBtnSendData = (Button) findViewById(R.id.btnSendData);
        logView = (TextView) findViewById(R.id.logView);
        accLogView = (TextView) findViewById(R.id.accLogView);
        gpsSwitch = (Switch) findViewById(R.id.gpsSwitch);

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        refMessageFormat = new RefMessage();
        alertMessageFormat = new RefMessage();

        // Using the gyro and accel
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        // Using the accel
        mAccelometerSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        mMagneticSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        mAccLis = new AccelometerListener();

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 1);
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH}, 1);
        }

        // Acquire a reference to the system Location Manager
        LocationManager locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);

        // GPS 프로바이더 사용가능여부
        boolean isGPSEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        // 네트워크 프로바이더 사용가능여부
        boolean isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

        Log.d("Main", "isGPSEnabled=" + isGPSEnabled);
        Log.d("Main", "isNetworkEnabled=" + isNetworkEnabled);
        mSensorManager.registerListener(mAccLis, mAccelometerSensor, 100000);
        mSensorManager.registerListener(mAccLis, mMagneticSensor, 100000);

        LocationListener locationListener = new LocationListener() {
            public void onLocationChanged(Location location) {
                try {
                    double lat = location.getLatitude();
                    double lng = location.getLongitude();

                    currentLatitude = (int) (lat * 10000000);
                    currentLongitude = (int) (lng * 10000000);

                    logView.setText("latitude: " + lat + "\nlongitude: " + lng);
                }
                catch (Exception e) {
                    Toast.makeText(getApplicationContext(), e.toString(), Toast.LENGTH_LONG).show();
                }
            }

            public void onStatusChanged(String provider, int status, Bundle extras) {
                logView.setText("onStatusChanged");
            }

            public void onProviderEnabled(String provider) {
                logView.setText("onProviderEnabled");
            }

            public void onProviderDisabled(String provider) {
                logView.setText("onProviderDisabled");
                currentLatitude = UNAVAILABLE_LATITUDE;
                currentLongitude = UNAVAILABLE_LONGITUDE;
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
            Log.d("Main", "longitude=" + lng + ", latitude=" + lat);
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
        gpsSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                isSensorRunnging = !isChecked;

                if (isSensorRunnging) {
                    mSensorManager.registerListener(mAccLis, mAccelometerSensor, 100000);
                }
                else {
                    mSensorManager.unregisterListener(mAccLis);
                }
            }
        });
        mBtnSendData.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View view) {
//                if (isSensorRunnging == false) {
//                    isSensorRunnging = true;
//                    mSensorManager.registerListener(mAccLis, mAccelometerSensor, SensorManager.SENSOR_DELAY_UI);
//                }
//                else {
//                    isSensorRunnging = false;
//                    mSensorManager.unregisterListener(mAccLis);
//                }
                if (mThreadConnectedBluetooth != null) {

                    // alert message 생성
                    alertMessageFormat.id = new String("HYES").getBytes();
                    alertMessageFormat.type = ConvertIntToByteArray(2);
                    alertMessageFormat.body_len = ConvertIntToByteArray(8);
                    alertMessageFormat.latitude = ConvertIntToByteArray(currentLatitude);
                    alertMessageFormat.longitude = ConvertIntToByteArray(currentLongitude);

                    Byte[] tempByte = new Byte[4];
                    for (int i = 0; i < 4; i++) tempByte[i] = alertMessageFormat.id[i];
                    List<Byte> msgFormat = new ArrayList<Byte>(Arrays.asList(tempByte));
                    for (int i = 0; i < 4; i++) tempByte[i] = alertMessageFormat.type[i];
                    msgFormat.addAll(new ArrayList<Byte>(Arrays.asList(tempByte)));
                    for (int i = 0; i < 4; i++) tempByte[i] = alertMessageFormat.body_len[i];
                    msgFormat.addAll(new ArrayList<Byte>(Arrays.asList(tempByte)));
                    for (int i = 0; i < 4; i++) tempByte[i] = alertMessageFormat.latitude[i];
                    msgFormat.addAll(new ArrayList<Byte>(Arrays.asList(tempByte)));
                    for (int i = 0; i < 4; i++) tempByte[i] = alertMessageFormat.longitude[i];
                    msgFormat.addAll(new ArrayList<Byte>(Arrays.asList(tempByte)));

                    for (int i = 0; i < 4; i++)
                        alertMessageFormat.id[i] = (byte) (alertMessageFormat.id[i] & 0xff);
                    for (int i = 0; i < 4; i++)
                        alertMessageFormat.type[i] = (byte) (alertMessageFormat.type[i] & 0xff);
                    for (int i = 0; i < 4; i++)
                        alertMessageFormat.body_len[i] = (byte) (alertMessageFormat.body_len[i] & 0xff);
                    for (int i = 0; i < 4; i++)
                        alertMessageFormat.latitude[i] = (byte) (alertMessageFormat.latitude[i] & 0xff);
                    for (int i = 0; i < 4; i++)
                        alertMessageFormat.longitude[i] = (byte) (alertMessageFormat.longitude[i] & 0xff);
                    mTvSendData.setText("id: " + new String(alertMessageFormat.id) + "\n");
                    mTvSendData.setText(mTvSendData.getText() + "type: " + ConvertByteArrayToInt(alertMessageFormat.type) + "\n");
                    mTvSendData.setText(mTvSendData.getText() + "body_len: " + ConvertByteArrayToInt(alertMessageFormat.body_len) + "\n");
                    mTvSendData.setText(mTvSendData.getText() + "latitude: " + ConvertByteArrayToInt(alertMessageFormat.latitude) + "\n");
                    mTvSendData.setText(mTvSendData.getText() + "longitude: " + ConvertByteArrayToInt(alertMessageFormat.longitude) + "\n");

                    mThreadConnectedBluetooth.write(msgFormat);
                }
            }
        });
        mBluetoothHandler = new Handler() {
            public void handleMessage(android.os.Message msg) {

                if (msg.what == BT_MESSAGE_READ) {

                    //String readMessage = null;
                    try {
                        // refMessageFormat = (RefMessage) msg.obj;
                        refMessageFormat.id = Arrays.copyOfRange((byte[]) msg.obj, 0, 4);
                        for (int i = 0; i < 4; i++)
                            refMessageFormat.id[i] = (byte) (refMessageFormat.id[i] & 0xff);

                        refMessageFormat.type = Arrays.copyOfRange((byte[]) msg.obj, 4, 8);
                        for (int i = 0; i < 4; i++)
                            refMessageFormat.type[i] = (byte) (refMessageFormat.type[i] & 0xff);

                        refMessageFormat.body_len = Arrays.copyOfRange((byte[]) msg.obj, 8, 12);
                        for (int i = 0; i < 4; i++)
                            refMessageFormat.body_len[i] = (byte) (refMessageFormat.body_len[i] & 0xff);

                        refMessageFormat.latitude = Arrays.copyOfRange((byte[]) msg.obj, 12, 16);
                        for (int i = 0; i < 4; i++)
                            refMessageFormat.latitude[i] = (byte) (refMessageFormat.latitude[i] & 0xff);

                        refMessageFormat.longitude = Arrays.copyOfRange((byte[]) msg.obj, 16, 20);
                        for (int i = 0; i < 4; i++)
                            refMessageFormat.longitude[i] = (byte) (refMessageFormat.longitude[i] & 0xff);

                        mTvReceiveData.setText("id: " + new String(refMessageFormat.id) + "\n");
                        mTvReceiveData.setText(mTvReceiveData.getText() + "type: " + ConvertByteArrayToInt(refMessageFormat.type) + "\n");
                        mTvReceiveData.setText(mTvReceiveData.getText() + "body_len: " + ConvertByteArrayToInt(refMessageFormat.body_len) + "\n");
                        mTvReceiveData.setText(mTvReceiveData.getText() + "latitude: " + ConvertByteArrayToInt(refMessageFormat.latitude) + "\n");
                        mTvReceiveData.setText(mTvReceiveData.getText() + "longitude: " + ConvertByteArrayToInt(refMessageFormat.longitude) + "\n");

                        refLatitude = ConvertByteArrayToInt(refMessageFormat.latitude);
                        refLongitude = ConvertByteArrayToInt(refMessageFormat.longitude);

                        //readMessage = new String((byte[]) msg.obj, "UTF-8");
                    } catch (Exception e) {
                        Toast.makeText(getApplicationContext(), "Fail to parse rx message - e: " + e.toString(), Toast.LENGTH_LONG).show();
                        e.printStackTrace();
                    }
                }
            }
        };
    }

    private static byte[] ConvertIntToByteArray(final int integer) {
        ByteBuffer buff = ByteBuffer.allocate(Integer.SIZE / 8);
        buff.clear();
        buff.order(ByteOrder.LITTLE_ENDIAN);
        buff.putInt(integer);
        return buff.array();
    }

    private static int ConvertByteArrayToInt(byte[] bytes) {
        final int size = Integer.SIZE / 8;
        ByteBuffer buff = ByteBuffer.allocate(size);
        final byte[] newBytes = new byte[size];

        for (int i = 0; i < size; i++) {
            if (i + bytes.length < size) {
                newBytes[i] = (byte) 0x00;
            } else {
                newBytes[i] = bytes[i + bytes.length - size];
            }
        }
        buff = ByteBuffer.wrap(newBytes);
        buff.order(ByteOrder.LITTLE_ENDIAN);
        return buff.getInt();
    }

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            try {
                String action = intent.getAction();
                if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                    int rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE);
                    String name = intent.getStringExtra(BluetoothDevice.EXTRA_NAME);
                    TextView rssi_msg = (TextView) findViewById(R.id.btRssi);
                    if (name == null) {
                        return;
                    }
                    if (mBluetoothDevice.getName().equals(name) && rssi != -100) {
                        rssi_msg.setText(name + ": " + rssi + "dBm");
                        currentRssi = rssi;
                        mBluetoothAdapter.cancelDiscovery();
                    }
                }
            } catch (Exception e) {
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

            if (mThreadSendNoti == null) {
                mThreadSendNoti = new SendNotiMessageThread();
                mThreadSendNoti.start();
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

    private class NotiMessage {
        byte[] id;
        byte[] type;
        byte[] body_len;
        byte[] latitude;
        byte[] longitude;
        byte[] rssi;

        public NotiMessage() {
            id = new byte[4];
            type = new byte[4];
            body_len = new byte[4];
            latitude = new byte[4];
            longitude = new byte[4];
            rssi = new byte[4];
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


    private class SendNotiMessageThread extends Thread {
        public SendNotiMessageThread() {
            notiMessageFormat = new NotiMessage();
        }

        public void run() {
            while (true) {
                if (mThreadConnectedBluetooth != null && currentRssi != -255) {
                    try {
                        if (isSensorRunnging) {
                            notiMessageFormat.id = new String("HYES").getBytes();
                            notiMessageFormat.type = ConvertIntToByteArray(3);
                            notiMessageFormat.body_len = ConvertIntToByteArray(8);
                            notiMessageFormat.latitude = ConvertIntToByteArray(refLatitude);
                            notiMessageFormat.longitude = ConvertIntToByteArray(refLongitude);
                            notiMessageFormat.rssi = ConvertIntToByteArray(currentRssi);
                        }
                        else {
                            notiMessageFormat.id = new String("HYES").getBytes();
                            notiMessageFormat.type = ConvertIntToByteArray(3);
                            notiMessageFormat.body_len = ConvertIntToByteArray(8);
                            notiMessageFormat.latitude = ConvertIntToByteArray(currentLatitude);
                            notiMessageFormat.longitude = ConvertIntToByteArray(currentLongitude);
                            notiMessageFormat.rssi = ConvertIntToByteArray(currentRssi);
                        }

                        Byte[] tempByte = new Byte[4];
                        for (int i = 0; i < 4; i++) tempByte[i] = notiMessageFormat.id[i];
                        List<Byte> msgFormat = new ArrayList<Byte>(Arrays.asList(tempByte));
                        for (int i = 0; i < 4; i++) tempByte[i] = notiMessageFormat.type[i];
                        msgFormat.addAll(new ArrayList<Byte>(Arrays.asList(tempByte)));
                        for (int i = 0; i < 4; i++) tempByte[i] = notiMessageFormat.body_len[i];
                        msgFormat.addAll(new ArrayList<Byte>(Arrays.asList(tempByte)));
                        for (int i = 0; i < 4; i++) tempByte[i] = notiMessageFormat.latitude[i];
                        msgFormat.addAll(new ArrayList<Byte>(Arrays.asList(tempByte)));
                        for (int i = 0; i < 4; i++) tempByte[i] = notiMessageFormat.longitude[i];
                        msgFormat.addAll(new ArrayList<Byte>(Arrays.asList(tempByte)));
                        for (int i = 0; i < 4; i++) tempByte[i] = notiMessageFormat.rssi[i];
                        msgFormat.addAll(new ArrayList<Byte>(Arrays.asList(tempByte)));

                        for (int i = 0; i < 4; i++) notiMessageFormat.id[i] = (byte) (notiMessageFormat.id[i] & 0xff);
                        for (int i = 0; i < 4; i++) notiMessageFormat.type[i] = (byte) (notiMessageFormat.type[i] & 0xff);
                        for (int i = 0; i < 4; i++) notiMessageFormat.body_len[i] = (byte) (notiMessageFormat.body_len[i] & 0xff);
                        for (int i = 0; i < 4; i++) notiMessageFormat.latitude[i] = (byte) (notiMessageFormat.latitude[i] & 0xff);
                        for (int i = 0; i < 4; i++) notiMessageFormat.longitude[i] = (byte) (notiMessageFormat.longitude[i] & 0xff);
                        for (int i = 0; i < 4; i++) notiMessageFormat.rssi[i] = (byte) (notiMessageFormat.rssi[i] & 0xff);

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mTvSendData.setText("id: " + new String(notiMessageFormat.id) + "\n");
                                mTvSendData.setText(mTvSendData.getText() + "type: " + ConvertByteArrayToInt(notiMessageFormat.type) + "\n");
                                mTvSendData.setText(mTvSendData.getText() + "body_len: " + ConvertByteArrayToInt(notiMessageFormat.body_len) + "\n");
                                mTvSendData.setText(mTvSendData.getText() + "latitude: " + ConvertByteArrayToInt(notiMessageFormat.latitude) + "\n");
                                mTvSendData.setText(mTvSendData.getText() + "longitude: " + ConvertByteArrayToInt(notiMessageFormat.longitude) + "\n");
                                mTvSendData.setText(mTvSendData.getText() + "rssi: " + ConvertByteArrayToInt(notiMessageFormat.rssi) + "\n");
                            }
                        });

                        mThreadConnectedBluetooth.write(msgFormat);

                        Thread.sleep(100);
                    } catch (Exception e) {
                        Toast.makeText(getApplicationContext(), e.toString(), Toast.LENGTH_LONG).show();
                    }
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
        public void write(List<Byte> str) {
            Byte[] bytes = str.toArray(new Byte[str.size()]);
            byte[] sendBytes = new byte[bytes.length];
            for (int i = 0; i < bytes.length; i++) sendBytes[i] = bytes[i];
            try {
                mmOutStream.write(sendBytes);
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

    private class AccelometerListener implements SensorEventListener {

        @Override
        public void onSensorChanged(SensorEvent event) {


            if (event.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) {
                accMat = event.values.clone();
            }
            else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
                magMat = event.values.clone();
            }

            SensorManager.getRotationMatrix(rotMat, null, accMat, magMat);

            earth[0] = rotMat[0] * accMat[0] + rotMat[1] * accMat[1] + rotMat[2] * accMat[2];
            earth[1] = rotMat[3] * accMat[0] + rotMat[4] * accMat[1] + rotMat[5] * accMat[2];
            earth[2] = rotMat[6] * accMat[0] + rotMat[7] * accMat[1] + rotMat[8] * accMat[2];

            double accX = event.values[0];
            double accY = event.values[1];
            double accZ = event.values[2];

            // 전역변수에 저장
            currentAccX = accX;
            currentAccY = accY;
            currentAccZ = accZ;

            double angleXZ = Math.atan2(accX,  accZ) * 180/Math.PI;
            double angleYZ = Math.atan2(accY,  accZ) * 180/Math.PI;

            double x_val = 0;
            double x_pos = 0;

            double y_val = 0;
            double y_pos = 0;

            double timeSample = 0.1f;

            x_val = prevValX + ((0.5 * (currentAccX + prevAccX)) * timeSample);
            x_pos = prevPosX + ((0.5 * (x_val + prevValX)) * timeSample);

            y_val = prevValY + ((0.5 * (currentAccY + prevAccY)) * timeSample);
            y_pos = prevPosY + ((0.5 * (y_val + prevValY)) * timeSample);


//            mTvSendData.setText(x_val + " = " + prevValX + " + " + "((0.5 * (" + currentAccX + " + " + prevAccX + "))" + timeSample + ")\n\n");
//
//            mTvSendData.setText(mTvSendData.getText() + "" + x_pos + " = " + prevPosX + " + " + "((0.5 * (" + x_val + " + " + prevValX + "))" + timeSample + ")");
            mTvSendData.setText("x_pos: " + x_pos + ", y_pos: " + y_pos);
            prevPosX = x_pos;
            prevValX = x_val;

            prevPosY = y_pos;
            prevValY = y_val;

            prevAccX = currentAccX;
            prevAccY = currentAccY;

            accLogView.setText("device x: " + String.format("%.4f", accMat[0]) + ", y: " + String.format("%.4f", accMat[1]) + ", z: " + String.format("%.4f", accMat[2]) + "\n");
            accLogView.setText(accLogView.getText() + "earth x: " + String.format("%.4f", earth[0]) + ", y: " + String.format("%.4f", earth[1]) + ", z: " + String.format("%.4f", earth[2]));

        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int i) {

        }
    }
}