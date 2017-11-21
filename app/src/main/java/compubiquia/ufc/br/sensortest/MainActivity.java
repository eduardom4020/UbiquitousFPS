package compubiquia.ufc.br.sensortest;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.MediaPlayer;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import java.io.IOException;

public class MainActivity extends AppCompatActivity implements SensorEventListener{

    private SensorManager sensor_manager;
    Sensor accelerometer;
    Sensor magnetometer;
    Sensor gyroscope;

    float[] vGravity;
    float[] vGeomagnetic;
    float[] vTilt;

    TextView textView;

    boolean shoot = false;

    MediaPlayer shootMP;

    float prev_angle;
    float prev_scope;
    float prev_tilt;

    float tilt = 0;

    float compass = 0;
    float scope_angle = 0;

    boolean scope_lock = false;

    //private Handler angle_handler = new Handler();
    private Handler shoot_handler = new Handler();
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
            prev_scope = scope_angle;

            Log.i("Scope_Update", "" + prev_scope);

            shoot_handler.postDelayed(shoot_runnable, 120);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sensor_manager = (SensorManager)getSystemService(SENSOR_SERVICE);
        accelerometer = sensor_manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        magnetometer = sensor_manager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        gyroscope = sensor_manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);

        textView = (TextView) findViewById(R.id.angle);

        shootMP = MediaPlayer.create(this, R.raw.shoot);

        //angle_handler.post(angle_runnable);
        shoot_handler.post(tilt_runnable);
        shoot_handler.post(shoot_runnable);
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

                    float azimut = (float) Math.toDegrees(orientation[0]); // azimut
                    compass = 180.0f - Math.round(azimut);

                    float roll = (float) Math.toDegrees(orientation[2]);

                    float pitch = (float) Math.toDegrees(orientation[1]);
                    scope_angle = pitch;



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

                if(tilt > -0.2f && tilt < 0.2f && scope_lock) {
                    shoot_handler.post(shoot_runnable);
                    scope_lock = false;
                }

                if(tilt < -0.8f && !scope_lock) {
                    shoot_handler.removeCallbacks(shoot_runnable);
                    scope_lock = true;
                }

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

                    //Log.i("In_shoot", "Bang! " + prev_scope);

                    shoot_handler.removeCallbacks(shoot_runnable);

                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            //textView.setText("");
                            shoot_handler.post(shoot_runnable);
                            shoot = false;
                        }
                    }, 300);
                }
            }
        }
        else {
            textView.setText("Erro: Gyroscópio não encontrado.");
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }
}
