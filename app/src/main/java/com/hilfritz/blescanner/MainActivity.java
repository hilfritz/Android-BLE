package com.hilfritz.blescanner;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;




import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import com.hilfritz.blescanner.manager.BleManager;
import com.hilfritz.blescanner.manager.SafeDelay;
import com.hilfritz.blescanner.ui.animate.TypeWriterStatus;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    public static final String TAG = "MainActivity";
    private static final int REQUEST_ENABLE_BT = 100;
    private static final int REQUEST_PERMISSIONS = 101;

    TypeWriterStatus typeWriterStatus;
    private SafeDelay safeDelay;
    private Button btnScan;
    private TextView txtScanStatus;
    private RecyclerView recyclerView;
    private DeviceAdapter deviceAdapter;

    private BleManager bleManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bleManager = BleManager.getInstance(this);

        btnScan = findViewById(R.id.btnScan);
        txtScanStatus = findViewById(R.id.txtScanStatus);
        typeWriterStatus = new TypeWriterStatus(this, txtScanStatus);
        recyclerView = findViewById(R.id.recyclerDevices);
        safeDelay = new SafeDelay(this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        deviceAdapter = new DeviceAdapter(item -> {
            Intent intent = new Intent(MainActivity.this, DeviceDetailsActivity.class);
            intent.putExtra(DeviceDetailsActivity.EXTRA_DEVICE_NAME,
                    item.name != null ? item.name : "Unknown Device");
            intent.putExtra(DeviceDetailsActivity.EXTRA_DEVICE_ADDRESS, item.address);
            startActivity(intent);
        });
        recyclerView.setAdapter(deviceAdapter);

        btnScan.setOnClickListener(v -> {
            if (!bleManager.isBluetoothAvailable()) {
                Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_LONG).show();
                return;
            }

            if (!hasAllPermissions()) {
                requestPermissions();
                return;
            }

            if (!bleManager.isBluetoothEnabled()) {
                Intent enableBtIntent = new Intent(android.bluetooth.BluetoothAdapter.ACTION_REQUEST_ENABLE);
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    // TODO: Consider calling
                    //    ActivityCompat#requestPermissions
                    // here to request the missing permissions, and then overriding
                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                    //                                          int[] grantResults)
                    // to handle the case where the user grants the permission. See the documentation
                    // for ActivityCompat#requestPermissions for more details.
                    return;
                }
                if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                    Log.e(TAG, "startActivityForResult: ERROR: Manifest.permission.BLUETOOTH_SCAN permission not granted");
                    Log.e(TAG, "startActivityForResult: ERROR: startActivityForResult failed (or impossible to start) because permission not granted");
                    return;
                }
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
                return;
            }

            // Toggle scan
            // If scan is running, BleManager will call onScanStopped

            txtScanStatus.setVisibility(View.VISIBLE);
            typeWriterStatus.start("Scanning...");
            safeDelay.post(1500, () -> bleManager.startScan());
            //bleManager.startScan();
        });

        // Attach listener for scan results
        bleManager.setScanListener(new BleManager.ScanListener() {
            @Override
            public void onDeviceFound(String name, String address, int rssi) {
                runOnUiThread(() -> {
                    deviceAdapter.addOrUpdateDevice(name, address, rssi);
                    txtScanStatus.setVisibility(View.INVISIBLE);
                });
            }

            @Override
            public void onScanStarted() {
                runOnUiThread(() -> {

                    //btnScan.setText("Scanning...");
                    deviceAdapter.clearDevices();
                });
            }

            @Override
            public void onScanStopped() {
                runOnUiThread(() -> btnScan.setText("Start Scan"));
            }
        });

        if (!hasAllPermissions()) {
            requestPermissions();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // stop scanning when leaving the screen
        bleManager.stopScan();
    }

    private boolean hasAllPermissions() {
        List<String> needed = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.BLUETOOTH_SCAN);
            }
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.BLUETOOTH_CONNECT);
            }
        } else {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.ACCESS_FINE_LOCATION);
            }
        }

        return needed.isEmpty();
    }

    private void requestPermissions() {
        List<String> needed = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            needed.add(Manifest.permission.BLUETOOTH_SCAN);
            needed.add(Manifest.permission.BLUETOOTH_CONNECT);
        } else {
            needed.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }

        ActivityCompat.requestPermissions(this,
                needed.toArray(new String[0]),
                REQUEST_PERMISSIONS);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS) {
            boolean allGranted = true;
            for (int res : grantResults) {
                if (res != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (!allGranted) {
                Toast.makeText(this,
                        "Permissions are required for BLE scanning",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    // DeviceAdapter and DeviceItem same as before
    public static class DeviceAdapter extends RecyclerView.Adapter<DeviceAdapter.DeviceViewHolder> {
        public interface OnDeviceClickListener {
            void onDeviceClick(DeviceItem item);
        }

        private final List<DeviceItem> devices = new ArrayList<>();
        private final OnDeviceClickListener listener;

        public DeviceAdapter(OnDeviceClickListener listener) {
            this.listener = listener;
        }

        @NonNull
        @Override
        public DeviceViewHolder onCreateViewHolder(@NonNull android.view.ViewGroup parent, int viewType) {
            android.view.View view = android.view.LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_device, parent, false);
            return new DeviceViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull DeviceViewHolder holder, int position) {
            DeviceItem item = devices.get(position);
            holder.txtName.setText(item.name != null ? item.name : "Unknown Device");
            holder.txtAddress.setText(item.address);
            holder.txtRssi.setText("RSSI: " + item.rssi + " dBm");
            holder.itemView.setOnClickListener(v -> {
                if (listener != null) listener.onDeviceClick(item);
            });
        }

        @Override
        public int getItemCount() {
            return devices.size();
        }

        public void clearDevices() {
            devices.clear();
            notifyDataSetChanged();
        }

        public void addOrUpdateDevice(String name, String address, int rssi) {
            for (int i = 0; i < devices.size(); i++) {
                DeviceItem item = devices.get(i);
                if (item.address.equals(address)) {
                    item.name = name;
                    item.rssi = rssi;
                    notifyItemChanged(i);
                    return;
                }
            }
            devices.add(new DeviceItem(name, address, rssi));
            notifyItemInserted(devices.size() - 1);
        }

        static class DeviceViewHolder extends RecyclerView.ViewHolder {
            TextView txtName, txtAddress, txtRssi;
            DeviceViewHolder(@NonNull android.view.View itemView) {
                super(itemView);
                txtName = itemView.findViewById(R.id.txtName);
                txtAddress = itemView.findViewById(R.id.txtAddress);
                txtRssi = itemView.findViewById(R.id.txtRssi);
            }
        }
    }

    public static class DeviceItem {
        String name;
        String address;
        int rssi;
        DeviceItem(String name, String address, int rssi) {
            this.name = name;
            this.address = address;
            this.rssi = rssi;
        }
    }
}
