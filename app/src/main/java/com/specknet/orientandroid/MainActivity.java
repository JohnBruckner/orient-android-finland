package com.specknet.orientandroid;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.opencsv.CSVWriter;
import com.polidea.rxandroidble2.RxBleClient;
import com.polidea.rxandroidble2.RxBleDevice;
import com.polidea.rxandroidble2.scan.ScanSettings;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

import io.reactivex.disposables.Disposable;

public class MainActivity extends Activity {
    private static final String LOGTAG = "OrientAndroid";
    //    private static final String ORIENT_BLE_ADDRESS = "CB:D5:E1:DD:8F:0D"; // test device
    private static final String ORIENT_BLE_ADDRESS = "C7:BA:D7:9D:F8:2E"; // test device
    public static final String ORIENT1_BLE_ADDRESS = "67:BA:D7:9D:F8:2D";
    public static final String ORIENT2_BLE_ADDRESS = "E3:CF:82:7B:BF:77";


    private static final String ORIENT_QUAT_CHARACTERISTIC = "00001526-1212-efde-1523-785feabcd125";
    private static final String ORIENT_RAW_CHARACTERISTIC = "00001527-1212-efde-1523-785feabcd125";

    private boolean raw = false;
    private boolean multi = false;
    private Disposable scanSubscription;
    private RxBleClient rxBleClient;
    private ByteBuffer packetData;
    private boolean foundO1 = false;
    private boolean foundO2 = false;
    private boolean receivingO1 = false;
    private boolean receivingO2 = false;

    private Long capture_started_timestamp = null;
    boolean connected = false;
    private float freq = 0.f;

    private int counter = 0;
    private CSVWriter writer;
    private File path;
    private File file;
    private boolean logging = false;

    private Button connect_button;
    private Button start_button;
    private Button stop_button;
    private Context ctx;
    private TextView captureTimetextView;
    private TextView accelTextView;
    private TextView gyroTextView;
    private TextView freqTextView;


    private RadioGroup characteristicRadioGroup;
    private String characteristic_str;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ctx = this;

        path = Environment.getExternalStorageDirectory();

        connect_button = findViewById(R.id.connect_button);
        start_button = findViewById(R.id.start_button);
        stop_button = findViewById(R.id.stop_button);
        captureTimetextView = findViewById(R.id.captureTimetextView);
        accelTextView = findViewById(R.id.accelTextView);
        gyroTextView = findViewById(R.id.gyroTextView);
        freqTextView = findViewById(R.id.freqTextView);

        characteristicRadioGroup = findViewById(R.id.radioCharacteristic);


        start_button.setOnClickListener(v -> {


            start_button.setEnabled(false);


            // make a new filename based on the start timestamp
            String file_ts = new SimpleDateFormat("yyyyMMdd'T'HHmmss").format(new Date());

            String[] entries = null;
            if (raw) {
                file = new File(path, "Orient_raw_" + file_ts + ".csv");
                entries = "device#timestamp#packet_seq#sample_seq#accel_x#accel_y#accel_z#gyro_x#gyro_y#gyro_z#mag_x#mag_y#mag_z".split(
                        "#");
            } else {
                file = new File(path, "Orient_quat_" + file_ts + ".csv");
                entries = "device#timestamp#packet_seq#sample_seq#quat_w#quat_x#quat_y#quat_z".split("#");
            }


            try {
                writer = new CSVWriter(new FileWriter(file), ',', '"', '"', "\n");
            } catch (IOException e) {
                Log.e("MainActivity", "Caught IOException: " + e.getMessage());
            }
            System.out.println("Writer: " + writer.toString());

            writer.writeNext(entries);

            logging = true;
            capture_started_timestamp = System.currentTimeMillis();
            counter = 0;
            Toast.makeText(this, "Start logging", Toast.LENGTH_SHORT).show();
            stop_button.setEnabled(true);
        });

        stop_button.setOnClickListener(v -> {
            logging = false;
            stop_button.setEnabled(false);
            try {
                writer.flush();
                writer.close();
                Toast.makeText(this, "Recording saved", Toast.LENGTH_SHORT).show();
            } catch (IOException e) {
                Log.e("MainActivity", "Caught IOException: " + e.getMessage());
            }
            start_button.setEnabled(true);
        });

        connect_button.setOnClickListener(v -> {
            int selectedId;
            RadioButton rb;

            selectedId = characteristicRadioGroup.getCheckedRadioButtonId();
            rb = findViewById(selectedId);
            characteristic_str = rb.getText().toString();

            if (characteristic_str.compareTo("Raw") == 0) {
                raw = true;
            } else if (characteristic_str.compareTo("Raw Multiple Devices") == 0) {
                raw = true;
                multi = true;
            }

            scanSubscription = rxBleClient.scanBleDevices(new ScanSettings.Builder()
                            // .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY) // change if needed
                            // .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES) // change if needed
                            .build()
                    // add filters if needed
            ).subscribe(scanResult -> {
                Log.i(LOGTAG,
                        "FOUND: " + scanResult.getBleDevice().getName() + ", " + scanResult.getBleDevice().getMacAddress());
                // Process scan result here.
                if (!multi) {
                    if (scanResult.getBleDevice().getMacAddress().equals(ORIENT_BLE_ADDRESS)) {
                        runOnUiThread(() -> {
                            Toast.makeText(ctx,
                                    "Found " + scanResult.getBleDevice().getName() + ", " + scanResult.getBleDevice().getMacAddress(),
                                    Toast.LENGTH_SHORT).show();
                        });
                        connectToOrient(ORIENT_BLE_ADDRESS, 0);
                        scanSubscription.dispose();
                    }
                } else {
                    if (!foundO1) {
                        if (scanResult.getBleDevice().getMacAddress().equals(ORIENT1_BLE_ADDRESS)) {
                            runOnUiThread(() -> {
                                Toast.makeText(ctx,
                                        "Found " + scanResult.getBleDevice().getName() + ", " + scanResult.getBleDevice().getMacAddress(),
                                        Toast.LENGTH_SHORT).show();
                            });
                            foundO1 = true;
                            connectToOrient(ORIENT1_BLE_ADDRESS, 1);
                        }
                    } else if (!foundO2) {
                        if (scanResult.getBleDevice().getMacAddress().equals(ORIENT2_BLE_ADDRESS)) {
                            runOnUiThread(() -> {
                                Toast.makeText(ctx,
                                        "Found " + scanResult.getBleDevice().getName() + ", " + scanResult.getBleDevice().getMacAddress(),
                                        Toast.LENGTH_SHORT).show();
                            });
                            foundO2 = true;
                            connectToOrient(ORIENT2_BLE_ADDRESS, 2);


                        }
                    }
                    if (foundO1 && foundO2) scanSubscription.dispose();
                }
            }, throwable -> {
                // Handle an error here.
                runOnUiThread(() -> {
                    Toast.makeText(ctx, "BLE scanning error: " + throwable.getLocalizedMessage(),
                            Toast.LENGTH_SHORT).show();
                });
            });

        });

        packetData = ByteBuffer.allocate(180);
        packetData.order(ByteOrder.LITTLE_ENDIAN);

        rxBleClient = RxBleClient.create(this);
    }


    private void connectToOrient(String addr, int n) {
        RxBleDevice dev;
        dev = rxBleClient.getBleDevice(addr);
        String characteristic;
        if (raw) characteristic = ORIENT_RAW_CHARACTERISTIC;
        else characteristic = ORIENT_QUAT_CHARACTERISTIC;

        boolean ac = false;
        if (n == 2) ac = true;

        dev.establishConnection(ac).flatMap(
                rxBleConnection -> rxBleConnection.setupNotification(UUID.fromString(characteristic))).doOnNext(
                notificationObservable -> {
                    // Notification has been set up
                }).flatMap(
                notificationObservable -> notificationObservable) // <-- Notification has been set up, now observe value changes.
                .subscribe(bytes -> {
                    //n += 1;
                    // Given characteristic has been changes, here is the value.

                    //Log.i("OrientAndroid", "Received " + bytes.length + " bytes");
                    if (!connected) {
                        runOnUiThread(() -> {
                            //Toast.makeText(ctx, "Receiving sensor data from " + Integer.toString(n),
                            //        Toast.LENGTH_SHORT).show();
                            if (n == 0) {
                                connected = true;
                                start_button.setEnabled(true);
                            } else if (n == 1) receivingO1 = true;
                            else if (n == 2) receivingO2 = true;
                            if (receivingO1 && receivingO2) {
                                connected = true;
                                start_button.setEnabled(true);
                            }
                        });
                    }
                    if (multi) {
                        handleMultiRawPacket(bytes, n);
                    } else {
                        if (raw) handleMultiRawPacket(bytes, n);
                        else handleQuatPacket(bytes);
                    }
                }, throwable -> {
                    // Handle an error here.
                    Log.e(LOGTAG, "Error: " + throwable.toString());
                });
    }

    private void handleQuatPacket(final byte[] bytes) {
        long ts = System.currentTimeMillis();
        packetData.clear();
        packetData.put(bytes);
        packetData.position(0);

        int w = packetData.getInt();
        int x = packetData.getInt();
        int y = packetData.getInt();
        int z = packetData.getInt();

        double dw = w / 1073741824.0;  // 2^30
        double dx = x / 1073741824.0;
        double dy = y / 1073741824.0;
        double dz = z / 1073741824.0;

        //Log.i("OrientAndroid", "QuatInt: (w=" + w + ", x=" + x + ", y=" + y + ", z=" + z + ")");
        //Log.i("OrientAndroid", "QuatDbl: (w=" + dw + ", x=" + dx + ", y=" + dy + ", z=" + dz + ")");

        if (logging) {
            //String[] entries = "first#second#third".split("#");
            String[] entries = {Integer.toString(0), Long.toString(ts), Integer.toString(counter), Integer.toString(0),
                    Double.toString(dw), Double.toString(dx), Double.toString(dy), Double.toString(dz),};
            writer.writeNext(entries);

            if (counter % 12 == 0) {
                long elapsed_time = System.currentTimeMillis() - capture_started_timestamp;
                int total_secs = (int) elapsed_time / 1000;
                int s = total_secs % 60;
                int m = total_secs / 60;

                String m_str = Integer.toString(m);
                if (m_str.length() < 2) {
                    m_str = "0" + m_str;
                }

                String s_str = Integer.toString(s);
                if (s_str.length() < 2) {
                    s_str = "0" + s_str;
                }


                Long elapsed_capture_time = System.currentTimeMillis() - capture_started_timestamp;
                float connected_secs = elapsed_capture_time / 1000.f;
                freq = counter / connected_secs;
                //Log.i("OrientAndroid", "Packet count: " + Integer.toString(n) + ", Freq: " + Float.toString(freq));

                String time_str = m_str + ":" + s_str;

                String accel_str = "Quat: (" + dw + ", " + dx + ", " + dy + ", " + dz + ")";
                String freq_str = "Freq: " + freq;

                runOnUiThread(() -> {
                    captureTimetextView.setText(time_str);
                    accelTextView.setText(accel_str);
                    freqTextView.setText(freq_str);
                });
            }

            counter += 1;
        }
    }

    private void handleRawPacket(final byte[] bytes) {
        long ts = System.currentTimeMillis();
        packetData.clear();
        packetData.put(bytes);
        packetData.position(0);

        float accel_x = packetData.getShort() / 1024.f;  // integer part: 6 bits, fractional part 10 bits, so div by 2^10
        float accel_y = packetData.getShort() / 1024.f;
        float accel_z = packetData.getShort() / 1024.f;

        float gyro_x = packetData.getShort() / 32.f;  // integer part: 11 bits, fractional part 5 bits, so div by 2^5
        float gyro_y = packetData.getShort() / 32.f;
        float gyro_z = packetData.getShort() / 32.f;

        float mag_x = packetData.getShort() / 16.f;  // integer part: 12 bits, fractional part 4 bits, so div by 2^4
        float mag_y = packetData.getShort() / 16.f;
        float mag_z = packetData.getShort() / 16.f;

        //Log.i("OrientAndroid", "Accel:(" + accel_x + ", " + accel_y + ", " + accel_z + ")");
        //Log.i("OrientAndroid", "Gyro:(" + gyro_x + ", " + gyro_y + ", " + gyro_z + ")");
        //if (mag_x != 0f || mag_y != 0f || mag_z != 0f)
        //Log.i("OrientAndroid", "Mag:(" + mag_x + ", " + mag_y + ", " + mag_z + ")");

        if (logging) {
            //String[] entries = "first#second#third".split("#");
            String[] entries = {Integer.toString(0), Long.toString(ts), Integer.toString(counter), Integer.toString(0),
                    Float.toString(accel_x), Float.toString(accel_y), Float.toString(accel_z), Float.toString(gyro_x),
                    Float.toString(gyro_y), Float.toString(gyro_z), Float.toString(mag_x), Float.toString(mag_y),
                    Float.toString(mag_z),};
            writer.writeNext(entries);
            Log.i(LOGTAG, "Packet received");

            if (counter % 12 == 0) {
                long elapsed_time = System.currentTimeMillis() - capture_started_timestamp;
                int total_secs = (int) elapsed_time / 1000;
                int s = total_secs % 60;
                int m = total_secs / 60;

                String m_str = Integer.toString(m);
                if (m_str.length() < 2) {
                    m_str = "0" + m_str;
                }

                String s_str = Integer.toString(s);
                if (s_str.length() < 2) {
                    s_str = "0" + s_str;
                }


                Long elapsed_capture_time = System.currentTimeMillis() - capture_started_timestamp;
                float connected_secs = elapsed_capture_time / 1000.f;
                freq = counter / connected_secs;
                //Log.i("OrientAndroid", "Packet count: " + Integer.toString(n) + ", Freq: " + Float.toString(freq));

                String time_str = m_str + ":" + s_str;

                String accel_str = "Accel: (" + accel_x + ", " + accel_y + ", " + accel_z + ")";
                String gyro_str = "Gyro: (" + gyro_x + ", " + gyro_y + ", " + gyro_z + ")";
                String freq_str = "Freq: " + freq;

                runOnUiThread(() -> {
                    captureTimetextView.setText(time_str);
                    accelTextView.setText(accel_str);
                    gyroTextView.setText(gyro_str);
                    freqTextView.setText(freq_str);
                });
            }

            counter += 1;
        }
    }

    private void handleMultiRawPacket(final byte[] bytes, int n) {
        long ts = System.currentTimeMillis();
        packetData.clear();
        packetData.put(bytes);
        packetData.position(0);

        for (int i = 0; i < 10; i++) {

            float accel_x = packetData.getShort() / 1024.f;  // integer part: 6 bits, fractional part 10 bits, so div by 2^10
            float accel_y = packetData.getShort() / 1024.f;
            float accel_z = packetData.getShort() / 1024.f;

            float gyro_x = packetData.getShort() / 32.f;  // integer part: 11 bits, fractional part 5 bits, so div by 2^5
            float gyro_y = packetData.getShort() / 32.f;
            float gyro_z = packetData.getShort() / 32.f;

            float mag_x = packetData.getShort() / 16.f;  // integer part: 12 bits, fractional part 4 bits, so div by 2^4
            float mag_y = packetData.getShort() / 16.f;
            float mag_z = packetData.getShort() / 16.f;

            //Log.i("OrientAndroid", "Accel:(" + accel_x + ", " + accel_y + ", " + accel_z + ")");
            //Log.i("OrientAndroid", "Gyro:(" + gyro_x + ", " + gyro_y + ", " + gyro_z + ")");
            //if (mag_x != 0f || mag_y != 0f || mag_z != 0f)
            //Log.i("OrientAndroid", "Mag:(" + mag_x + ", " + mag_y + ", " + mag_z + ")");

            if (logging) {
                //String[] entries = "first#second#third".split("#");
                String[] entries = {Integer.toString(n), Long.toString(ts), Integer.toString(counter),
                        Integer.toString(i), Float.toString(accel_x), Float.toString(accel_y), Float.toString(accel_z),
                        Float.toString(gyro_x), Float.toString(gyro_y), Float.toString(gyro_z), Float.toString(mag_x),
                        Float.toString(mag_y), Float.toString(mag_z),};
                writer.writeNext(entries);

                if (counter % 1 == 0) {
                    long elapsed_time = System.currentTimeMillis() - capture_started_timestamp;
                    int total_secs = (int) elapsed_time / 1000;
                    int s = total_secs % 60;
                    int m = total_secs / 60;

                    String m_str = Integer.toString(m);
                    if (m_str.length() < 2) {
                        m_str = "0" + m_str;
                    }

                    String s_str = Integer.toString(s);
                    if (s_str.length() < 2) {
                        s_str = "0" + s_str;
                    }


                    Long elapsed_capture_time = System.currentTimeMillis() - capture_started_timestamp;
                    float connected_secs = elapsed_capture_time / 1000.f;
                    freq = counter / connected_secs;
                    //Log.i("OrientAndroid", "Packet count: " + Integer.toString(n) + ", Freq: " + Float.toString(freq));

                    String time_str = m_str + ":" + s_str;

                    String accel_str = "Accel: (" + accel_x + ", " + accel_y + ", " + accel_z + ")";
                    String gyro_str = "Gyro: (" + gyro_x + ", " + gyro_y + ", " + gyro_z + ")";
                    String freq_str = "Freq: " + freq;

                    runOnUiThread(() -> {
                        captureTimetextView.setText(time_str);
                        accelTextView.setText(accel_str);
                        gyroTextView.setText(gyro_str);
                        freqTextView.setText(freq_str);
                    });
                }

            }
        }
        counter += 1;
    }


}
