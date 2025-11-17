package com.hilfritz.blescanner.manager;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
//import android.bluetooth.BluetoothLeScanner;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

public class BleManager {

    private static final String TAG = "BleManager";
    private static final long SCAN_PERIOD = 10_000;

    private static BleManager instance;

    public static BleManager getInstance(Context context) {
        if (instance == null) {
            instance = new BleManager(context.getApplicationContext());
        }
        return instance;
    }

    // --- Core BLE fields ---
    private final Context appContext;
    private final BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private BluetoothGatt bluetoothGatt;
    private final Handler handler = new Handler();

    private boolean isScanning = false;

    // --- Listeners for UI ---
    public interface ScanListener {
        void onDeviceFound(String name, String address, int rssi);
        void onScanStarted();
        void onScanStopped();
    }

    public interface ConnectionListener {
        void onConnecting();
        void onConnected();
        void onDisconnected();
        void onServicesAvailable(List<BluetoothGattService> services);
    }

    // Small “read manager” callback interface
    public interface CharacteristicReadListener {
        void onCharacteristicRead(BluetoothGattCharacteristic characteristic, byte[] value);
        void onCharacteristicReadError(BluetoothGattCharacteristic characteristic, int status);
    }

    private ScanListener scanListener;
    private ConnectionListener connectionListener;
    private CharacteristicReadListener characteristicReadListener;

    private BleManager(Context context) {
        this.appContext = context;
        BluetoothManager bm =
                (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bm != null ? bm.getAdapter() : null;
    }

    // region Public API

    public void setScanListener(ScanListener listener) {
        this.scanListener = listener;
    }

    public void setConnectionListener(ConnectionListener listener) {
        this.connectionListener = listener;
    }

    public void setCharacteristicReadListener(CharacteristicReadListener listener) {
        this.characteristicReadListener = listener;
    }

    public boolean isBluetoothAvailable() {
        return bluetoothAdapter != null;
    }

    public boolean isBluetoothEnabled() {
        return bluetoothAdapter != null && bluetoothAdapter.isEnabled();
    }

    public void startScan() {
        if (!isBluetoothAvailable() || !isBluetoothEnabled()) {
            Log.w(TAG, "Bluetooth not available or not enabled");
            return;
        }

        if (!hasScanPermission()) {
            Log.w(TAG, "Missing scan permission");
            return;
        }

        if (isScanning) return;

        bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        if (bluetoothLeScanner == null) {
            Log.w(TAG, "BluetoothLeScanner is null");
            return;
        }

        isScanning = true;
        if (scanListener != null) scanListener.onScanStarted();

        handler.postDelayed(this::stopScan, SCAN_PERIOD);
        if (ActivityCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "startScan: ERROR: Manifest.permission.BLUETOOTH_SCAN permission not granted");
            Log.e(TAG, "startScan: ERROR: Scanning not started because permission not granted");
            return;
        }
        bluetoothLeScanner.startScan(scanCallback);
        Log.d(TAG, "Scan started");
    }

    public void stopScan() {
        if (!isScanning || bluetoothLeScanner == null) return;

        if (ActivityCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "stopScan: ERROR: Manifest.permission.BLUETOOTH_SCAN permission not granted");
            Log.e(TAG, "stopScan: ERROR: Scanning not stopped (or impossible to stop) because permission not granted");
            return;
        }
        bluetoothLeScanner.stopScan(scanCallback);
        isScanning = false;
        if (scanListener != null) scanListener.onScanStopped();
        Log.d(TAG, "Scan stopped");
    }

    public void connect(String address) {
        if (!isBluetoothAvailable() || !isBluetoothEnabled()) {
            Log.w(TAG, "Bluetooth not available or not enabled");
            return;
        }

        if (!hasConnectPermission()) {
            Log.w(TAG, "Missing connect permission");
            return;
        }

        if (connectionListener != null) {
            connectionListener.onConnecting();
        }

        BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            Log.w(TAG, "Device not found for address: " + address);
            if (connectionListener != null) {
                connectionListener.onDisconnected();
            }
            return;
        }

        if (bluetoothGatt != null) {
            if (ActivityCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "connect: ERROR: Manifest.permission.BLUETOOTH_SCAN permission not granted");
                Log.e(TAG, "connect: ERROR: connect failed (or impossible to start) because permission not granted");
                return;
            }
            bluetoothGatt.close();
            bluetoothGatt = null;
        }

        bluetoothGatt = device.connectGatt(appContext, false, gattCallback);
        Log.d(TAG, "Connecting to " + address);
    }

    public void disconnect() {
        if (bluetoothGatt != null) {
            if (ActivityCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "disconnect: ERROR: Manifest.permission.BLUETOOTH_SCAN permission not granted");
                Log.e(TAG, "disconnect: ERROR: disconnect failed (or impossible to disconnect) because permission not granted");
                return;
            }
            bluetoothGatt.disconnect();
        }
    }

    public void close() {
        if (bluetoothGatt != null) {
            if (ActivityCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "close: ERROR: Manifest.permission.BLUETOOTH_SCAN permission not granted");
                Log.e(TAG, "close: ERROR: close failed (or impossible to close) because permission not granted");
                return;
            }
            bluetoothGatt.close();
            bluetoothGatt = null;
        }
    }

    // Small manager API: request a read
    public boolean readCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (bluetoothGatt == null) {
            Log.w(TAG, "readCharacteristic: bluetoothGatt is null");
            return false;
        }
        if (!hasConnectPermission()) {
            Log.w(TAG, "readCharacteristic: missing BLUETOOTH_CONNECT permission");
            return false;
        }

        if (ActivityCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "readCharacteristic: ERROR: Manifest.permission.BLUETOOTH_SCAN permission not granted");
            Log.e(TAG, "readCharacteristic: ERROR: readCharacteristic failed (or impossible to read) because permission not granted");
            return false;
        }
        boolean started = bluetoothGatt.readCharacteristic(characteristic);
        Log.d(TAG, "readCharacteristic started=" + started +
                " uuid=" + characteristic.getUuid());
        return started;
    }

    // endregion

    // region Permissions

    private boolean hasScanPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(appContext,
                    Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(appContext,
                    Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED;
        }
    }

    private boolean hasConnectPermission() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(appContext,
                    Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        } else {
            // Pre-Android 12: no runtime permission for connect
            return true;
        }
    }

    // endregion

    // region Callbacks

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, @NonNull ScanResult result) {
            super.onScanResult(callbackType, result);

            String name = null;
            if (result.getScanRecord() != null) {
                name = result.getScanRecord().getDeviceName();
            }
            if (name == null) {
                if (ActivityCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    Log.e(TAG, "onScanResult: ERROR: Manifest.permission.BLUETOOTH_SCAN permission not granted");
                    Log.e(TAG, "onScanResult: ERROR: device name get failed (or impossible to get) because permission not granted");
                    return;
                }
                name = result.getDevice().getName();
            }

            String address = result.getDevice().getAddress();
            int rssi = result.getRssi();

            if (scanListener != null) {
                scanListener.onDeviceFound(name, address, rssi);
            }
        }
    };

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(@NonNull BluetoothGatt gatt,
                                            int status,
                                            int newState) {
            super.onConnectionStateChange(gatt, status, newState);

            if (newState == BluetoothGatt.STATE_CONNECTED) {
                Log.d(TAG, "Connected to GATT server");
                if (connectionListener != null) {
                    connectionListener.onConnected();
                }
                if (ActivityCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    Log.e(TAG, "onConnectionStateChange: ERROR: Manifest.permission.BLUETOOTH_SCAN permission not granted");
                    Log.e(TAG, "onConnectionStateChange: ERROR: onConnectionStateChange failed (or impossible to get) because permission not granted");
                    return;
                }
                gatt.discoverServices();
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                Log.d(TAG, "Disconnected from GATT server");
                if (connectionListener != null) {
                    connectionListener.onDisconnected();
                }
            }
        }

        @Override
        public void onServicesDiscovered(@NonNull BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);

            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.w(TAG, "Service discovery failed with status: " + status);
                return;
            }

            List<BluetoothGattService> services = new ArrayList<>(gatt.getServices());
            if (connectionListener != null) {
                connectionListener.onServicesAvailable(services);
            }
        }

        @Override
        public void onCharacteristicRead(@NonNull BluetoothGatt gatt,
                                         @NonNull BluetoothGattCharacteristic characteristic,
                                         int status) {
            super.onCharacteristicRead(gatt, characteristic, status);

            if (characteristicReadListener == null) return;

            if (status == BluetoothGatt.GATT_SUCCESS) {
                byte[] value = characteristic.getValue();
                characteristicReadListener.onCharacteristicRead(characteristic, value);
            } else {
                characteristicReadListener.onCharacteristicReadError(characteristic, status);
            }
        }
    };

    // endregion
}