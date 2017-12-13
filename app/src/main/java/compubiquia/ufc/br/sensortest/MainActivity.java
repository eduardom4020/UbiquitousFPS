package compubiquia.ufc.br.sensortest;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
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
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import org.json.JSONException;
import org.json.JSONObject;

import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;

public class MainActivity extends AppCompatActivity implements SensorEventListener, LocationListener {

    private static final int REQUEST_CODE_LOC = 11;
    private double distance;
    private SensorManager sensor_manager;
    Sensor accelerometer;
    Sensor magnetometer;
    Sensor gyroscope;

    float[] vGravity;
    float[] vGeomagnetic;
    float[] vTilt;

    TextView inclination_text;
    TextView bang_text;
    TextView compass_text;
    TextView hp_text;
    TextView id_text;

    boolean shoot = false;

    MediaPlayer shootMP;

    float prev_angle;
    ArrayList<Float> scope_list;
    float prev_tilt;

    float tilt = 0;

    float compass = 0;
    float scope_angle = 0;

    boolean scope_lock = false;

    //private Handler angle_handler = new Handler();
    private Handler shoot_handler = new Handler();

    private BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
    /*
    private Runnable angle_runnable = new Runnable() {
        @Override
        public void run() {
            prev_angle = angle;
            angle_handler.postDelayed(angle_runnable, 200);
        }
    };

    private Runnable tilt_runnable = new Runnable() {
        @Override
        public void run() {
            prev_tilt = pitch;
            tilt_handler.postDelayed(tilt_runnable, 200);
        }
    };*/

    private final BroadcastReceiver receiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if(BluetoothDevice.ACTION_FOUND.equals(action)) {
                // RSSI
                int rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE);
                // Distância calculada
                String name = intent.getStringExtra(BluetoothDevice.EXTRA_NAME);
                if(name != null && name.equals("TMR@Phone")){
                    distance = calculateDistance(rssi);
                }
            }
        }
    };

    private double calculateDistance(int rssi) {
        int txPower = -59;
        if (rssi == 0) {
            return -1.0;
        }
        double ratio = rssi * 1.0 / txPower;
        if (ratio < 1.0) {
            return Math.pow(ratio, 10);
        }
        else {
            double distance =  (0.89976) * Math.pow(ratio, 7.7095) + 0.111;
            return distance;
        }
    }

    private Runnable tilt_runnable = new Runnable() {
        @Override
        public void run() {
            prev_tilt = tilt;

//            if(!shoot) {
//                prev_pitch = pitch;
//            }

            shoot_handler.postDelayed(tilt_runnable, 80);
        }
    };

    private Runnable shoot_runnable = new Runnable() {
        @Override
        public void run() {
            //scope_list.add(scope_angle);

            //Log.i("Scope", ""+scope_angle);

            inclination_text.setText("" + scope_angle);

            shoot_handler.postDelayed(shoot_runnable, 20);
        }
    };

    private Socket mSocket;
    private Timer timer = new Timer();
    private LocationManager locationManager;
    private static final int HANDLER_DELAY = 500;
    private static final int GPS_TIME_INTERVAL = 4000; // get gps location every 1 min
    private static final int GPS_DISTANCE = 1;
    private final Handler gpsHandler = new Handler();
    private String hp;
    private String id;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            accessLocationPermission();
        }

        sensor_manager = (SensorManager) getSystemService(SENSOR_SERVICE);
        accelerometer = sensor_manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        magnetometer = sensor_manager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        gyroscope = sensor_manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        locationManager = (LocationManager) MainActivity.this.getSystemService(Context.LOCATION_SERVICE);

        inclination_text = (TextView) findViewById(R.id.angle);
        bang_text = (TextView) findViewById(R.id.bang);
        compass_text = (TextView) findViewById(R.id.compass);
        hp_text = (TextView) findViewById(R.id.hp);
        id_text = (TextView) findViewById(R.id.idU);

        shootMP = MediaPlayer.create(this, R.raw.shoot);

        //angle_handler.post(angle_runnable);
        shoot_handler.post(tilt_runnable);
        shoot_handler.post(shoot_runnable);

        scope_list = new ArrayList<>();

        // Connection of the socket

        //SocketApplication app = (SocketApplication) this.getApplication();

        //mSocket = app.getSocket();


        try {
            mSocket = IO.socket(Constants.SERVER_URL);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }

        mSocket.on("get_id", idMessage);
        mSocket.on("get_hp", hpMessage);

        mSocket.connect();

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        registerReceiver(receiver, filter);

        gpsHandler.postDelayed(new Runnable() {
            public void run() {
                sendGPSData();
                btAdapter.startDiscovery();
                gpsHandler.postDelayed(this, HANDLER_DELAY);
            }
        }, HANDLER_DELAY);




        //mSocket.on("message", onNewMessage);


    }

    protected void onResume() {
        super.onResume();
        sensor_manager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        sensor_manager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_NORMAL);
        sensor_manager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_NORMAL);

        //angle_handler.post(angle_runnable);
        shoot_handler.post(tilt_runnable);
        shoot_handler.post(shoot_runnable);
    }

    protected void onPause() {
        super.onPause();
        sensor_manager.unregisterListener(this);

        //angle_handler.removeCallbacks(angle_runnable);
        shoot_handler.removeCallbacks(tilt_runnable);
        shoot_handler.removeCallbacks(shoot_runnable);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        mSocket.disconnect();
        mSocket.off("get_id", idMessage);
        mSocket.off("get_hp", hpMessage);
        //mSocket.off("message", onNewMessage);
        //mSocket.off("login", onLogin);
        unregisterReceiver(receiver);
    }

    //private EditText mInputMessageView;

    private void sendShootData(Float compass, double distance) {
        //String message = mInputMessageView.getText().toString().trim();
        //String message = "The gun fired!!!";
        /*if (TextUtils.isEmpty(message)) {
            return;
        }*/

        //mInputMessageView.setText("");
        //mSocket.emit("message", message);
        String shoot = distance + " " + compass;
        mSocket.emit("shoot", shoot);
    }

    private void sendGPSData() {

        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return;
            }
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                    GPS_TIME_INTERVAL, GPS_DISTANCE, this);

            Location gpslocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);



            //String location = gpslocation.getLatitude()+" "+gpslocation.getLongitude();
            //mSocket.emit("set_location", location);


        }
    }

    private Emitter.Listener hpMessage = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            (MainActivity.this).runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Integer data = (Integer) args[0];

                    String hp;
                    try {
                        //hp = data.getString("max_hp");
                    } catch (Exception e) {
                        return;
                    }

                    // add the message to view
                    addHP("HP: "+data.toString());
                }
            });
        }
    };

    private Emitter.Listener idMessage = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            (MainActivity.this).runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    String data = (String) args[0];

                    String hp;
                    try {
                        //hp = data.getString("max_hp");
                    } catch (Exception e) {
                        return;
                    }

                    final int mid = data.length() / 2; //get the middle of the String
                    String[] parts = {data.substring(0, mid),data.substring(mid)};

                    // add the message to view
                    addID("ID: "+parts[0]);
                }
            });
        }
    };

    public void addHP(String hp){
        this.hp = hp;
        hp_text.setText(this.hp);
    }

    public void addID(String id){
        this.id = id;
        id_text.setText(this.id);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if(gyroscope != null) {
            if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER)
                vGravity = event.values;
            if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD)
                vGeomagnetic = event.values;
            if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE)
                vTilt = event.values;

            if (vGravity != null && vGeomagnetic != null) {
                float rot[] = new float[9];
                float I[] = new float[9];
                boolean success = SensorManager.getRotationMatrix(rot, I, vGravity, vGeomagnetic);
                if (success) {
                    float orientation[] = new float[3];
                    SensorManager.getOrientation(rot, orientation);

                    /*float[] inclineGravity = vGravity.clone();

                    double norm_Of_g = Math.sqrt(inclineGravity[0] * inclineGravity[0] + inclineGravity[1] * inclineGravity[1] + inclineGravity[2] * inclineGravity[2]);

                    inclineGravity[0] = (float) (inclineGravity[0] / norm_Of_g);
                    inclineGravity[1] = (float) (inclineGravity[1] / norm_Of_g);
                    inclineGravity[2] = (float) (inclineGravity[2] / norm_Of_g);

                    //Checks if device is flat on ground or not
                    int inclination = (int) Math.round(Math.toDegrees(Math.asin(inclineGravity[1])));
                    //scope_angle = inclination;

                    //Log.i("Inclination", "" + inclination);*/

                    float azimut = (float) Math.toDegrees(orientation[0]); // azimut
                    compass = 180.0f - Math.round(azimut);
                    //compass = azimut;
                    float roll = (float) Math.toDegrees(orientation[2]);

                    float pitch = (float) Math.toDegrees(orientation[1]);
                    scope_angle = pitch;
                    if(!shoot) {
                        scope_list.add(scope_angle);
                    }


                    /*if(prev_tilt - pitch >= 40.0f && !shoot) {
                        textView.setText((prev_tilt - pitch) + "");

                        if(shootMP.isPlaying()) {
                            shootMP.stop();
                            try {
                                shootMP.prepare();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }

                        shootMP.start();
                        shoot = true;

                        new Handler().postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                //textView.setText("");
                                shoot = false;
                            }
                        }, 300);
                    }*/
                }
            }

            if(vTilt != null) {
                tilt = vTilt[2];

                //prev_pitch = pitch;

//                if(tilt > -0.2f && tilt < 0.2f && scope_lock) {
//                    shoot_handler.post(shoot_runnable);
//                    scope_lock = false;
//                }
//
//                if(tilt < -0.8f && !scope_lock) {
//                    shoot_handler.removeCallbacks(shoot_runnable);
//                    scope_lock = true;
//                }

                if(tilt < -3.0f && tilt - prev_tilt < -5.0f && !shoot) {
                    if(shootMP.isPlaying()) {
                        shootMP.stop();
                        try {
                            shootMP.prepare();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }

                    shootMP.start();
                    shoot = true;

                    shoot_handler.removeCallbacks(shoot_runnable);
                    Float prev_scope = null;

                    int count = 0;
                    
                    for(Float scope : scope_list) {
                        if(prev_scope == null) {
                            prev_scope = scope;
                        }
                        else if((prev_scope >= 0 && scope < 0) || (prev_scope < 0 && scope >= 0)) {
                            break;
                        }
                        else {
                            prev_scope += scope;
                            count += 1;
                        }
                    }

                    int limit = count - 30;

                    float sum = 0;
                    int length =0;

                    while(count > 0 && count > limit) {
                        sum += scope_list.get(count);
                        length += 1;
                        count -= 1;

                        Log.i("Traceback", ""+scope_list.get(count));
                    }
                    Log.i("List End", "--------------------------------------");

                    prev_scope = sum/length;

                    bang_text.setText(prev_scope.toString());
                    compass_text.setText(Float.toString(compass));



                    scope_list.clear();

                    //Log.i("In_shoot", "Bang! " + scope_list);

                    // **************************** The gun fired at this moment ****************************
                    sendShootData(compass, distance);

                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            //textView.setText("");
//                            scope_list.clear();
                            shoot_handler.post(shoot_runnable);
                            shoot = false;
                        }
                    }, 500);
                }
            }
        }
        else {
            //textView.setText("Erro: Gyroscópio não encontrado.");
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    @Override
    public void onLocationChanged(Location location) {

        locationManager.removeUpdates(this);
        //Toast.makeText(MainActivity.this,"Lat: "+location.getLatitude() + " Long: " + location.getLongitude() + " ", Toast.LENGTH_LONG).show();
    }

    @Override
    public void onStatusChanged(String s, int i, Bundle bundle) {

    }

    @Override
    public void onProviderEnabled(String s) {

    }

    @Override
    public void onProviderDisabled(String s) {

    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void accessLocationPermission() {
        int accessCoarseLocation = checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION);
        int accessFineLocation   = checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION);

        List<String> listRequestPermission = new ArrayList<String>();

        if (accessCoarseLocation != PackageManager.PERMISSION_GRANTED) {
            listRequestPermission.add(android.Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        if (accessFineLocation != PackageManager.PERMISSION_GRANTED) {
            listRequestPermission.add(android.Manifest.permission.ACCESS_FINE_LOCATION);
        }

        if (!listRequestPermission.isEmpty()) {
            String[] strRequestPermission = listRequestPermission.toArray(new String[listRequestPermission.size()]);
            requestPermissions(strRequestPermission, REQUEST_CODE_LOC);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_CODE_LOC:
                if (grantResults.length > 0) {
                    for (int gr : grantResults) {
                        if (gr != PackageManager.PERMISSION_GRANTED) {
                            return;
                        }
                    }
                    btAdapter.startDiscovery();
                }
                break;
            default:
                return;
        }
    }
}
