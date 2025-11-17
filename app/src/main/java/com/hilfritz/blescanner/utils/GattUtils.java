package com.hilfritz.blescanner.utils;

import android.bluetooth.BluetoothGattCharacteristic;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class GattUtils {

    private GattUtils() {
        // no instance
    }

    public static String bytesToHex(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return "(empty)";
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString().trim();
    }

    public static String bytesToAsciiSafe(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return "(empty)";
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            int v = b & 0xFF;
            if (v >= 32 && v <= 126) {
                sb.append((char) v);
            } else {
                sb.append('.');
            }
        }
        return sb.toString();
    }

    public static String shortUuid(UUID uuid) {
        // For standard BLE UUIDs: 0000xxxx-0000-1000-8000-00805f9b34fb → xxxx
        String s = uuid.toString();
        if (s.length() >= 8 && s.startsWith("0000") && s.endsWith("00805f9b34fb")) {
            String shortPart = s.substring(4, 8);
            return shortPart.toUpperCase();
        }
        // Non-standard: just show last 4 chars
        if (s.length() >= 4) {
            return s.substring(s.length() - 4).toUpperCase();
        }
        return s;
    }

    public static String buildPropsText(int props) {
        List<String> out = new ArrayList<>();
        if ((props & BluetoothGattCharacteristic.PROPERTY_READ) != 0) out.add("READ");
        if ((props & BluetoothGattCharacteristic.PROPERTY_WRITE) != 0) out.add("WRITE");
        if ((props & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) out.add("WRITE_NR");
        if ((props & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) out.add("NOTIFY");
        if ((props & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) out.add("INDICATE");
        return String.join(", ", out);
    }

    /**
     * Small dictionary mapping common GATT UUIDs to human-readable names.
     * Not exhaustive – just enough to make things understandable.
     */
    public static String gattName(UUID uuid, boolean isService) {
        String shortId = shortUuid(uuid);

        if (isService) {
            switch (shortId) {
                case "1800": return "Generic Access";
                case "1801": return "Generic Attribute";
                case "180D": return "Heart Rate";
                case "180F": return "Battery Service";
                case "180A": return "Device Information";
                default: return null;
            }
        } else {
            switch (shortId) {
                case "2A00": return "Device Name";
                case "2A01": return "Appearance";
                case "2A19": return "Battery Level";
                case "2A37": return "Heart Rate Measurement";
                case "2A38": return "Body Sensor Location";
                default: return null;
            }
        }
    }
}
