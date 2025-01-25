package org.godotengine.plugin.android.rxble3

/*
 ⚠️ STOP: Do NOT place pawtronics logic here or BleManager.gd
    this converts the RxAndroidBle library for BleManager.gd bindings
    BleManager.gd

    Pawtronics goes main.gd

    Kotlin Native binding - https://godot-kotlin.readthedocs.io/en/latest/
    Godot Kotlin - https://godot-kotl.in/en/stable/  

*/

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
import org.godotengine.godot.plugin.SignalInfo

import java.util.UUID

class GodotAndroidPlugin(godot: Godot) : GodotPlugin(godot) {

    private val TAG = "RxAndroidBleGd"
    private val rxBleClient: RxBleClient = RxBleClient.create(godot.getActivity() as Context)

    // Existing disposable for general subscriptions (e.g., scanning)
    private val disposables: CompositeDisposable = CompositeDisposable()

    /**
     * Map to hold CompositeDisposable for each device using MAC address as the key.
     * This allows managing multiple device connections independently.
     */
    private val deviceDisposablesMap: MutableMap<String, CompositeDisposable> = mutableMapOf()
    private var isScanning = false

    override fun getPluginName() = "RxAndroidBleGd"

    override fun getPluginMethods(): List<String> {
        Log.v(TAG, "getPluginMethods()")
        return listOf(
            "startScan", "stopScan", "connectToDevice", "disconnectDevice",
            "readCharacteristic", "writeCharacteristic", "subscribeToNotifications", "unsubscribeFromNotifications",
            "pairDevice", "requestMtu", "readRssi", "performCustomGattOperation"
        )
    }

    override fun getPluginSignals(): MutableSet<SignalInfo> {
        Log.v(TAG, "getPluginSignals()")
        // val signals = mutableSetOf<SignalInfo>()
        // signals.add(SignalInfo("ble_scan_started"))
        // signals.add(SignalInfo("ble_scan_stopped"))
        // signals.add(SignalInfo("ble_device_found", String::class.java, String::class.java))
        // signals.add(SignalInfo("connect_started", String::class.java))
        // signals.add(SignalInfo("connected", String::class.java))
        // signals.add(SignalInfo("connect_error", String::class.java, String::class.java))
        // signals.add(SignalInfo("disconnected", String::class.java))        
        // return signals

        return mutableSetOf(
            SignalInfo("ble_scan_started"),
            SignalInfo("ble_scan_stopped"),
            SignalInfo("ble_device_found", String::class.java, String::class.java),
            // TESTING:
            SignalInfo("ble_pairing_init", String::class.java),
            SignalInfo("ble_pairing_error", String::class.java, String::class.java),
            SignalInfo("ble_connected", String::class.java),
            SignalInfo("ble_characteristic_discovered", String::class.java),
            // 
            SignalInfo("ble_read_characteristic_success", String::class.java, String::class.java, String::class.java),
            SignalInfo("ble_read_characteristic_error", String::class.java),
            // FUTURE: 
            // SignalInfo("ble_scan_error", String::class.java),
            // SignalInfo("ble_connect_error", String::class.java, String::class.java),
            // SignalInfo("ble_disconnected", String::class.java),
            // SignalInfo("ble_notification_received", String::class.java, String::class.java, String::class.java),
            // SignalInfo("ble_notification_error", String::class.java, String::class.java, String::class.java),
            // SignalInfo("ble_request_mtu_success", String::class.java, Int::class.java),
            // SignalInfo("ble_request_mtu_error", String::class.java, String::class.java),
            // SignalInfo("ble_connection_state_changed", String::class.java, String::class.java),
            // SignalInfo("ble_connection_state_error", String::class.java, String::class.java)
        )
    }


    init {
        Log.v(TAG, "GodotAndroidPlugin initialized")
    }

    /**
     * Displays a toast message and logs it.
     */
    private fun debugToast(message: String) {
        runOnUiThread {
            activity?.let {
                Toast.makeText(it, message, Toast.LENGTH_SHORT).show()
            } ?: Log.e(TAG, "Activity is null, cannot show toast: $message")
        }
        Log.v(TAG, message)
    }

    @UsedByGodot
    fun getDiagnostics(): String {
        val osVersion = android.os.Build.VERSION.RELEASE
        val deviceModel = android.os.Build.MODEL
        val deviceManufacturer = android.os.Build.MANUFACTURER
        val bleState = rxBleClient.state.toString()

        return """
            |Plugin: $pluginName
            |OS Version: $osVersion
            |Device Model: $deviceModel
            |Device Manufacturer: $deviceManufacturer
            |BLE State: $bleState
        """.trimMargin()
    }



    @UsedByGodot
    fun showDebugToast(message: String) {
        debugToast(message)
    }


    /**
     * Retrieves the CompositeDisposable for a given device, creating one if it doesn't exist.
     */
    private fun getDeviceDisposables(macAddress: String): CompositeDisposable {
        return deviceDisposablesMap.getOrPut(macAddress) { CompositeDisposable() }
    }    


    private fun hasBlePermissions(): Boolean {
        val permissions = listOf(
            android.Manifest.permission.BLUETOOTH_SCAN,
            android.Manifest.permission.BLUETOOTH_CONNECT,
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        )
        return permissions.all {
            ActivityCompat.checkSelfPermission(activity!!, it) == PackageManager.PERMISSION_GRANTED
        }
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

        if (rxBleClient.state != RxBleClient.State.READY) {
            Log.e(TAG, "BLE Client is not ready")
            sendGodotEvent("ble_scan_error", "BLE Client is not ready")
            return
        }

        if (!hasBlePermissions()) {
            sendGodotEvent("ble_scan_error", "Missing BLE permissions")
            return
        }

        if (isScanning) {
            Log.w(TAG, "Scan already in progress")
            return
        }
        isScanning = true

        // debugToast("BLE Scan Started")
        sendGodotEvent("ble_scan_started")
        // Log.v(TAG, "startScan() finished")

        // Build dynamic scan filters based on provided parameters
        val filters = mutableListOf<ScanFilter.Builder>()
        if (deviceName.isNotEmpty()) {
            filters.add(ScanFilter.Builder().setDeviceName(deviceName))
        }
        // Log.v(TAG, "startScan() ..")
        if (macAddress.isNotEmpty()) {
            filters.add(ScanFilter.Builder().setDeviceAddress(macAddress))
        }

        // Log.v(TAG, "startScan() ...")
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

        // Log.v(TAG, "startScan() ....")
        getDeviceDisposables(macAddress).add(
            rxBleClient.scanBleDevices(scanSettings, *scanFilterList.toTypedArray())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ scanResult: ScanResult ->
                    val device = scanResult.bleDevice
                    Log.v(TAG, "Found device: ${device.macAddress} (${device.name ?: "Unknown"})")
                    sendGodotEvent("ble_device_found", device.macAddress, device.name ?: "Unknown")

                    // I think this also works:
                    // emitSignal("ble_device_found", device.macAddress, device.name ?: "Unknown")
                }, { throwable ->
                    Log.e(TAG, "Scan failed: ${throwable.message}")
                    sendGodotEvent("ble_scan_error", throwable.message ?: "Unknown error")
                })
        )
        Log.v(TAG, "startScan() done")
    }


    
    /**
     * Exposed method to stop scanning for BLE devices.
     * Clears all scan-related disposables.
     */
    @UsedByGodot
    fun stopScan() {
        Log.v(TAG, "stopScan() called")
        if (!isScanning) return
        isScanning = false
        // Only dispose scan-related subscriptions
        disposables.clear()
        // debugToast("BLE Scan Stopped")
        sendGodotEvent("ble_scan_stopped")

        getDeviceDisposables("").dispose()
        deviceDisposablesMap.clear()
        Log.v(TAG, "BLE Scan Stopped")
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

        // debugToast("Connecting to $macAddress")
        // sendGodotEvent("ble_event", macAddress, "connectToDevice()")

        val device: RxBleDevice = rxBleClient.getBleDevice(macAddress)
        val deviceDisposables = getDeviceDisposables(macAddress)

        deviceDisposables.add(
            device.establishConnection(false)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ connection ->
                    Log.v(TAG, "Connected to $macAddress")
                    debugToast("Connected to $macAddress")
                    sendGodotEvent("ble_connected", macAddress)

                    // Discover services and emit discovered services and characteristics
                    connection.discoverServices()
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe({ services ->
                            services.bluetoothGattServices.forEach { service ->
                                sendGodotEvent("ble_service_discovered", service.uuid.toString())
                                service.characteristics.forEach { characteristic ->
                                    sendGodotEvent("ble_characteristic_discovered", characteristic.uuid.toString())
                                }
                            }
                        }, { serviceError ->
                            Log.e(TAG, "Service discovery failed: ${serviceError.message}")
                            sendGodotEvent("ble_service_error", serviceError.message ?: "Unknown service error")
                        })
                }, { connectError ->
                    Log.e(TAG, "Connection failed: ${connectError.message}")
                    debugToast("Connection failed: ${connectError.message}")
                    sendGodotEvent("ble_connect_error", connectError.message ?: "Unknown connection error")
                }, {
                    Log.v(TAG, "Disconnected from $macAddress")
                    debugToast("Disconnected from $macAddress")
                    sendGodotEvent("ble_disconnected", macAddress)
                })
        )
    }

    /**
     * Exposed method to disconnect from a BLE device by its MAC address.
     * Events Emitted:
     *  - disconnected: Emitted when disconnection is successful.
     * @param macAddress The MAC address of the device to disconnect from.
     */
    @UsedByGodot
    fun disconnectDevice(macAddress: String) {
        Log.v(TAG, "disconnectDevice() called with MAC: $macAddress")
        // val device: RxBleDevice = rxBleClient.getBleDevice(macAddress)
        val deviceDisposables = deviceDisposablesMap[macAddress]

        deviceDisposables?.let {
            it.dispose()
            deviceDisposablesMap.remove(macAddress)
            debugToast("Disconnected from $macAddress")
            sendGodotEvent("ble_disconnected", macAddress)
        } ?: run {
            Log.e(TAG, "No active connection found for $macAddress")
            sendGodotEvent("ble_disconnect_error", macAddress, "No active connection found")
        }
    }

    /**
     * Exposed method to read a characteristic from a connected BLE device.
     * Events Emitted:
     * - read_characteristic_started, read_characteristic_success, read_characteristic_error
     * @param macAddress The MAC address of the connected device.
     * @param characteristicUuid The UUID of the characteristic to read.
     */
    @UsedByGodot
    fun readCharacteristic(macAddress: String, characteristicUuid: String) {
        Log.v(TAG, "readCharacteristic() called for $macAddress, UUID: $characteristicUuid")
        // sendGodotEvent("ble_read_characteristic_started", macAddress, characteristicUuid)


        val device: RxBleDevice = rxBleClient.getBleDevice(macAddress)
        val uuid = UUID.fromString(characteristicUuid)
        val deviceDisposables = getDeviceDisposables(macAddress)

        deviceDisposables.add(
            device.establishConnection(false)
                .flatMapSingle { connection -> connection.readCharacteristic(uuid) }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ bytes ->
                    val value = bytes.joinToString(separator = "") { String.format("%02X", it) }
                    Log.v(TAG, "Read successful: $value")
                    sendGodotEvent("ble_read_characteristic_success", macAddress, characteristicUuid, value)
                }, { readError ->
                    Log.e(TAG, "Read failed: ${readError.message}")
                    sendGodotEvent("ble_read_characteristic_error", macAddress, characteristicUuid, readError.message ?: "Unknown read error")
                })
        )
    }

    /**
     * Exposed method to write to a characteristic on a connected BLE device.
     * Events Emitted:
     * - write_characteristic_started, write_characteristic_success, write_characteristic_error
     * @param macAddress The MAC address of the connected device.
     * @param characteristicUuid The UUID of the characteristic to write to.
     * @param value The byte array to write, represented as a hex string (e.g., "0A1B2C").
     */
    @UsedByGodot
    fun writeCharacteristic(macAddress: String, characteristicUuid: String, value: String) {
        Log.v(TAG, "writeCharacteristic() called for $macAddress, UUID: $characteristicUuid, Value: $value")
        sendGodotEvent("ble_write_characteristic_started", macAddress, characteristicUuid, value)

        val device: RxBleDevice = rxBleClient.getBleDevice(macAddress)
        val uuid = UUID.fromString(characteristicUuid)
        val bytesToWrite = hexStringToByteArray(value)
        val deviceDisposables = getDeviceDisposables(macAddress)

        deviceDisposables.add(
            device.establishConnection(false)
                .flatMapSingle { connection -> connection.writeCharacteristic(uuid, bytesToWrite) }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ writtenBytes ->
                    val writtenValue = writtenBytes.joinToString(separator = "") { String.format("%02X", it) }
                    Log.v(TAG, "Write successful: $writtenValue")
                    sendGodotEvent("ble_write_characteristic_success", macAddress, characteristicUuid, writtenValue)
                }, { writeError ->
                    Log.e(TAG, "Write failed: ${writeError.message}")
                    sendGodotEvent("ble_write_characteristic_error", macAddress, characteristicUuid, writeError.message ?: "Unknown write error")
                })
        )
    }

    /**
     * Exposed method to subscribe to notifications for a characteristic.
     * Events Emitted:
     * - subscribe_notifications_started, subscribe_notifications_success, subscribe_notifications_error, notification_received, notification_error
     * @param macAddress The MAC address of the connected device.
     * @param characteristicUuid The UUID of the characteristic to subscribe to.
     */ 
    @UsedByGodot
    fun subscribeToNotifications(macAddress: String, characteristicUuid: String) {
        Log.v(TAG, "subscribeToNotifications() called for $macAddress, UUID: $characteristicUuid")
        sendGodotEvent("ble_subscribe_notifications_started", macAddress, characteristicUuid)

        val device: RxBleDevice = rxBleClient.getBleDevice(macAddress)
        val uuid = UUID.fromString(characteristicUuid)
        val deviceDisposables = getDeviceDisposables(macAddress)

        deviceDisposables.add(
            device.establishConnection(false)
                .flatMap { connection -> connection.setupNotification(uuid) }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ notificationObservable: Observable<ByteArray> ->
                    val notificationDisposable = notificationObservable
                        .subscribe({ bytes: ByteArray ->
                            val notificationValue = bytes.joinToString(separator = "") { String.format("%02X", it) }
                            Log.v(TAG, "Notification received: $notificationValue")
                            sendGodotEvent("ble_notification_received", macAddress, characteristicUuid, notificationValue)
                        }, { notificationError: Throwable ->
                            Log.e(TAG, "Notification error: ${notificationError.message}")
                            sendGodotEvent("ble_notification_error", macAddress, characteristicUuid, notificationError.message ?: "Unknown error")
                        })
                    deviceDisposables.add(notificationDisposable)
                    sendGodotEvent("ble_subscribe_notifications_success", macAddress, characteristicUuid)
                }, { setupError: Throwable ->
                    Log.e(TAG, "Setup notification failed: ${setupError.message}")
                    sendGodotEvent("ble_subscribe_notifications_error", macAddress, characteristicUuid, setupError.message ?: "Unknown error")
                })
        )
    }

    /**
     * Exposed method to unsubscribe from notifications for a characteristic.
     * Events Emitted:
     * - unsubscribe_notifications: Emitted when unsubscribing is successful.
     * @param macAddress The MAC address of the connected device.
     * @param characteristicUuid The UUID of the characteristic to unsubscribe from.
     */
    @UsedByGodot
    fun unsubscribeFromNotifications(macAddress: String, characteristicUuid: String) {
        Log.v(TAG, "unsubscribeFromNotifications() called for $macAddress, UUID: $characteristicUuid")
        // val device: RxBleDevice = rxBleClient.getBleDevice(macAddress)
        val deviceDisposables = deviceDisposablesMap[macAddress]

        deviceDisposables?.let {
            it.dispose()
            deviceDisposablesMap.remove(macAddress)
            debugToast("Unsubscribed from notifications for $characteristicUuid")
            sendGodotEvent("ble_unsubscribe_notifications", macAddress, characteristicUuid)
        } ?: run {
            Log.e(TAG, "No active subscription found for $macAddress on $characteristicUuid")
            sendGodotEvent("ble_unsubscribe_notifications_error", macAddress, characteristicUuid, "No active subscription found")
        }
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
        val deviceDisposables = getDeviceDisposables(macAddress)

        deviceDisposables.add(
            device.observeConnectionStateChanges()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ connectionState ->
                    Log.v(TAG, "Connection state for $macAddress: $connectionState")
                    sendGodotEvent("ble_connection_state_changed", macAddress, connectionState.name)
                }, { throwable ->
                    Log.e(TAG, "Connection state observation failed: ${throwable.message}")
                    sendGodotEvent("ble_connection_state_error", macAddress, throwable.message ?: "Unknown connection state error")
                })
        )
    }

    /**
     * Exposed method to initiate bonding (pairing) with a BLE device.
     * Events Emitted:
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

        val device: RxBleDevice = rxBleClient.getBleDevice(macAddress)
        val bluetoothDevice: BluetoothDevice = device.bluetoothDevice
        // val deviceDisposables = getDeviceDisposables(macAddress)

        // Check for necessary permissions
        if (ActivityCompat.checkSelfPermission(activity!!, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "BLUETOOTH_CONNECT permission not granted")
            sendGodotEvent("ble_pairing_error", macAddress, "Missing BLUETOOTH_CONNECT permission")
            return
        }

        
        sendGodotEvent("ble_pairing_init", macAddress)

        getDeviceDisposables(macAddress).add(
            rxBleClient.observeStateChanges()
                .filter { it == RxBleClient.State.READY }
                .firstOrError()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    val success = bluetoothDevice.createBond()
                    if (success) {
                        Log.v(TAG, "Pairing initiated with $macAddress")
                        sendGodotEvent("ble_pairing_success", macAddress)
                    } else {
                        Log.e(TAG, "Pairing initiation failed with $macAddress")
                        sendGodotEvent("ble_pairing_error", macAddress, "Failed to initiate pairing")
                    }
                }, { throwable ->
                    Log.e(TAG, "Pairing observation failed: ${throwable.message}")
                    sendGodotEvent("ble_pairing_error", macAddress, throwable.message ?: "Unknown pairing error")
                })
        )
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
        sendGodotEvent("ble_request_mtu_started", macAddress, mtu)

        val device: RxBleDevice = rxBleClient.getBleDevice(macAddress)
        val deviceDisposables = getDeviceDisposables(macAddress)

        deviceDisposables.add(
            device.establishConnection(false)
                .flatMapSingle { connection -> connection.requestMtu(mtu) }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ grantedMtu ->
                    Log.v(TAG, "MTU request successful: $grantedMtu")
                    sendGodotEvent("ble_request_mtu_success", macAddress, grantedMtu)
                }, { mtuError ->
                    Log.e(TAG, "MTU request failed: ${mtuError.message}")
                    sendGodotEvent("ble_request_mtu_error", macAddress, mtuError.message ?: "Unknown MTU error")
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
        sendGodotEvent("ble_read_rssi_started", macAddress)

        val device: RxBleDevice = rxBleClient.getBleDevice(macAddress)

        getDeviceDisposables(macAddress).add(
            device.establishConnection(false)
                .flatMapSingle { connection -> connection.readRssi() }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ rssi ->
                    Log.v(TAG, "RSSI for $macAddress: $rssi")
                    sendGodotEvent("ble_read_rssi_success", macAddress, rssi)
                }, { rssiError ->
                    Log.e(TAG, "Read RSSI failed: ${rssiError.message}")
                    sendGodotEvent("ble_read_rssi_error", macAddress, rssiError.message ?: "Unknown RSSI error")
                })
        )
    }

    /**
     * Exposed method to perform a custom GATT operation.
     * Note: This is a generic placeholder. Implement specific operations as needed.
     * Events Emitted:
     * - custom_gatt_operation_started: Emitted when a custom GATT operation starts.
     * - custom_gatt_operation_error: Emitted if the custom GATT operation fails or is unsupported.
     * @param macAddress The MAC address of the connected device.
     * @param operation The name of the operation (e.g., "read", "write").
     * @param characteristicUuid The UUID of the characteristic.
     * @param value Optional value for write operations, represented as a hex string.
     */
    @UsedByGodot
    fun performCustomGattOperation(macAddress: String, operation: String, characteristicUuid: String, value: String = "") {
        Log.v(TAG, "performCustomGattOperation() called for $macAddress, Operation: $operation, UUID: $characteristicUuid, Value: $value")
        sendGodotEvent("ble_custom_gatt_operation_started", macAddress, operation, characteristicUuid, value)

        when (operation.lowercase()) {
            "read" -> {
                readCharacteristic(macAddress, characteristicUuid)
            }
            "write" -> {
                writeCharacteristic(macAddress, characteristicUuid, value)
            }
            else -> {
                Log.e(TAG, "Unsupported GATT operation: $operation")
                sendGodotEvent("ble_custom_gatt_operation_error", macAddress, operation, characteristicUuid, "Unsupported operation")
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
        deviceDisposablesMap.values.forEach { it.dispose() }
        deviceDisposablesMap.clear()
        super.onMainDestroy()
    }

    /**
     * Helper method to run actions on the UI thread.
     */
    private fun runOnUiThread(action: () -> Unit) {
        activity?.let {
            it.runOnUiThread(action)
        } ?: Log.e(TAG, "Activity is null, cannot run on UI thread")
    }

    
    /**
     * Sends an event to Godot with the specified name and parameters.
     * Instead of overloading, create a private, generic helper method to abstract event handling and use a safe public dispatcher.
     */
    // @UsedByGodot
    fun sendGodotEvent(eventName: String, vararg params: Any) {
        activity?.runOnUiThread {
            Log.v(TAG, "sendGodotEvent: '$eventName'; params: ${params.joinToString()}")
            try {
                emitSignal(eventName, *params)
            } catch (e:Exception) {
                Log.e(TAG, "Error emitting Godot signal: $eventName, ${e.message}")
            }

        } ?: Log.e(TAG, "Activity is null, cannot emit event: $eventName")
    }


}


