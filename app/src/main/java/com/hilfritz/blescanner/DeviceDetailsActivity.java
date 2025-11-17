package com.hilfritz.blescanner;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;

import com.hilfritz.blescanner.adapters.ServiceListAdapter;
import com.hilfritz.blescanner.ui.animate.TypeWriterStatus;
import com.hilfritz.blescanner.ui.dialog.DialogManager;
import com.hilfritz.blescanner.utils.GattUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DeviceDetailsActivity extends AppCompatActivity {

    public static final String EXTRA_DEVICE_NAME = "extra_device_name";
    public static final String EXTRA_DEVICE_ADDRESS = "extra_device_address";

    private static final String TAG = "DeviceDetails";
    // Standard CCCD (Client Characteristic Configuration Descriptor) UUID for NOTIFY/INDICATE.
    private static final UUID CLIENT_CHARACTERISTIC_CONFIG_UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private TextView txtTitle;
    private TextView txtStatus;
    private TextView txtValue;
    private ListView listView;
    private TypeWriterStatus typewriterStatus;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothGatt bluetoothGatt;

    private ServiceListAdapter listAdapter;

    // Display text for each row
    private final List<String> displayItems = new ArrayList<>();
    // Same size as displayItems; null for service rows, non-null for characteristic rows
    private final List<BluetoothGattCharacteristic> characteristicItems = new ArrayList<>();

    private DialogManager dialogManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_details);

        dialogManager = new DialogManager(DeviceDetailsActivity.this);

        txtTitle = findViewById(R.id.txtDeviceTitle);
        txtStatus = findViewById(R.id.txtStatus);
        txtValue = findViewById(R.id.txtValue);
        listView = findViewById(R.id.listServices);
        typewriterStatus = new TypeWriterStatus(this, txtStatus);

        String name = getIntent().getStringExtra(EXTRA_DEVICE_NAME);
        String address = getIntent().getStringExtra(EXTRA_DEVICE_ADDRESS);

        if (address == null) {
            dialogManager.showInfoDialog("Oops", "No device address provided.");
            finish();
            return;
        }

        if (name == null || name.isEmpty()) {
            name = "Unknown Device";
        }

        txtTitle.setText(name + " (" + address + ")");
        txtStatus.setText("Connecting...");

        listAdapter = new ServiceListAdapter(this, new ArrayList<>());
        listView.setAdapter(listAdapter);

        // List item click: NOTIFY → enable notifications, else READ if possible
        listView.setOnItemClickListener((AdapterView<?> parent, android.view.View view, int position, long id) -> {
            BluetoothGattCharacteristic ch = characteristicItems.get(position);
            if (ch == null) {
                dialogManager.showInfoDialog("INFO", "This is a service header, not a characteristic.");
                return;
            }

            if (bluetoothGatt == null) {
                dialogManager.showInfoDialog("ERROR", "Not connected to device.");
                return;
            }

            int props = ch.getProperties();

            if ((props & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                enableNotifications(ch);
                return;
            }

            if ((props & BluetoothGattCharacteristic.PROPERTY_READ) != 0) {
                typewriterStatus.start("Reading characteristic...");
                boolean started = bluetoothGatt.readCharacteristic(ch);
                if (!started) {
                    dialogManager.showInfoDialog("ERROR", "readCharacteristic() failed to start.");
                }
            } else {
                dialogManager.showInfoDialog(
                        "ERROR",
                        "Characteristic has no NOTIFY or READ property. One of these is required for notifications or reads."
                );
            }
        });

        BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();

        if (bluetoothAdapter == null) {
            dialogManager.showInfoDialog("ERROR", "Bluetooth is not supported on this device.");
            finish();
            return;
        }

        if (!hasConnectPermission()) {
            dialogManager.showInfoDialog("ERROR", "Missing BLUETOOTH_CONNECT permission.");
            finish();
            return;
        }

        BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            dialogManager.showInfoDialog("ERROR", "Device not found. It may be out of range.");
            finish();
            return;
        }

        bluetoothGatt = device.connectGatt(this, false, gattCallback);
    }

    private boolean hasConnectPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(this,
                    Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {

        @Override
        public void onConnectionStateChange(@NonNull BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);

            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.w(TAG, "Connection state changed, error status: " + status);
            }

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, "Connected to GATT server");
                runOnUiThread(() -> typewriterStatus.start("Connected. Discovering services..."));
                gatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d(TAG, "Disconnected from GATT server");
                runOnUiThread(() -> typewriterStatus.start("Disconnected"));
            }
        }

        @Override
        public void onServicesDiscovered(@NonNull BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);

            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.w(TAG, "onServicesDiscovered received: " + status);
                runOnUiThread(() ->
                        dialogManager.showInfoDialog("ERROR",
                                "Failed to discover services (status " + status + ")."));
                return;
            }

            List<String> tempDisplay = new ArrayList<>();
            List<BluetoothGattCharacteristic> tempChars = new ArrayList<>();

            for (BluetoothGattService service : gatt.getServices()) {
                UUID sUuid = service.getUuid();
                String sShort = GattUtils.shortUuid(sUuid);
                String sName = GattUtils.gattName(sUuid, true);

                String serviceLine = "Service " + sShort +
                        (sName != null ? " – " + sName : "");
                tempDisplay.add(serviceLine);
                tempChars.add(null); // header row

                for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                    UUID cUuid = characteristic.getUuid();
                    String cShort = GattUtils.shortUuid(cUuid);
                    String cName = GattUtils.gattName(cUuid, false);

                    int props = characteristic.getProperties();
                    String propsText = GattUtils.buildPropsText(props);

                    StringBuilder line = new StringBuilder();
                    line.append("  Char ").append(cShort);
                    if (cName != null) {
                        line.append(" – ").append(cName);
                    }
                    if (!propsText.isEmpty()) {
                        line.append("\n    Props: ").append(propsText);
                    }

                    tempDisplay.add(line.toString());
                    tempChars.add(characteristic);
                }
            }

            runOnUiThread(() -> {
                typewriterStatus.setAutoClear(false);
                typewriterStatus.start("Services discovered.\nTap a NOTIFY char for live updates, or READ for one-time value.");
                displayItems.clear();
                characteristicItems.clear();
                displayItems.addAll(tempDisplay);
                characteristicItems.addAll(tempChars);
                listAdapter.setItems(displayItems);
            });
        }

        @Override
        public void onCharacteristicChanged(@NonNull BluetoothGatt gatt,
                                            @NonNull BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);

            byte[] value = characteristic.getValue();
            final String hex = GattUtils.bytesToHex(value);
            final String ascii = GattUtils.bytesToAsciiSafe(value);

            Log.d(TAG, "onCharacteristicChanged, UUID=" + characteristic.getUuid()
                    + ", value=" + hex);

            runOnUiThread(() -> {
                typewriterStatus.start("Notification received (live updates)");
                String str = "Last notification:\nUUID: " + characteristic.getUuid()
                        + "\nHex: " + hex
                        + "\nASCII-ish: " + ascii;
                dialogManager.showInfoDialog("Notification Received!", str);
            });
        }

        @Override
        public void onCharacteristicRead(@NonNull BluetoothGatt gatt,
                                         @NonNull BluetoothGattCharacteristic characteristic,
                                         int status) {
            super.onCharacteristicRead(gatt, characteristic, status);

            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.w(TAG, "onCharacteristicRead failed: " + status);
                runOnUiThread(() ->
                        dialogManager.showInfoDialog("ERROR",
                                "Characteristic read failed (status " + status + ")."));
                return;
            }

            byte[] value = characteristic.getValue();
            final String hex = GattUtils.bytesToHex(value);
            final String ascii = GattUtils.bytesToAsciiSafe(value);

            Log.d(TAG, "onCharacteristicRead, UUID=" + characteristic.getUuid()
                    + ", value=" + hex);

            runOnUiThread(() -> {
                String str = "Last value:\nUUID: " + characteristic.getUuid()
                        + "\nHex: " + hex
                        + "\nASCII-ish: " + ascii;
                dialogManager.showInfoDialogXml("Characteristic Read Success", str);
            });
        }
    };

    private void enableNotifications(BluetoothGattCharacteristic characteristic) {
        if (bluetoothGatt == null) return;

        boolean ok = bluetoothGatt.setCharacteristicNotification(characteristic, true);
        if (!ok) {
            dialogManager.showInfoDialog("ERROR", "setCharacteristicNotification() failed.");
            return;
        }

        BluetoothGattDescriptor descriptor =
                characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID);
        if (descriptor == null) {
            dialogManager.showInfoDialog("INFO",
                    "CCCD descriptor not found; some devices still send notifications without it.");
            typewriterStatus.start("Notifications enabled (no CCCD write)");
            return;
        }

        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        boolean wrote = bluetoothGatt.writeDescriptor(descriptor);
        if (!wrote) {
            dialogManager.showInfoDialog("ERROR", "writeDescriptor() failed to start.");
        } else {
            typewriterStatus.start("Enabling notifications...");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bluetoothGatt != null) {
            bluetoothGatt.close();
            bluetoothGatt = null;
        }
    }
}
