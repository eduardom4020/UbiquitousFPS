package compubiquia.ufc.br.sensortest;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;

import java.io.IOException;
import java.net.URISyntaxException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
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
    TextView position_text;
    Button view_in_map;

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

//            inclination_text.setText("" + scope_angle);
//
//            shoot_handler.postDelayed(shoot_runnable, 20);
        }
    };

    private Socket mSocket;
    private Timer timer = new Timer();
    private static final int HANDLER_DELAY = 500;
    private static final int GPS_TIME_INTERVAL = 4000; // get gps location every 1 min
    private static final int GPS_DISTANCE = 1;
    private final Handler gpsHandler = new Handler();
    private String hp;
    private String id;

    /*
     * LOCATION VARIABLES
     */

    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int REQUEST_PERMISSIONS_REQUEST_CODE = 34;
    private static final int REQUEST_CHECK_SETTINGS = 0x1;
    private static final long UPDATE_INTERVAL_IN_MILLISECONDS = 200;
    private static final long FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS =
            UPDATE_INTERVAL_IN_MILLISECONDS / 2;
    private final static String KEY_REQUESTING_LOCATION_UPDATES = "requesting-location-updates";
    private final static String KEY_LOCATION = "location";
    private final static String KEY_LAST_UPDATED_TIME_STRING = "last-updated-time-string";
    private FusedLocationProviderClient mFusedLocationClient;
    private SettingsClient mSettingsClient;
    private LocationRequest mLocationRequest;
    private LocationSettingsRequest mLocationSettingsRequest;
    private LocationCallback mLocationCallback;
    private Location mCurrentLocation;
    private Boolean mRequestingLocationUpdates;
    private String mLastUpdateTime;

    private float[] cartesian;

    /*
     * LOCATION VARIABLES
     */

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        cartesian = new float[3];

        sensor_manager = (SensorManager) getSystemService(SENSOR_SERVICE);
        accelerometer = sensor_manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        magnetometer = sensor_manager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        gyroscope = sensor_manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);

//        inclination_text = (TextView) findViewById(R.id.angle);
//        bang_text = (TextView) findViewById(R.id.bang);
        compass_text = (TextView) findViewById(R.id.compass);
        hp_text = (TextView) findViewById(R.id.hp);
        id_text = (TextView) findViewById(R.id.idU);
        position_text = findViewById(R.id.location_tv);
        view_in_map = findViewById(R.id.bt_view_map);

        mRequestingLocationUpdates = false;
        mLastUpdateTime = "";

        updateValuesFromBundle(savedInstanceState);

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        mSettingsClient = LocationServices.getSettingsClient(this);

        createLocationCallback();
        createLocationRequest();
        buildLocationSettingsRequest();

        view_in_map.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(position_text.getText() != null) {
                    String[] tokens = position_text.getText().toString().split(" ");

                    String location = tokens[0] + "," + tokens[1];

                    Intent intent = new Intent(android.content.Intent.ACTION_VIEW,
                    Uri.parse("http://maps.google.com/maps?q=" + location + "&center=" + location));
                    startActivity(intent);
                }
            }
        });

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

    }

    private void updateValuesFromBundle(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            if (savedInstanceState.keySet().contains(KEY_REQUESTING_LOCATION_UPDATES)) {
                mRequestingLocationUpdates = savedInstanceState.getBoolean(
                        KEY_REQUESTING_LOCATION_UPDATES);
            }

            if (savedInstanceState.keySet().contains(KEY_LOCATION)) {
                mCurrentLocation = savedInstanceState.getParcelable(KEY_LOCATION);
            }

            if (savedInstanceState.keySet().contains(KEY_LAST_UPDATED_TIME_STRING)) {
                mLastUpdateTime = savedInstanceState.getString(KEY_LAST_UPDATED_TIME_STRING);
            }
            updateLocation();
        }
    }

    private void createLocationRequest() {
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(UPDATE_INTERVAL_IN_MILLISECONDS);
        mLocationRequest.setFastestInterval(FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    private void createLocationCallback() {
        mLocationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                super.onLocationResult(locationResult);

                mCurrentLocation = locationResult.getLastLocation();
                mLastUpdateTime = DateFormat.getTimeInstance().format(new Date());
                updateLocation();
            }
        };
    }

    private void buildLocationSettingsRequest() {
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder();
        builder.addLocationRequest(mLocationRequest);
        mLocationSettingsRequest = builder.build();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_CHECK_SETTINGS:
                switch (resultCode) {
                    case Activity.RESULT_OK:
                        Log.i(TAG, "User agreed to make required location settings changes.");
                        break;
                    case Activity.RESULT_CANCELED:
                        Log.i(TAG, "User chose not to make required location settings changes.");
                        mRequestingLocationUpdates = false;
                        updateLocation();
                        break;
                }
                break;
        }
    }

    private void startLocationUpdates() {
        mRequestingLocationUpdates = true;
        mSettingsClient.checkLocationSettings(mLocationSettingsRequest)
                .addOnSuccessListener(this, new OnSuccessListener<LocationSettingsResponse>() {
                    @SuppressLint("MissingPermission")
                    @Override
                    public void onSuccess(LocationSettingsResponse locationSettingsResponse) {
                        Log.i(TAG, "All location settings are satisfied.");

                        mFusedLocationClient.requestLocationUpdates(mLocationRequest,
                                mLocationCallback, Looper.myLooper());

                        updateLocation();
                    }
                })
                .addOnFailureListener(this, new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        int statusCode = ((ApiException) e).getStatusCode();
                        switch (statusCode) {
                            case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                                Log.i(TAG, "Location settings are not satisfied. Attempting to upgrade " +
                                        "location settings ");
                                try {
                                    ResolvableApiException rae = (ResolvableApiException) e;
                                    rae.startResolutionForResult(MainActivity.this, REQUEST_CHECK_SETTINGS);
                                } catch (IntentSender.SendIntentException sie) {
                                    Log.i(TAG, "PendingIntent unable to execute request.");
                                }
                                break;
                            case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                                String errorMessage = "Location settings are inadequate, and cannot be " +
                                        "fixed here. Fix in Settings.";
                                Log.e(TAG, errorMessage);
                                Toast.makeText(MainActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                                mRequestingLocationUpdates = false;
                        }

                        updateLocation();
                    }
                });
    }

    private void updateLocation() {
        if (mCurrentLocation != null) {
            mCurrentLocation.setAccuracy(0.3f);

            double latitude = mCurrentLocation.getLatitude();
            double longitude = mCurrentLocation.getLongitude();

            position_text.setText(latitude + " " + longitude);

            float earth_radius = 6371000.0f;
            double rad_latitude = Math.toRadians(latitude);
            double rad_longitude = Math.toRadians(longitude);

            cartesian[0] = earth_radius * (float)Math.cos(rad_latitude) * (float)Math.cos(rad_longitude);
            cartesian[1] = earth_radius * (float)Math.cos(rad_latitude) * (float)Math.sin(rad_longitude);
            cartesian[2] = Math.abs(earth_radius * (float)Math.sin(rad_latitude));

            mSocket.emit("set_location", cartesian[0] + " " + cartesian[1]);
        }
    }

    private void stopLocationUpdates() {
        mRequestingLocationUpdates = false;
        if (!mRequestingLocationUpdates) {
            Log.d(TAG, "stopLocationUpdates: updates never requested, no-op.");
            return;
        }

        mFusedLocationClient.removeLocationUpdates(mLocationCallback)
                .addOnCompleteListener(this, new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        mRequestingLocationUpdates = false;
                    }
                });
    }

    protected void onResume() {
        super.onResume();
        sensor_manager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        sensor_manager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_NORMAL);
        sensor_manager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_NORMAL);

        //angle_handler.post(angle_runnable);
        shoot_handler.post(tilt_runnable);
        shoot_handler.post(shoot_runnable);

        if (checkPermissions()) {
            startLocationUpdates();
        } else if (!checkPermissions()) {
            requestPermissions();
        }

        updateLocation();
    }

    public void onSaveInstanceState(Bundle savedInstanceState) {
        savedInstanceState.putBoolean(KEY_REQUESTING_LOCATION_UPDATES, mRequestingLocationUpdates);
        savedInstanceState.putParcelable(KEY_LOCATION, mCurrentLocation);
        savedInstanceState.putString(KEY_LAST_UPDATED_TIME_STRING, mLastUpdateTime);
        super.onSaveInstanceState(savedInstanceState);
    }

    private boolean checkPermissions() {
        int permissionState = ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION);
        return permissionState == PackageManager.PERMISSION_GRANTED;
    }

    private void showSnackbar(final int mainTextStringId, final int actionStringId,
                              View.OnClickListener listener) {
        Snackbar.make(
                findViewById(android.R.id.content),
                getString(mainTextStringId),
                Snackbar.LENGTH_INDEFINITE)
                .setAction(getString(actionStringId), listener).show();
    }

    private void requestPermissions() {
        boolean shouldProvideRationale =
                ActivityCompat.shouldShowRequestPermissionRationale(this,
                        Manifest.permission.ACCESS_FINE_LOCATION);

        if (shouldProvideRationale) {
            Log.i(TAG, "Displaying permission rationale to provide additional context.");
            showSnackbar(R.string.permission_rationale,
                    android.R.string.ok, new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            ActivityCompat.requestPermissions(MainActivity.this,
                                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                                    REQUEST_PERMISSIONS_REQUEST_CODE);
                        }
                    });
        } else {
            Log.i(TAG, "Requesting permission");
            ActivityCompat.requestPermissions(MainActivity.this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_PERMISSIONS_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        Log.i(TAG, "onRequestPermissionResult");
        if (requestCode == REQUEST_PERMISSIONS_REQUEST_CODE) {
            if (grantResults.length <= 0) {
                Log.i(TAG, "User interaction was cancelled.");
            } else if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (mRequestingLocationUpdates) {
                    Log.i(TAG, "Permission granted, updates requested, starting location updates");
                    startLocationUpdates();
                }
            } else {
                showSnackbar(R.string.permission_denied_explanation,
                        R.string.settings, new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                Intent intent = new Intent();
                                intent.setAction(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                Uri uri = Uri.fromParts("package",
                                        BuildConfig.APPLICATION_ID, null);
                                intent.setData(uri);
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                startActivity(intent);
                            }
                        });
            }
        }
    }

    protected void onPause() {
        super.onPause();
        sensor_manager.unregisterListener(this);

        //angle_handler.removeCallbacks(angle_runnable);
        shoot_handler.removeCallbacks(tilt_runnable);
        shoot_handler.removeCallbacks(shoot_runnable);
        stopLocationUpdates();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        mSocket.disconnect();
        mSocket.off("get_id", idMessage);
        mSocket.off("get_hp", hpMessage);
        //mSocket.off("message", onNewMessage);
        //mSocket.off("login", onLogin);
    }

    //private EditText mInputMessageView;

    private void sendShootData() {
//        String shoot = distance + " " + compass + " " + cartesian;

        float gun_range = 10.0f;
        float rad_compass = (float)Math.toRadians(compass);

        //to trace ray we only need a point generated by compass. For this we use the
        //parametric function of circunference:

        float ref_x = cartesian[0] + gun_range * (float)Math.cos(rad_compass);
        float ref_y = cartesian[1] + gun_range * (float)Math.sin(rad_compass);

        String shoot = cartesian[0] + " " + cartesian[1] + " " + cartesian[2]
                + " " + ref_x + " " + ref_y;

        mSocket.emit("shoot", shoot);
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
                    float outRot[] = new float[9];

                    SensorManager.remapCoordinateSystem(rot, SensorManager.AXIS_X,
                            SensorManager.AXIS_MINUS_Y, outRot);
                    SensorManager.getOrientation(outRot, orientation);

                    //need to prevent azimut changes by rotating cellphone
                    float azimut = (float) Math.toDegrees(orientation[0]); // azimut
                    compass = 180.0f - Math.round(azimut);

                    compass_text.setText(compass + "º");

                    float roll = (float) Math.toDegrees(orientation[2]);

                    float pitch = (float) Math.toDegrees(orientation[1]);
                    scope_angle = pitch;
                    if(!shoot) {
                        scope_list.add(scope_angle);
                    }
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
                    //use socket here with prev_scope and compass

//                    bang_text.setText(prev_scope.toString());
//                    compass_text.setText(Float.toString(compass));



                    scope_list.clear();

                    //Log.i("In_shoot", "Bang! " + scope_list);

                    // **************************** The gun fired at this moment ****************************
//                    sendShootData(compass, distance);
                    sendShootData();

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
}
