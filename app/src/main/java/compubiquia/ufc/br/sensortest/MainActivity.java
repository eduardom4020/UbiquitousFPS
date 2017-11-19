package compubiquia.ufc.br.sensortest;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.MediaPlayer;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
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

    float lastAngle = 0;

    TextView textView;

    boolean shoot = false;

    MediaPlayer shootMP;

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
    }

    protected void onResume() {
        super.onResume();
        sensor_manager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        sensor_manager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_NORMAL);
        sensor_manager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_NORMAL);
    }

    protected void onPause() {
        super.onPause();
        sensor_manager.unregisterListener(this);
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

                    float angle = (float) Math.toDegrees(orientation[0]); // azimut
                    angle = 180.0f - Math.round(angle);

                    float roll = (float) Math.toDegrees(orientation[2]);

                    float pitch = (float) Math.toDegrees(orientation[1]);


                    if (Math.abs(angle - lastAngle) > 3.0f && Math.abs(pitch) <= 75.0f && Math.abs(roll) > 20.0f) {
                        //textView.setText("Angulo: " + (int) angle + "º");
                        lastAngle = angle;
                    }
                }
            }

            if(vTilt != null) {
                if(vTilt[2] < -2.0f && !shoot) {
                    //textView.setText("Bang");
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
                    }, 200);
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
