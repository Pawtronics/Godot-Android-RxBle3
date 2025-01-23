package org.godotengine.plugin.android.rxble3

import android.bluetooth.BluetoothDevice
import android.os.ParcelUuid
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import android.content.pm.PackageManager
import android.annotation.SuppressLint


import com.polidea.rxandroidble3.RxBleClient
import com.polidea.rxandroidble3.RxBleDevice
import com.polidea.rxandroidble3.scan.ScanFilter
import com.polidea.rxandroidble3.scan.ScanSettings
import com.polidea.rxandroidble3.scan.ScanResult
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.godotengine.godot.Godot
import org.godotengine.godot.plugin.GodotPlugin
import org.godotengine.godot.plugin.UsedByGodot
import java.util.UUID



class GodotAndroidPlugin(godot: Godot) : GodotPlugin(godot) {

    private val TAG = "RxAndroidBleGd"
    private val rxBleClient: RxBleClient = RxBleClient.create(godot.getActivity() as Context)
    /**
     * The CompositeDisposable ensures that all subscriptions are properly disposed of, preventing memory leaks. 
     * It's crucial to manage these disposables, especially when dealing with long-lived connections or subscriptions.
     */
    private val disposables: CompositeDisposable = CompositeDisposable()

    override fun getPluginName() = "RxAndroidBleGd"

    init {
        Log.v(TAG, "GodotAndroidPlugin initialized")
    }

    /**
     * Displays a toast message and logs it. MVP for debugging.
     */
    private fun debugToast(message: String) {
        runOnUiThread {
            Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
        }
        Log.v(TAG, message)
    }

    /**
     * Sends an event to Godot with the specified name and parameters.
     */
    private fun sendGodotEvent(eventName: String, vararg params: Any) {
        emitSignal(eventName, *params)
    }

    /**
     * Exposed method to start scanning for BLE devices with optional filters.
     * Events Emitted:
     * - scan_started: Emitted when scanning starts.
     * - device_found: Emitted for each device found, providing MAC address and device name.
     * - scan_error: Emitted if scanning fails.
     * @param deviceName Optional device name to filter scan results.
     * @param macAddress Optional MAC address to filter scan results.
     * @param serviceUuid Optional service UUID to filter scan results.
     */
    @UsedByGodot
    fun startScan(deviceName: String = "", macAddress: String = "", serviceUuid: String = "") {
        Log.v(TAG, "startScan() called with deviceName: '$deviceName', macAddress: '$macAddress', serviceUuid: '$serviceUuid'")
        debugToast("BLE Scan Started")
        sendGodotEvent("scan_started")

        // Build dynamic scan filters based on provided parameters
        val filters = mutableListOf<ScanFilter.Builder>()
        if (deviceName.isNotEmpty()) {
            filters.add(ScanFilter.Builder().setDeviceName(deviceName))
        }
        if (macAddress.isNotEmpty()) {
            filters.add(ScanFilter.Builder().setDeviceAddress(macAddress))
        }
        if (serviceUuid.isNotEmpty()) {
            val parcelUuid = ParcelUuid(UUID.fromString(serviceUuid))
            filters.add(ScanFilter.Builder().setServiceUuid(parcelUuid))
        }
        
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val scanFilterList = if (filters.isNotEmpty()) {
            filters.map { it.build() }
        } else {
            emptyList()
        }

        disposables.add(
            rxBleClient.scanBleDevices(scanSettings, *scanFilterList.toTypedArray())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ scanResult: ScanResult ->
                    val device = scanResult.bleDevice
                    Log.v(TAG, "Found device: ${device.macAddress} (${device.name ?: "Unknown"})")
                    sendGodotEvent("device_found", device.macAddress, device.name ?: "Unknown")
                }, { throwable ->
                    Log.e(TAG, "Scan failed: ${throwable.message}")
                    sendGodotEvent("scan_error", throwable.message ?: "Unknown error")
                })                
        )
    }

    /**
     * Exposed method to stop scanning for BLE devices.
     */
    @UsedByGodot
    fun stopScan() {
        Log.v(TAG, "stopScan() called")
        disposables.clear()
        debugToast("BLE Scan Stopped")
        sendGodotEvent("scan_stopped")
    }

    /**
     * Exposed method to connect to a BLE device by its MAC address.
     * Events Emitted:
     *  - connect_started, connected, connect_error, disconnected: Corresponding to connection lifecycle events.
     * @param macAddress The MAC address of the device to connect to.
     */
    @UsedByGodot
    fun connectToDevice(macAddress: String) {
        Log.v(TAG, "connectToDevice() called with MAC: $macAddress")
        debugToast("Connecting to $macAddress")
        sendGodotEvent("connect_started", macAddress)

        val device: RxBleDevice = rxBleClient.getBleDevice(macAddress)

        disposables.add(
            device.establishConnection(false)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ connection ->
                    Log.v(TAG, "Connected to $macAddress")
                    debugToast("Connected to $macAddress")
                    sendGodotEvent("connected", macAddress)

                    // Discover services and emit discovered services and characteristics
                    connection.discoverServices()
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe({ services ->
                            services.bluetoothGattServices.forEach { service ->
                                sendGodotEvent("service_discovered", service.uuid.toString())
                                service.characteristics.forEach { characteristic ->
                                    sendGodotEvent("characteristic_discovered", characteristic.uuid.toString())
                                }
                            }
                        }, { serviceError ->
                            Log.e(TAG, "Service discovery failed: ${serviceError.message}")
                            sendGodotEvent("service_error", serviceError.message ?: "Unknown service error")
                        })
                }, { connectError ->
                    Log.e(TAG, "Connection failed: ${connectError.message}")
                    debugToast("Connection failed: ${connectError.message}")
                    sendGodotEvent("connect_error", connectError.message ?: "Unknown connection error")
                }, {
                    Log.v(TAG, "Disconnected from $macAddress")
                    debugToast("Disconnected from $macAddress")
                    sendGodotEvent("disconnected", macAddress)
                })
        )
    }

    /**
     * Exposed method to disconnect from a BLE device by its MAC address.
     * @param macAddress The MAC address of the device to disconnect from.
     */
    @UsedByGodot
    fun disconnectDevice(macAddress: String) {
        Log.v(TAG, "disconnectDevice() called with MAC: $macAddress")
        @Suppress("UNUSED_VARIABLE")
        val device: RxBleDevice = rxBleClient.getBleDevice(macAddress)
        disposables.clear()
        debugToast("Disconnected from $macAddress")
        sendGodotEvent("disconnected", macAddress)
    }

    /**
     * Exposed method to read a characteristic from a connected BLE device.
     * @param macAddress The MAC address of the connected device.
     * @param characteristicUuid The UUID of the characteristic to read.
     */
    @UsedByGodot
    fun readCharacteristic(macAddress: String, characteristicUuid: String) {
        Log.v(TAG, "readCharacteristic() called for $macAddress, UUID: $characteristicUuid")
        sendGodotEvent("read_characteristic_started", macAddress, characteristicUuid)

        val device: RxBleDevice = rxBleClient.getBleDevice(macAddress)
        val uuid = UUID.fromString(characteristicUuid)

        disposables.add(
            device.establishConnection(false)
                .flatMapSingle { it.readCharacteristic(uuid) }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ bytes ->
                    val value = bytes.joinToString(separator = "") { String.format("%02X", it) }
                    Log.v(TAG, "Read successful: $value")
                    sendGodotEvent("read_characteristic_success", macAddress, characteristicUuid, value)
                }, { readError ->
                    Log.e(TAG, "Read failed: ${readError.message}")
                    sendGodotEvent("read_characteristic_error", macAddress, characteristicUuid, readError.message ?: "Unknown read error")
                })
        )
    }

    /**
     * Exposed method to write to a characteristic on a connected BLE device.
     * @param macAddress The MAC address of the connected device.
     * @param characteristicUuid The UUID of the characteristic to write to.
     * @param value The byte array to write, represented as a hex string (e.g., "0A1B2C").
     */
    @UsedByGodot
    fun writeCharacteristic(macAddress: String, characteristicUuid: String, value: String) {
        Log.v(TAG, "writeCharacteristic() called for $macAddress, UUID: $characteristicUuid, Value: $value")
        sendGodotEvent("write_characteristic_started", macAddress, characteristicUuid, value)

        val device: RxBleDevice = rxBleClient.getBleDevice(macAddress)
        val uuid = UUID.fromString(characteristicUuid)
        val bytesToWrite = hexStringToByteArray(value)

        disposables.add(
            device.establishConnection(false)
                .flatMapSingle { it.writeCharacteristic(uuid, bytesToWrite) }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ writtenBytes ->
                    val writtenValue = writtenBytes.joinToString(separator = "") { String.format("%02X", it) }
                    Log.v(TAG, "Write successful: $writtenValue")
                    sendGodotEvent("write_characteristic_success", macAddress, characteristicUuid, writtenValue)
                }, { writeError ->
                    Log.e(TAG, "Write failed: ${writeError.message}")
                    sendGodotEvent("write_characteristic_error", macAddress, characteristicUuid, writeError.message ?: "Unknown write error")
                })
        )
    }

    /**
     * Exposed method to subscribe to notifications for a characteristic.
     * @param macAddress The MAC address of the connected device.
     * @param characteristicUuid The UUID of the characteristic to subscribe to.
     */
    @UsedByGodot
    fun subscribeToNotifications(macAddress: String, characteristicUuid: String) {
        Log.v(TAG, "subscribeToNotifications() called for $macAddress, UUID: $characteristicUuid")
        sendGodotEvent("subscribe_notifications_started", macAddress, characteristicUuid)

        val device: RxBleDevice = rxBleClient.getBleDevice(macAddress)
        val uuid = UUID.fromString(characteristicUuid)

        disposables.add(
            device.establishConnection(false)
            .flatMap { connection -> connection.setupNotification(uuid) }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ notificationObservable: Observable<ByteArray> ->
                    disposables.add(
                        notificationObservable
                            .subscribe({ bytes: ByteArray ->
                                val notificationValue = bytes.joinToString(separator = "") { String.format("%02X", it) }
                                Log.v(TAG, "Notification received: $notificationValue")
                                sendGodotEvent("notification_received", macAddress, characteristicUuid, notificationValue)
                            }, { notificationError: Throwable ->
                                Log.e(TAG, "Notification error: ${notificationError.message}")
                                sendGodotEvent("notification_error", macAddress, characteristicUuid, notificationError.message ?: "Unknown error")
                        })
                    )
                }, { setupError: Throwable ->
                    Log.e(TAG, "Setup notification failed: ${setupError.message}")
                    sendGodotEvent("subscribe_notifications_error", macAddress, characteristicUuid, setupError.message ?: "Unknown error")
                })       
        )
    }

    /**
     * Exposed method to unsubscribe from notifications for a characteristic.
     * @param macAddress The MAC address of the connected device.
     * @param characteristicUuid The UUID of the characteristic to unsubscribe from.
     */
    @UsedByGodot
    fun unsubscribeFromNotifications(macAddress: String, characteristicUuid: String) {
        Log.v(TAG, "unsubscribeFromNotifications() called for $macAddress, UUID: $characteristicUuid")
        val device: RxBleDevice = rxBleClient.getBleDevice(macAddress)
        @Suppress("UNUSED_VARIABLE")
        val uuid = UUID.fromString(characteristicUuid)

        // needed later, add logging to use the variable
        Log.d(TAG, "Device obtained: ${device.macAddress}")

        // RxAndroidBle manages notification observables internally, disposing them will unsubscribe
        disposables.clear()
        debugToast("Unsubscribed from notifications for $characteristicUuid")
        sendGodotEvent("unsubscribe_notifications", macAddress, characteristicUuid)
    }

    /**
     * Exposed method to observe connection state changes for a specific device.
     * Events Emitted:
     *   - connection_state_changed: Emitted with the MAC address and the new connection state (e.g., CONNECTED, DISCONNECTED).
     *   - connection_state_error: Emitted if there's an error while observing connection states.
     * @param macAddress The MAC address of the device to monitor.
     */
    @UsedByGodot
    fun observeConnectionState(macAddress: String) {
        Log.v(TAG, "observeConnectionState() called for MAC: $macAddress")
        val device: RxBleDevice = rxBleClient.getBleDevice(macAddress)

        disposables.add(
            device.observeConnectionStateChanges()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ connectionState ->
                    Log.v(TAG, "Connection state for $macAddress: $connectionState")
                    sendGodotEvent("connection_state_changed", macAddress, connectionState.name)
                }, { throwable ->
                    Log.e(TAG, "Connection state observation failed: ${throwable.message}")
                    sendGodotEvent("connection_state_error", macAddress, throwable.message ?: "Unknown connection state error")
                })
        )
    }

    /**
     * Exposed method to initiate bonding (pairing) with a BLE device.
     * - pairing_started: Emitted when pairing starts.
     * - pairing_initiated: Emitted when pairing is successfully initiated.
     * - pairing_failed: Emitted if pairing initiation fails.
     * - pairing_error: Emitted if there's an error during pairing observation.
     * @param macAddress The MAC address of the device to pair with.
     */
    @SuppressLint("MissingPermission")
    @UsedByGodot
    fun pairDevice(macAddress: String) {
        Log.v(TAG, "pairDevice() called with MAC: $macAddress")
        sendGodotEvent("pairing_started", macAddress)
    
        val device: RxBleDevice = rxBleClient.getBleDevice(macAddress)
        val bluetoothDevice: BluetoothDevice = device.bluetoothDevice
    
        if (ActivityCompat.checkSelfPermission(activity!!, android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            disposables.add(
                rxBleClient.observeStateChanges()
                    .filter { it == RxBleClient.State.READY }
                    .firstOrError()
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({
                        val success = bluetoothDevice.createBond()
                        if (success) {
                            Log.v(TAG, "Pairing initiated with $macAddress")
                            sendGodotEvent("pairing_initiated", macAddress)
                        } else {
                            Log.e(TAG, "Pairing initiation failed with $macAddress")
                            sendGodotEvent("pairing_failed", macAddress, "Failed to initiate pairing")
                        }
                    }, { throwable ->
                        Log.e(TAG, "Pairing observation failed: ${throwable.message}")
                        sendGodotEvent("pairing_error", macAddress, throwable.message ?: "Unknown pairing error")
                    })
            )
        } else {
            Log.e(TAG, "BLUETOOTH_CONNECT permission not granted")
            sendGodotEvent("pairing_error", macAddress, "Missing BLUETOOTH_CONNECT permission")
        }
    }
    
    /**
     * Exposed method to request MTU size for a connected BLE device.
     * Events Emitted:
     * - request_mtu_started: Emitted when MTU request starts.
     * - request_mtu_success: Emitted when MTU request is successful, providing the granted MTU size.
     * - request_mtu_error: Emitted if the MTU request fails.
     * @param macAddress The MAC address of the connected device.
     * @param mtu The desired MTU size.
     */
    @UsedByGodot
    fun requestMtu(macAddress: String, mtu: Int) {
        Log.v(TAG, "requestMtu() called for $macAddress, MTU: $mtu")
        sendGodotEvent("request_mtu_started", macAddress, mtu)

        val device: RxBleDevice = rxBleClient.getBleDevice(macAddress)

        disposables.add(
            device.establishConnection(false)
                .flatMapSingle { it.requestMtu(mtu) }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ grantedMtu ->
                    Log.v(TAG, "MTU request successful: $grantedMtu")
                    sendGodotEvent("request_mtu_success", macAddress, grantedMtu)
                }, { mtuError ->
                    Log.e(TAG, "MTU request failed: ${mtuError.message}")
                    sendGodotEvent("request_mtu_error", macAddress, mtuError.message ?: "Unknown MTU error")
                })
        )
    }

    /**
     * Exposed method to read RSSI from a connected BLE device.
     * Events Emitted:
     * - read_rssi_started: Emitted when RSSI reading starts.
     * - read_rssi_success: Emitted upon successful RSSI reading, providing the RSSI value.
     * - read_rssi_error: Emitted if RSSI reading fails.
     * @param macAddress The MAC address of the connected device.
     */
    @UsedByGodot
    fun readRssi(macAddress: String) {
        Log.v(TAG, "readRssi() called for $macAddress")
        sendGodotEvent("read_rssi_started", macAddress)

        val device: RxBleDevice = rxBleClient.getBleDevice(macAddress)

        disposables.add(
            device.establishConnection(false)
                .flatMapSingle { it.readRssi() }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ rssi ->
                    Log.v(TAG, "RSSI for $macAddress: $rssi")
                    sendGodotEvent("read_rssi_success", macAddress, rssi)
                }, { rssiError ->
                    Log.e(TAG, "Read RSSI failed: ${rssiError.message}")
                    sendGodotEvent("read_rssi_error", macAddress, rssiError.message ?: "Unknown RSSI error")
                })
        )
    }

    /**
     * Exposed method to perform a custom GATT operation.
     * Note: This is a generic placeholder. Implement specific operations as needed.
     * @param macAddress The MAC address of the connected device.
     * @param operation The name of the operation (e.g., "read", "write").
     * @param characteristicUuid The UUID of the characteristic.
     * @param value Optional value for write operations, represented as a hex string.
     */
    @UsedByGodot
    fun performCustomGattOperation(macAddress: String, operation: String, characteristicUuid: String, value: String = "") {
        Log.v(TAG, "performCustomGattOperation() called for $macAddress, Operation: $operation, UUID: $characteristicUuid, Value: $value")
        sendGodotEvent("custom_gatt_operation_started", macAddress, operation, characteristicUuid, value)

        @Suppress("UNUSED_VARIABLE")
        val device: RxBleDevice = rxBleClient.getBleDevice(macAddress)
        @Suppress("UNUSED_VARIABLE")
        val uuid = UUID.fromString(characteristicUuid)

        when (operation.lowercase()) {
            "read" -> {
                readCharacteristic(macAddress, characteristicUuid)
            }
            "write" -> {
                writeCharacteristic(macAddress, characteristicUuid, value)
            }
            else -> {
                Log.e(TAG, "Unsupported GATT operation: $operation")
                sendGodotEvent("custom_gatt_operation_error", macAddress, operation, characteristicUuid, "Unsupported operation")
            }
        }
    }

    /**
     * Utility method to convert a hex string to a byte array.
     */
    private fun hexStringToByteArray(hex: String): ByteArray {
        val cleanHex = hex.replace("\\s".toRegex(), "")
        val len = cleanHex.length
        require(len % 2 == 0) { "Hex string must have an even length" }
        return ByteArray(len / 2) {
            cleanHex.substring(it * 2, it * 2 + 2).toInt(16).toByte()
        }
    }

    /**
     * Cleans up all disposables when the plugin is destroyed.
     */
    override fun onMainDestroy() {
        disposables.clear()
        super.onMainDestroy()
    }

    /**
     * Helper method to run actions on the UI thread.
     */
    private fun runOnUiThread(action: () -> Unit) {
        // activity.runOnUiThread(action)
        activity?.let {
            it.runOnUiThread(action)
        } ?: Log.e(TAG, "Activity is null")
        
    }
}
