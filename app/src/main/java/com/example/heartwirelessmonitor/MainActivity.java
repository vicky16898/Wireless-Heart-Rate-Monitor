package com.example.heartwirelessmonitor;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

import butterknife.BindView;
import butterknife.ButterKnife;


public class MainActivity extends AppCompatActivity {

    @BindView(R.id.heart_rate)
    TextView heartRate;
    @BindView(R.id.get_data)
    Button getData;
    @BindView(R.id.clear)
    Button clear;
    BluetoothAdapter mBluetoothAdapter;
    BluetoothSocket mmSocket;
    BluetoothDevice mmDevice;
    OutputStream mmOutputStream;
    InputStream mmInputStream;
    Thread workerThread;
    byte[] readBuffer;
    int readBufferPosition;
    boolean connectionAvailability = false;
    volatile boolean stopWorker;
    boolean isButtonClicked = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(MainActivity.this);

        clear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                heartRate.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.gray));
                heartRate.setText("HEART RATE");
            }
        });

        getData.setOnClickListener(v -> {

            if (!isButtonClicked) {
                getData.setText("STOP");
                isButtonClicked = true;
                final Handler h = new Handler();
                h.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            findBT();
                            if (connectionAvailability)
                                openBT();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }, 100);
            } else {
                heartRate.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.gray));
                getData.setText("START");
                isButtonClicked = false;
                try {
                    closeBT();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

        });
    }

    void findBT() {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            Log.d("STATUS", "NO BLUETOOTH ADAPTER AVAILABLE");
            connectionAvailability = false;
            return;
        }
        connectionAvailability = true;

        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBluetooth, 0);
        }

        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        if (pairedDevices.size() > 0) {
            for (BluetoothDevice device : pairedDevices) {
                Log.d("DEVICE NAME", String.valueOf(device.getName()));
                if (device.getName().equals("HC-05")) {
                    mmDevice = device;
                    Log.d("HC-05", "CONNECTED");
                    break;

                }
            }
        }
        Log.d("STATUS", "BLUETOOTH DEVICE FOUND");
    }

    void openBT() throws IOException {
        UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); //Standard SerialPortService ID
        mmSocket = mmDevice.createRfcommSocketToServiceRecord(uuid);
        mmSocket.connect();
        mmOutputStream = mmSocket.getOutputStream();
        mmInputStream = mmSocket.getInputStream();

        beginListenForData();

        Log.d("STATUS", "BLUETOOTH DEVICE OPENED");
    }

    void beginListenForData() {
        final Handler handler = new Handler();
        final byte delimiter = 10; //This is the ASCII code for a newline character

        stopWorker = false;
        readBufferPosition = 0;
        readBuffer = new byte[1024];
        workerThread = new Thread(new Runnable() {
            public void run() {
                while (!Thread.currentThread().isInterrupted() && !stopWorker) {
                    try {
                        int bytesAvailable = mmInputStream.available();
                        Log.d("bytesSize", String.valueOf(bytesAvailable));
                        if (bytesAvailable > 0) {
                            byte[] packetBytes = new byte[bytesAvailable];
                            mmInputStream.read(packetBytes);
                            for (int i = 0; i < bytesAvailable; i++) {
                                byte b = packetBytes[i];
                                if (b == delimiter) {
                                    byte[] encodedBytes = new byte[readBufferPosition];
                                    System.arraycopy(readBuffer, 0, encodedBytes, 0, encodedBytes.length);
                                    final String data = new String(encodedBytes, "US-ASCII");
                                    readBufferPosition = 0;

                                    handler.post(new Runnable() {
                                        public void run() {
                                            int bpm = Integer.valueOf(data);
                                            if (bpm >= 60 && bpm <= 100) {
                                                heartRate.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.green));
                                            } else if (bpm <= 60 && bpm >= 0) {
                                                heartRate.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.red));
                                            } else {
                                                heartRate.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.red));
                                            }
                                            heartRate.setText(data);
                                        }
                                    });
                                } else {
                                    readBuffer[readBufferPosition++] = b;
                                }
                            }
                        }
                    } catch (IOException ex) {
                        stopWorker = true;
                    }
                }
            }
        });

        workerThread.start();
    }

    void closeBT() throws IOException {
        if (connectionAvailability) {
            stopWorker = true;
            mmOutputStream.close();
            mmInputStream.close();
            mmSocket.close();
            Log.d("STATUS", "BLUETOOTH CLOSED");
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        try {
            closeBT();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
