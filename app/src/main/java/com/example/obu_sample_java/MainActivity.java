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
    Switch refSwitch;
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

    final static double LATITUDE_TO_GPS_CONST = 0.111;
    final static double LONGITUDE_TO_GPS_CONST = 0.089;
    final static double GPS_MIN_UNIT = 0.000001;

    int lastLatitude = UNAVAILABLE_LATITUDE;
    int lastLongitude = UNAVAILABLE_LONGITUDE;
    int currentLatitude = UNAVAILABLE_LATITUDE;
    int currentLongitude = UNAVAILABLE_LONGITUDE;
    int currentRssi = UNAVAILABLE_RSSI;

    int refLatitude = UNAVAILABLE_LATITUDE;
    int refLongitude = UNAVAILABLE_LONGITUDE;

    double currentAccX = 0;
    double currentAccY = 0;
    double currentAccZ = 0;

    double prevAccX = 0;
    double prevAccY = 0;

    double prevValX = 0;
    double prevValY = 0;

    double prevPosX = 0;
    double prevPosY = 0;

    // 현재 이동 거리
    double currentPosX = 0;
    double currentPosY = 0;

    // IMU 센서
    private SensorManager mSensorManager = null;
    private SensorEventListener mAccLis;
    private Sensor mAccelometerSensor = null;
    private Sensor mMagneticSensor = null;
    private Sensor mGravitySensor = null;
    boolean isUsingGps = false;
    boolean isUsingRef = false;

    float[] accMat = new float[3];
    float[] magMat = new float[3];
    float[] gravity = new float[3];
    float[] earth = new float[3];
    float[] rotMat = new float[9];

    // 칼만 필터
    private KalmanFilter kalman;

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
        refSwitch = (Switch) findViewById(R.id.refSwitch);

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        refMessageFormat = new RefMessage();
        alertMessageFormat = new RefMessage();

        // Using the gyro and accel
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        // Using the accel
        mAccelometerSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        mMagneticSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        mGravitySensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
        mAccLis = new AccelometerListener();

        // Kalman
        kalman = new KalmanFilter(0.0f);

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
        mSensorManager.registerListener(mAccLis, mAccelometerSensor, 10000);
        mSensorManager.registerListener(mAccLis, mMagneticSensor, 10000);
        mSensorManager.registerListener(mAccLis, mGravitySensor, 10000);

        LocationListener locationListener = new LocationListener() {
            public void onLocationChanged(Location location) {
                try {
                    double lat = location.getLatitude();
                    double lng = location.getLongitude();

                    currentLatitude = (int) (lat * 10000000);
                    currentLongitude = (int) (lng * 10000000);

                    lastLatitude = currentLatitude;
                    lastLongitude = currentLongitude;

                    logView.setText("latitude: " + currentLatitude + "\nlongitude: " + currentLongitude);
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

        // GPS 사용 스위치 클릭 이벤트 콜백 등록한다.
        gpsSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                isUsingGps = isChecked;

                // GPS를 사용하지 않을 경우, 센서의 데이터를 사용한다.
                // 센서의 데이터를 사용하는 경우, 사용할 변수들을 초기화한다.
                // 센서의 데이터를 사용하는 경우, 가속도, 지자기, 중력 센서 이벤트 콜백을 등록한다.
                if (!isUsingGps) {
                    currentAccX = 0;
                    currentAccY = 0;
                    currentAccZ = 0;

                    prevAccX = 0;
                    prevAccY = 0;

                    prevValX = 0;
                    prevValY = 0;

                    prevPosX = 0;
                    prevPosY = 0;

                    currentPosX = 0;
                    currentPosY = 0;
                    mSensorManager.registerListener(mAccLis, mAccelometerSensor, 10000);
                    mSensorManager.registerListener(mAccLis, mMagneticSensor, 10000);
                    mSensorManager.registerListener(mAccLis, mGravitySensor, 10000);
                }

                // GPS를 사용하는 경우, 센서의 데이터를 사용하지 않는다.
                // 센서의 데이터를 사용하지 않는 경우, 센서 이벤트 콜백을 해제한다.
                else {
                    mSensorManager.unregisterListener(mAccLis);
                }
            }
        });

        // Ref GPS 사용 스위치 클릭 이벤트 콜백 등록한다.
        refSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                isUsingRef = isChecked;
            }
        });

        // 경고 메시지 전송 버튼 클릭 이벤트 콜백을 등록한다.
        mBtnSendData.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View view) {
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

        // 블루투스 수신 이벤트 콜백을 등록한다.
        mBluetoothHandler = new Handler() {
            public void handleMessage(android.os.Message msg) {

                if (msg.what == BT_MESSAGE_READ) {

                    //String readMessage = null;
                    try {
                        // 수신한 데이터를 저장하고 unsigned 형으로 변환한다.
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

                        // 저장한 메시지의 데이터를 로그에 출력한다.
                        mTvReceiveData.setText("id: " + new String(refMessageFormat.id) + "\n");
                        mTvReceiveData.setText(mTvReceiveData.getText() + "type: " + ConvertByteArrayToInt(refMessageFormat.type) + "\n");
                        mTvReceiveData.setText(mTvReceiveData.getText() + "body_len: " + ConvertByteArrayToInt(refMessageFormat.body_len) + "\n");
                        mTvReceiveData.setText(mTvReceiveData.getText() + "latitude: " + ConvertByteArrayToInt(refMessageFormat.latitude) + "\n");
                        mTvReceiveData.setText(mTvReceiveData.getText() + "longitude: " + ConvertByteArrayToInt(refMessageFormat.longitude) + "\n");

                        // 메시지의 참조좌표를 전역변수에 저장한다.
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

    /**
     * @brief 정수형 데이터를 바이트열 데이터로 변환한다. (32bits)
     * @param integer 변환할 정수형 데이터
     * @return 변환한 바이트열 데이터
     */
    private static byte[] ConvertIntToByteArray(final int integer) {
        ByteBuffer buff = ByteBuffer.allocate(Integer.SIZE / 8);
        buff.clear();
        buff.order(ByteOrder.LITTLE_ENDIAN);
        buff.putInt(integer);
        return buff.array();
    }

    /**
     * @brief 바이트열 데이터를 정수형 데이터로 변환한다. (32bits)
     * @param bytes 변환할 바이트열 데이터
     * @return 변환한 정수형 데이터
     */
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

    /**
     * @brief 블루투스 디바이스 탐색하여 RSSI를 출력한다.
     */
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            try {
                String action = intent.getAction();
                if (BluetoothDevice.ACTION_FOUND.equals(action)) {

                    // RSSI 값을 읽는다.
                    int rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE);

                    // 탐색한 디바이스 이름을 읽는다.
                    String name = intent.getStringExtra(BluetoothDevice.EXTRA_NAME);

                    // 문구를 출력할 UI 정보를 가져온다.
                    TextView rssi_msg = (TextView) findViewById(R.id.btRssi);

                    // 이름이 없는 디바이스는 무시한다.
                    if (name == null) {
                        return;
                    }

                    // 원하는 디바이스일 경우, 문구를 출력한다.
                    // RSSI 값이 -100일 경우, 출력하지 않는다.
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

    /**
     * @brief 블루투스를 활성화 했을 때, 문구를 출력한다.
     */
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

    /**
     * @brief 블루투스를 비활성화 했을 때, 문구를 출력한다.
     */
    void bluetoothOff() {
        if (mBluetoothAdapter.isEnabled()) {
            mBluetoothAdapter.disable();
            Toast.makeText(getApplicationContext(), "블루투스가 비활성화 되었습니다.", Toast.LENGTH_SHORT).show();
            mTvBluetoothStatus.setText("inactive");
        } else {
            Toast.makeText(getApplicationContext(), "블루투스가 이미 비활성화 되어 있습니다.", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * @brief 블루투스의 활성화 상태를 출력한다.
     * @param requestCode 블루투스 모듈 사용 가능 여부 확인 요청
     * @param resultCode 블루투스 모듈 사용 가능 여부 결과
     * @param data
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case BT_REQUEST_ENABLE:
                // 블루투스 활성화를 확인을 눌렀을 때, active로 설정한다.
                if (resultCode == RESULT_OK) {
                    Toast.makeText(getApplicationContext(), "블루투스 활성화", Toast.LENGTH_LONG).show();
                    mTvBluetoothStatus.setText("active");
                }
                // 블루투스 활성화를 취소를 눌렀을 때, inactive로 설정한다.
                else if (resultCode == RESULT_CANCELED) {
                    Toast.makeText(getApplicationContext(), "취소", Toast.LENGTH_LONG).show();
                    mTvBluetoothStatus.setText("inactive");
                }
                break;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    /**
     * @brief 블루투스 연결 버튼 클릭 이벤트 콜백
     * @details 블루투스 연결 버튼을 클릭했을 때, 블루투스 페어링된 디바이스의 목록 다이얼로그를 출력한다.
     */
    void listPairedDevices() {

        // 블루투스 모듈 사용 가능 여부를 확인한다.
        if (mBluetoothAdapter.isEnabled()) {

            // 블루투스 페어링된 디바이스의 목록을 가져온다.
            mPairedDevices = mBluetoothAdapter.getBondedDevices();

            // 블루투스 페어링된 디바이스가 있을 경우에만 동작한다.
            if (mPairedDevices.size() > 0) {

                // 다이얼로그를 선언한다.
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("장치 선택");

                // 다이얼로그에 디바이스 이름을 리스트에 저장한다.
                mListPairedDevices = new ArrayList<String>();
                for (BluetoothDevice device : mPairedDevices) {
                    mListPairedDevices.add(device.getName());
                    //mListPairedDevices.add(device.getName() + "\n" + device.getAddress());
                }

                final CharSequence[] items = mListPairedDevices.toArray(new CharSequence[mListPairedDevices.size()]);
                mListPairedDevices.toArray(new CharSequence[mListPairedDevices.size()]);

                // 다이얼로그에 다바이스 리스트를 출력하고 클릭 콜백함수를 지정한다.
                builder.setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int item) {
                        connectSelectedDevice(items[item].toString());
                    }
                });

                // 다이얼로그를 생성하고 출력한다.
                AlertDialog alert = builder.create();
                alert.show();
            } else {
                Toast.makeText(getApplicationContext(), "페어링된 장치가 없습니다.", Toast.LENGTH_LONG).show();
            }
        } else {
            Toast.makeText(getApplicationContext(), "블루투스가 비활성화 되어 있습니다.", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * @brief 블루투스 디바이스 선택 콜백
     * @details 불루투스 디바이스를 선택했을 때, 블루투스 연결 및 처리 스레드를 시작한다.
     * @details 블루투스 디바이스를 선택했을 때, Notification 메시지 전송 스레드를 시작한다.
     * @details 블루투스 디바이스를 선택했을 때, RSSI 측정 스레드를 시작한다.
     * @param selectedDeviceName 선택한 블루투스 디바이스 이름
     */
    void connectSelectedDevice(String selectedDeviceName) {

        // 블루투스 디바이스 이름을 통해 디바이스 정보를 저장한다.
        for (BluetoothDevice tempDevice : mPairedDevices) {
            if (selectedDeviceName.equals(tempDevice.getName())) {
                mBluetoothDevice = tempDevice;
                break;
            }
        }

        try {

            // 블루투스 디바이스 정보를 통해 블루투스 소켓을 생성하고 연결한다.
            mBluetoothSocket = mBluetoothDevice.createInsecureRfcommSocketToServiceRecord(BT_UUID);
            mBluetoothSocket.connect();

            // 블루투스 연결 및 처리 스레드를 등록하고 시작한다.
            mThreadConnectedBluetooth = new ConnectedBluetoothThread(mBluetoothSocket);
            mThreadConnectedBluetooth.start();

            // 블루투스 수신 세기 측정 스레드를 등록하고 시작한다.
            if (mThreadGetRssi == null) {
                mThreadGetRssi = new GetRssiThread();
                mThreadGetRssi.start();
            }

            // Notification 메시지 전송 스레드를 등록하고 시작한다.
            if (mThreadSendNoti == null) {
                mThreadSendNoti = new SendNotiMessageThread();
                mThreadSendNoti.start();
            }

            mBluetoothHandler.obtainMessage(BT_CONNECTING_STATUS, 1, -1).sendToTarget();

        } catch (IOException e) {
            Toast.makeText(getApplicationContext(), "블루투스 연결 중 오류가 발생했습니다." + e.toString(), Toast.LENGTH_LONG).show();
        }
    }

    /**
     * @brief Reference GPS 메시지 클래스
     */
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

    /**
     * @brief Notification 메시지 클래스
     */
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

    /**
     * @brief 블루투스의 수신 세기를 측정하는 스레드
     * @details 디바이스 탐색 기능을 사용하여 RSSI를 측정한다.
     */
    private class GetRssiThread extends Thread {
        public GetRssiThread() {
            // Register for broadcasts when a device is discovered
            registerReceiver(receiver, new IntentFilter(BluetoothDevice.ACTION_FOUND));

            mBluetoothAdapter.startDiscovery();
        }

        /**
         * @brief 탐색 시작/종룔를 반복하여 RSSI를 갱신한다.
         */
        public void run() {
            while (true) {
                if (mBluetoothAdapter.isDiscovering() == false) {
                    mBluetoothAdapter.startDiscovery();
                }
            }
        }
    }

    /**
     * @brief RSU에 Notification 메시지를 전송하는 스레드
     * @details 일정 주기로 RSU에 블루투스로 Notification 메시지를 전송한다.
     */
    private class SendNotiMessageThread extends Thread {
        public SendNotiMessageThread() {
            notiMessageFormat = new NotiMessage();
        }

        /**
         * @brief Notification 메시지 전송 프로세스
         * @details Notification 메시지 내용을 채우고 전송한다.
         */
        public void run() {
            while (true) {
                
                /* 블루투스 연결이 된 상태에서만 메시지를 전송한다. */
                if (mThreadConnectedBluetooth != null) {
                    try {

                        // 메시지 내용 채우기
                        notiMessageFormat.id = new String("HYES").getBytes();
                        notiMessageFormat.type = ConvertIntToByteArray(3);
                        notiMessageFormat.body_len = ConvertIntToByteArray(8);
                        notiMessageFormat.rssi = ConvertIntToByteArray(currentRssi);

                        /* 센서를 사용하는 경우, 마지막 GPS 정보에 가속도 센서의 이동거리를 더하여 좌표를 생성한다. */
                        if (!isUsingGps) {

                            // 이동 거리를 gps 이동 좌표로 계산
                            int accGpsPosX = (int)((GPS_MIN_UNIT * (currentPosX / LATITUDE_TO_GPS_CONST)) * 10000000);
                            int accGpsPosY = (int)((GPS_MIN_UNIT * (currentPosY / LONGITUDE_TO_GPS_CONST)) * 10000000);

                            // 참조 좌표를 사용하지 않는 경우, 마지막 GPS 정보에 가속도 센서의 이동거리를 더하여 좌표를 생성한다.
                            // 참조 좌표를 이용해서 OBU의 좌표를 보정하지 않는다.
                            if (!isUsingRef) {

                                // 마지막 gps 좌표에 이동 좌표를 더하여 전송
                                notiMessageFormat.latitude = ConvertIntToByteArray(lastLatitude + accGpsPosX);
                                notiMessageFormat.longitude = ConvertIntToByteArray(lastLongitude + accGpsPosY);
                            }

                            // 참조 좌표를 사용하는 경우, 마지막 GPS 정보에 가속도 센서의 이동거리를 더하여 좌표를 생성한다.
                            // 참조 좌표를 이용해서 OBU의 좌표를 보정한다.
                            else {
                                double a = lastLatitude / 10000000;
                                double b = lastLongitude / 10000000;
                                double c = refLatitude / 10000000;
                                double d = refLongitude / 10000000;

                                double accDoubleX = accGpsPosX / 10000000;
                                double accDoubleY = accGpsPosY / 10000000;

                                // 마지막 GPS 좌표와 참조 좌표의 직선을 구한다.
                                // 접하는 두 점
                                double pointX1, pointX2, pointY1, pointY2;

                                // 원 반지름
                                double r;

                                // obu, rsu를 지나는 직선의 기울기, 절편
                                double m, n;

                                // 2차 방정식 계수들
                                double X, Y;
                                double A, B, C, D;

                                r = Math.sqrt((accDoubleX * accDoubleX) + (accDoubleY * accDoubleY));

                                if (a != c) {

                                    m = (d - b) / (c - a);
                                    n = ((b * c) - (a * d)) / (c - a);

                                    A = (m * m) + 1;
                                    B = ((m * m) - (m * b) - a);
                                    C = ((a * a) + (b * b) - (r * r) + (n * n) - (2 * n * b));
                                    D = (B * B) - (A * C);

                                    // 직선과 원이 만나지 않는 경우
                                    if (D < 0) {
                                        // nothing
                                    }

                                    // 직선과 원이 한 점에서 접하는 경우
                                    if (D == 0) {
                                        // nothing
                                    }

                                    // 직선과 원이 두 점에서 접하는 경우
                                    if (D > 0) {
                                        X = -(B + Math.sqrt(D)) / A;
                                        Y = (m * X) + n;
                                        pointX1 = X;
                                        pointY1 = Y;

                                        X = -(B - Math.sqrt(D)) / A;
                                        Y = (m * X) + n;
                                        pointX2 = X;
                                        pointY2 = Y;

                                        // 두 점 중에서 rsu(ref)와 가까운 점을 사용한다.
                                        double dist1 = Math.sqrt(Math.pow(c - pointX1, 2) + Math.pow(d - pointY1, 2));
                                        double dist2 = Math.sqrt(Math.pow(c - pointX2, 2) + Math.pow(d - pointY2, 2));

                                        notiMessageFormat.latitude = ConvertIntToByteArray(dist1 < dist2 ? (int)(pointX1 * 10000000) : (int)(pointX2 * 10000000));
                                        notiMessageFormat.longitude = ConvertIntToByteArray(dist1 < dist2 ? (int)(pointY1 * 10000000) : (int)(pointY2 * 10000000));

                                    }
                                }
                                // lastLatitude == refLatitude 일 경우, 수직선
                                else {

                                    // 직선과 원이 만나지 않는 경우
                                    if (lastLatitude < (lastLatitude - r) || lastLatitude > (lastLatitude + r)) {
                                        // nothing
                                    }

                                    // 직선과 원이 한 점에서 접하는 경우
                                    if (lastLatitude == (lastLatitude - r) || lastLatitude == (lastLongitude + r)) {
                                        // nothing
                                    }

                                    // 직선과 원이 두 점에서 접하는 경우
                                    if (lastLatitude > (lastLongitude - r) && lastLatitude < (lastLongitude + r)) {
                                        X = lastLatitude;
                                        Y = lastLongitude + Math.sqrt((r * r) - ((lastLatitude - lastLatitude) * (lastLatitude - lastLatitude)));
                                        pointX1 = X;
                                        pointY1 = Y;

                                        X = lastLatitude;
                                        Y = lastLongitude - Math.sqrt((r * r) - ((lastLatitude - lastLatitude) * (lastLatitude - lastLatitude)));
                                        pointX2 = X;
                                        pointY2 = Y;

                                        // 두 점 중에서 rsu(ref)와 가까운 점을 사용한다.
                                        double dist1 = Math.sqrt(Math.pow(c - pointX1, 2) + Math.pow(d - pointY1, 2));
                                        double dist2 = Math.sqrt(Math.pow(c - pointX2, 2) + Math.pow(d - pointY2, 2));

                                        notiMessageFormat.latitude = ConvertIntToByteArray(dist1 < dist2 ? (int)(pointX1 * 10000000) : (int)(pointX2 * 10000000));
                                        notiMessageFormat.longitude = ConvertIntToByteArray(dist1 < dist2 ? (int)(pointY1 * 10000000) : (int)(pointY2 * 10000000));
                                    }
                                }
                            }
                        }
                        
                        /* 센서를 사용하지 않는 경우, GPS 좌표를 사용한다. */
                        else {
                            notiMessageFormat.latitude = ConvertIntToByteArray(currentLatitude);
                            notiMessageFormat.longitude = ConvertIntToByteArray(currentLongitude);
                        }
                        
                        // 각 데이터를 바이트열로 변환
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

                        // 바이트열을 모두 unsigned 형태로 변환한다.
                        for (int i = 0; i < 4; i++) notiMessageFormat.id[i] = (byte) (notiMessageFormat.id[i] & 0xff);
                        for (int i = 0; i < 4; i++) notiMessageFormat.type[i] = (byte) (notiMessageFormat.type[i] & 0xff);
                        for (int i = 0; i < 4; i++) notiMessageFormat.body_len[i] = (byte) (notiMessageFormat.body_len[i] & 0xff);
                        for (int i = 0; i < 4; i++) notiMessageFormat.latitude[i] = (byte) (notiMessageFormat.latitude[i] & 0xff);
                        for (int i = 0; i < 4; i++) notiMessageFormat.longitude[i] = (byte) (notiMessageFormat.longitude[i] & 0xff);
                        for (int i = 0; i < 4; i++) notiMessageFormat.rssi[i] = (byte) (notiMessageFormat.rssi[i] & 0xff);

                        // 생성한 메시지를 로그에 출력한다.
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

                        // 메시지 전송
                        mThreadConnectedBluetooth.write(msgFormat);

                        // 메시지 전송 주기는 100밀리초이다.
                        Thread.sleep(100);

                    } catch (Exception e) {
                        Toast.makeText(getApplicationContext(), e.toString(), Toast.LENGTH_LONG).show();
                    }
                }
            }
        }
    }

    /**
     * @brief 블루투스 연결 스레드
     * @details 블루투스 시리얼 스트림을 등록한다.
     * @details 블루투스 시리얼 스트림 내용을 읽거나 쓴다.
     */
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

    /**
     * @brief 센서 이벤트 콜백
     * @details 가속도 센서, 중력 센서, 지자기 센서의 값을 읽고 저장한다.
     */
    private class AccelometerListener implements SensorEventListener {

        @Override
        public void onSensorChanged(SensorEvent event) {

            // 가속도 센서 값 읽기
            if (event.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) {
                
                // 가속도 센서 값을 복사하여 저장한다.
                accMat = event.values.clone();

                // 가속도 센서 값을 칼만 필터를 통해 정밀도를 올린다.
                accMat[0] = kalman.update(accMat[0]);
                accMat[1] = kalman.update(accMat[1]);
                accMat[2] = kalman.update(accMat[2]);

                // 가속도 매트릭스, 마그네틱 매트릭스를 통해 로테이션 매트릭스 생성
                SensorManager.getRotationMatrix(rotMat, null, gravity, magMat);

                // 로테이션 매트릭스를 통해 디바이스 기반의 가속도 센서 값을 지구계 기반의 가속도 센서 값으로 변환
                earth[0] = (rotMat[0] * accMat[0]) + (rotMat[1] * accMat[1]) + (rotMat[2] * accMat[2]);
                earth[1] = (rotMat[3] * accMat[0]) + (rotMat[4] * accMat[1]) + (rotMat[5] * accMat[2]);
                earth[2] = (rotMat[6] * accMat[0]) + (rotMat[7] * accMat[1]) + (rotMat[8] * accMat[2]);

                // 변환 값을 전역변수 값에 저장
                currentAccX = earth[0];
                currentAccY = earth[1];
                currentAccZ = earth[2];

                // 가속도 센서 값을 누적 거리 데이터로 변환
                double x_val = 0;
                double x_pos = 0;

                double y_val = 0;
                double y_pos = 0;

                double timeSample = 0.01f;

                // 가속도 값을 속도 값으로 적분
                x_val = prevValX + ((0.5 * (currentAccX + prevAccX)) * timeSample);
                y_val = prevValY + ((0.5 * (currentAccY + prevAccY)) * timeSample);

                // 속도 값을 누적 거리 값으로 적분
                x_pos = prevPosX + ((0.5 * (x_val + prevValX)) * timeSample);
                y_pos = prevPosY + ((0.5 * (y_val + prevValY)) * timeSample);

                // 현재 적분 데이터를 이전 데이터에 저장
                prevPosX = x_pos;
                prevValX = x_val;

                prevPosY = y_pos;
                prevValY = y_val;

                prevAccX = currentAccX;
                prevAccY = currentAccY;

                currentPosX = x_pos;
                currentPosY = y_pos;

                // Accelometer Data에 출력 (지구계 기반의 데이터만 출력)
                // accLogView.setText("device x: " + String.format("%.4f", accMat[0]) + ", y: " + String.format("%.4f", accMat[1]) + ", z: " + String.format("%.4f", accMat[2]) + "\n");
                accLogView.setText("earth x: " + String.format("%.4f", currentAccX) + ", y: " + String.format("%.4f", currentAccY) + ", z: " + String.format("%.4f", currentAccZ) + "\n");
                accLogView.setText(accLogView.getText() + "earth x_pos: " + String.format("%.4f", x_pos) + ", y_pos: " + String.format("%.4f", y_pos));
            }
            
            // 지자기 센서의 값을 읽고 복사하여 저장한다.
            else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
                magMat = event.values.clone();
            }
            
            // 중력 센서의 값을 읽고 복사하여 저장한다.
            else if (event.sensor.getType() == Sensor.TYPE_GRAVITY) {
                gravity = event.values.clone();
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int i) {

        }
    }

    //칼만필터를 클래스로 선언한다. 여기에 쓰이는 공식은 이미 여러 사이트에 소개되어있다.
    class KalmanFilter {
        private float Q = 0.00001f;
        private float R = 0.001f;
        private float X = 0, P = 1, K;

        //첫번째값을 입력받아 초기화 한다. 예전값들을 계산해서 현재값에 적용해야 하므로 반드시 하나이상의 값이 필요하므로~
        KalmanFilter(float initValue) {
            X = initValue;
        }

        //예전값들을 공식으로 계산한다
        private void measurementUpdate(){
            K = (P + Q) / (P + Q + R);
            P = R * (P + Q) / (R + P + Q);
        }

        //현재값을 받아 계산된 공식을 적용하고 반환한다
        public float update(float measurement){
            measurementUpdate();
            X = X + (measurement - X) * K;

            return X;
        }
    }
}