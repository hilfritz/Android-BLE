# Android BLE Scanner & GATT Client Sample project
* A simple and light implementation of an Android application for Bluetooth Low Energy (BLE) scanning, connecting, and characteristic read/notify operations.*

## Overview
This project is a **Bluetooth Low Energy (BLE) Scanner and GATT Client** built in **Java**, designed showcase basic BLE fundamentals such as scanning, connecting, reading, and receiving notifications.

## Features
- Real-time BLE scanning with RSSI
- GATT connection + service/characteristic discovery
- Automatic NOTIFY enablement with CCCD handling
- Characteristic READ support
- Clean modular structure with helper classes

## Project Structure
```
/app
 └── src/main/java/com/hilfritz/blescanner
      ├── MainActivity.java
      ├── DeviceDetailsActivity.java
      ├── manager/BleManager.java
      ├── adapters/ServiceListAdapter.java
      ├── ui/
      │    ├── animate/TypeWriterStatus.java
      │    └── dialog/DialogManager.java
```

## Tech Stack
| Component | Technology |
|----------|------------|
| Language | Java |
| SDK | Android 6.0+ |
| BLE API | BluetoothAdapter / BluetoothGatt |
| UI | RecyclerView, ListView, Dialogs |

## Getting Started
### Clone the Repository
```bash
git clone https://github.com/hilfritz/Android-BLE.git
cd Android-BLE
```

## Usage Guide
1. Tap **Start Scan**
2. Select a BLE device to connect
3. Discover services/characteristics
4. Tap a characteristic to:
   - Enable notifications (if NOTIFY supported)
   - Read value (if READ supported)
5. Real-time updates appear via dialog

## Testing with nRF Connect by Nordic Semiconductors - an external app available in PlayStore
1. Create Service UUID **0xAA15**
2. Create Characteristic UUID **0xAA16** with **NOTIFY**
3. Advertise service from OnePlus
4. Connect with this app
5. Send notifications from nRF

## Screenshots

| Scan Screen | Device List | Services View |
|-------------|-------------|---------------|
| <img src="docs/screenshots/scan.png" width="260"/> | <img src="docs/screenshots/devices.png" width="260"/> | <img src="docs/screenshots/services.png" width="260"/> |

| Characteristic Read | Notification Received |
|---------------------|-----------------------|
| <img src="docs/screenshots/read.png" width="260"/> | <img src="docs/screenshots/notify.png" width="260"/> |

## BLE Manager Class
```java
bleManager.startScan();
device.connectGatt(context, false, gattCallback);
gatt.setCharacteristicNotification(ch, true);
gatt.readCharacteristic(ch);
```

## Contributing
PRs and issues are welcome.

## 📄 License
MIT License.
