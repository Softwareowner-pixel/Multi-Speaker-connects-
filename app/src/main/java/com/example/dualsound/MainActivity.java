package com.example.dualsound;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import java.util.*;

public class MainActivity extends Activity {
    private BluetoothAdapter adapter;
    private LinearLayout list;
    private TextView status;
    private final Set<String> seen = new HashSet<>();
    private BroadcastReceiver receiver;

    private boolean hasPermissions() {
        if (Build.VERSION.SDK_INT >= 31) {
            return checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        }
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestNeededPermissions() {
        if (Build.VERSION.SDK_INT >= 31) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT}, 10);
        } else {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 10);
        }
    }

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        adapter = BluetoothAdapter.getDefaultAdapter();
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 28, 24, 24);
        root.setBackgroundColor(0xFFF4F6FA);

        TextView title = new TextView(this);
        title.setText("DualSound");
        title.setTextSize(28);
        title.setTextColor(0xFF172033);
        title.setPadding(0, 0, 0, 8);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Nearby Bluetooth devices");
        subtitle.setTextSize(16);
        root.addView(subtitle);

        status = new TextView(this);
        status.setText("Ready to scan");
        status.setPadding(0, 14, 0, 14);
        root.addView(status);

        Button scan = new Button(this);
        scan.setText("SCAN BLUETOOTH DEVICES");
        root.addView(scan);

        ScrollView scroll = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        setContentView(root);
        scan.setOnClickListener(v -> startScan());

        if (adapter == null) status.setText("This phone does not support Bluetooth.");
        else if (!adapter.isEnabled()) status.setText("Turn on Bluetooth, then tap Scan.");
    }

    private void startScan() {
        if (adapter == null) return;
        if (!hasPermissions()) { requestNeededPermissions(); return; }
        if (!adapter.isEnabled()) {
            status.setText("Please turn on Bluetooth in Android Settings.");
            startActivity(new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS));
            return;
        }
        try {
            if (adapter.isDiscovering()) adapter.cancelDiscovery();
            seen.clear();
            list.removeAllViews();
            showPairedDevices();
            status.setText("Scanning… Make nearby devices discoverable.");
            registerScanner();
            adapter.startDiscovery();
        } catch (SecurityException e) {
            status.setText("Bluetooth permission is required. Allow it and scan again.");
        }
    }

    private void showPairedDevices() {
        try {
            for (BluetoothDevice d : adapter.getBondedDevices()) addDevice(d, "Previously paired");
        } catch (SecurityException ignored) {}
    }

    private void registerScanner() {
        if (receiver != null) {
            try { unregisterReceiver(receiver); } catch (Exception ignored) {}
        }
        receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                    BluetoothDevice d = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    if (d != null) addDevice(d, "Nearby — not necessarily paired");
                } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                    status.setText("Scan finished. Devices found: " + seen.size());
                }
            }
        };
        IntentFilter f = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        f.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, f, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(receiver, f);
    }

    private void addDevice(BluetoothDevice d, String note) {
        try {
            String address = d.getAddress();
            if (seen.contains(address)) return;
            seen.add(address);
            String name = d.getName();
            if (name == null || name.trim().isEmpty()) name = "Unknown Bluetooth device";
            final String deviceName = name;
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(22, 18, 22, 18);
            card.setBackgroundColor(0xFFFFFFFF);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
            cp.setMargins(0, 14, 0, 0);
            card.setElevation(3);
            TextView n = new TextView(this);
            n.setText(deviceName);
            n.setTextSize(18);
            n.setTextColor(0xFF172033);
            card.addView(n);
            TextView details = new TextView(this);
            details.setText(note + "\n" + address);
            details.setTextSize(12);
            details.setTextColor(0xFF667085);
            card.addView(details);
            Button select = new Button(this);
            select.setText("Open Bluetooth settings to connect");
            select.setOnClickListener(v -> startActivity(new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)));
            card.addView(select);
            list.addView(card, cp);
        } catch (SecurityException ignored) {}
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == 10) {
            boolean ok = true;
            for (int r : results) if (r != PackageManager.PERMISSION_GRANTED) ok = false;
            if (ok) startScan();
            else status.setText("Permissions denied. Allow Nearby devices permission to scan.");
        }
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        try { if (adapter != null && adapter.isDiscovering()) adapter.cancelDiscovery(); } catch (Exception ignored) {}
        if (receiver != null) try { unregisterReceiver(receiver); } catch (Exception ignored) {}
    }
}
