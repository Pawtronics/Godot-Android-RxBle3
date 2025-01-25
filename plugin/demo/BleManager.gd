# BleManager.gd
extends Node

## ⚠️ BleManager *MUST AVOID* including any Pawtronics device logic
# Acts as a bridge between Godot UI and the Android plugin.
# Listens for signals emitted by GodotAndroidPlugin.kt and re-emits simplified versions for the UI.
# Future device state, high level background tasks (ex: audio file transfer) related to IOT intra-device connectivity
# Should NOT duplicate signal names from the Android plugin to avoid infinite loops.
#
# If signals have the same name across the Android plugin and the Godot layers, they might cause recursive emissions, leading to crashes. 
# The solution is to ensure distinct names and clear ownership.

# Suggested signals for Godot:
# scan_progress(seconds_left)
# device_discovered(mac_address, name)
# scan_complete(success)
# connection_state(mac_address, state)




# Signal definitions to communicate BLE events to the main application
signal scan_started
signal scan_stopped
signal scan_progress

## ble_ are incoming from RxAndroidBleGd
signal ble_device_found(mac_address, device_name)

signal ble_pairing_init(macAddress)
signal ble_pairing_error(macAddress)

# characteristics like battery life
signal ble_read_characteristic_started(mac_address, characteristicUuid)
signal ble_read_characteristic_success(mac_address, characteristicUuid, value)
signal ble_read_characteristic_error(mac_address, characteristicUuid, errMsg)

## non ble_ are godot facing

signal pairing_init(macAddress)

# Singleton instance for managing BLE devices
signal device_created(mac_address)
signal device_removed(mac_address)

var _plugin_name = "RxAndroidBleGd"
var _RxAndroidBleGd

# Define known characteristic UUIDs
const CHARACTERISTIC_BATTERY_LEVEL = "00002a19-0000-1000-8000-00805f9b34fb"

# Characteristic lookup table
var characteristic_lookup = {
	CHARACTERISTIC_BATTERY_LEVEL: "battery_level"
}


var scan_timer: Timer = null
var remaining_time: int = 30  # 30-second scan timeout

var devices: Dictionary = {}

func _on_ble_device_found(mac_address, device_name):
	if not devices.has(mac_address):
		var device_node = preload("res://addons/RxAndroidBleGd/PawtronicsDeviceNode.gd").new()
		device_node.initialize(mac_address, device_name)
		add_child(device_node)
		devices[mac_address] = device_node
		emit_signal("device_created", mac_address)
		await get_tree().create_timer(3.0).timeout
		device_node.read_battery_level()


func remove_device(mac_address):
	if devices.has(mac_address):
		devices[mac_address].queue_free()
		devices.erase(mac_address)
		emit_signal("device_removed", mac_address)

func get_device(mac_address):
	return devices.get(mac_address, null)

##

func get_diagnostics() -> String:
	if _RxAndroidBleGd:
		return _RxAndroidBleGd.getDiagnostics()
	return "Diagnostics unavailable"

func show_debug_toast(message: String):
	if _RxAndroidBleGd:
		_RxAndroidBleGd.showDebugToast(message)


func _ready():
	if Engine.has_singleton(_plugin_name):
		_RxAndroidBleGd = Engine.get_singleton(_plugin_name)
		if _RxAndroidBleGd:
			_connect_plugin_signals()
			print("BleManager is _ready")
	else:
		printerr("Couldn't find plugin " + _plugin_name)
	_connect_local_signals()


func _connect_local_signals():
	var ourSignals = [
		"pairing_init"
	]

	for xsignal in ourSignals:
		if has_signal(xsignal):
			var method_name = "_on_" + xsignal
			if has_method(method_name):
				_RxAndroidBleGd.connect(xsignal, Callable(self, method_name))
				print("signal connected ", xsignal, " to ", method_name)
			else:
				printerr("Method not found for signal:", xsignal)
		else:
			printerr("Signal not found in BleManager:", xsignal)


# Add this helper function somewhere near other read functions.

func read_battery_level(mac_address: String) -> void:
	if _RxAndroidBleGd:
		var characteristicUuid = "00002a19-0000-1000-8000-00805f9b34fb"
		_RxAndroidBleGd.readCharacteristic(mac_address, characteristicUuid)
	else:
		printerr("RxAndroidBleGd not loaded; cannot read battery level.")


func _connect_plugin_signals():
	
	# By convention _RxAndroidBleGd  always have ble_* prefix
	# normal non-ble_* prefix is outgoing to main

	var RxAndroidBleGd_signals = [
		#"ble_scan_started",
		#"ble_scan_stopped",
		"ble_device_found",			# success
		"ble_pairing_init",			# start
		"ble_pairing_error",		# 💩
		# "ble_pairing_success",	# 👆 use ble_device_found()
		
		
		# ex: battery_life
		"ble_read_characteristic_success",
		"ble_read_characteristic_error",
		
		# note: there are also local signals
		]

	# connect all the signals
	for signalx in RxAndroidBleGd_signals:
		if BleManager.has_signal(signalx):
			var method_name = "_on_" + signalx
			if has_method(method_name):
				_RxAndroidBleGd.connect(signalx, Callable(self, method_name))
				print("signal connected ", signalx, " to ", method_name)
			else:
				printerr("Method not found for signal:", signalx)
		else:
			printerr("Signal not found in _RxAndroidBleGd:", signalx)


func start_scan(device_name = "", mac_address = "", service_uuid = ""):
	if _RxAndroidBleGd:
		_start_scan_timer()
		_RxAndroidBleGd.startScan(device_name, mac_address, service_uuid)
	else:
		printerr("RxAndroidBleGd not loaded")


func _start_scan_timer():
	if scan_timer == null:
		scan_timer = Timer.new()
		scan_timer.wait_time = 1
		scan_timer.autostart = true
		scan_timer.connect("timeout", Callable(self, "_on_scan_timer_tick"))
		add_child(scan_timer)
	scan_timer.start()
	# 🦨 make duration ble device scan will stay enabled a release/debug differniated constant in the gradle config.
	remaining_time = 3
	emit_signal("scan_progress", remaining_time)

func _on_scan_timer_tick():
	if remaining_time > 0:
		remaining_time -= 1
		# get_node("ButtonScan").text = "Scanning... %d" % remaining_time
		emit_signal("scan_progress", remaining_time)
	else:
		stop_scan()

func stop_scan():
	if _RxAndroidBleGd:
		_RxAndroidBleGd.stopScan()
		if scan_timer:
			scan_timer.stop()
		# get_node("ButtonScan").text = "Start Scan"
		# emit_signal("scan_stopped")
		# emit_signal("scan_progress", 0)  
	else:
		printerr("RxAndroidBleGd not loaded")


func connect_device(mac_address):
	if _RxAndroidBleGd:
		_RxAndroidBleGd.connectToDevice(mac_address)
	else:
		printerr("RxAndroidBleGd not loaded")

func disconnect_device(mac_address):
	print("BleManager.disconnect_device: ", mac_address)
	if _RxAndroidBleGd:
		_RxAndroidBleGd.disconnectDevice(mac_address)
	else:
		printerr("RxAndroidBleGd not loaded")
		
func read_characteristic(mac_address, characteristic_uuid):
	if _RxAndroidBleGd:
		_RxAndroidBleGd.readCharacteristic(mac_address, characteristic_uuid)
	else:
		printerr("RxAndroidBleGd not loaded")

func write_characteristic(mac_address, characteristic_uuid, value_hex):
	if _RxAndroidBleGd:
		_RxAndroidBleGd.writeCharacteristic(mac_address, characteristic_uuid, value_hex)
	else:
		printerr("RxAndroidBleGd not loaded")

func subscribe_notifications(mac_address, characteristic_uuid):
	if _RxAndroidBleGd:
		_RxAndroidBleGd.subscribeToNotifications(mac_address, characteristic_uuid)
	else:
		printerr("RxAndroidBleGd not loaded")

func unsubscribe_notifications(mac_address, characteristic_uuid):
	if _RxAndroidBleGd:
		_RxAndroidBleGd.unsubscribeFromNotifications(mac_address, characteristic_uuid)
	else:
		printerr("RxAndroidBleGd not loaded")

func pair_device(mac_address):
	if _RxAndroidBleGd:
		print("pair_device ", mac_address)
		# _RxAndroidBleGd.pairDevice(mac_address)
		_RxAndroidBleGd.connectToDevice(mac_address)
	else:
		printerr("RxAndroidBleGd not loaded cannot pair_device")

func request_mtu(mac_address, mtu_size):
	if _RxAndroidBleGd:
		_RxAndroidBleGd.requestMtu(mac_address, mtu_size)
	else:
		printerr("RxAndroidBleGd not loaded")


func _on_ble_event(mac_address, eventStr):
	# if eventStr str suffix is () see if it's looking for a signal
	# task: emit a signal only if it exists
	pass
	

	

func _on_ble_scan_started():
	emit_signal("scan_started")
	pass

func _on_ble_scan_stopped():
	emit_signal("scan_stopped")
	pass

func _on_scan_progress(remains):
	print("scan_progress: ", remains)
	# emit_signal("scan_progress", remains)
	pass


func _on_ble_read_characteristic_success(mac_address, characteristicUuid, value):
	var device = get_device(mac_address)
	if device:
		if characteristic_lookup.has(characteristicUuid):
			var metric = characteristic_lookup[characteristicUuid]
			match metric:
				"battery_level":
					var battery_level = value.hex_encode().hex_to_int()
					device.battery_level = battery_level
					print("Updated battery level for: ", mac_address, " -> ", battery_level, "%")
				_:
					print("Unhandled characteristic: ", characteristicUuid)
		else:
			printerr("Unknown characteristic UUID received: ", characteristicUuid)
	else:
		printerr("Device not found for MAC: ", mac_address)


func _on_ble_read_characteristic_error(mac_address, charactersticUuid, errStr):
	printerr("🦨 _on_ble_read_characteristic_error: ", charactersticUuid, errStr)
	pass

func _on_ble_pairing_init(mac_address):
	print("_on_ble_pairing_init() .. is empty function")
	emit_signal("pairing_init", mac_address)

func _on_ble_pairing_error(mac_address, error_message):
	emit_signal("pairing_error", mac_address, error_message)

func _on_connected(mac_address):
	emit_signal("connected", mac_address)

func _on_disconnected(mac_address):
	emit_signal("disconnected", mac_address)



func _on_scan_error(error_message):
	emit_signal("scan_error", error_message)

func _on_connect_started(mac_address):
	emit_signal("connect_started", mac_address)


func _on_connect_error(mac_address, error_message):
	emit_signal("connect_error", mac_address, error_message)


func _on_read_success(mac_address, characteristic_uuid, value):
	emit_signal("read_characteristic_success", mac_address, characteristic_uuid, value)

func _on_read_error(mac_address, characteristic_uuid, error_message):
	emit_signal("read_characteristic_error", mac_address, characteristic_uuid, error_message)

func _on_write_success(mac_address, characteristic_uuid, value):
	emit_signal("write_characteristic_success", mac_address, characteristic_uuid, value)

func _on_write_error(mac_address, characteristic_uuid, error_message):
	emit_signal("write_characteristic_error", mac_address, characteristic_uuid, error_message)

func _on_notification_received(mac_address, characteristic_uuid, value):
	emit_signal("notification_received", mac_address, characteristic_uuid, value)

func _on_notification_error(mac_address, characteristic_uuid, error_message):
	emit_signal("notification_error", mac_address, characteristic_uuid, error_message)

func _on_pairing_started(mac_address):
	emit_signal("pairing_started", mac_address)


func _on_request_mtu_success(mac_address, granted_mtu):
	emit_signal("request_mtu_success", mac_address, granted_mtu)

func _on_request_mtu_error(mac_address, error_message):
	emit_signal("request_mtu_error", mac_address, error_message)

func _on_connection_state_changed(mac_address, connection_state):
	emit_signal("connection_state_changed", mac_address, connection_state)

func _on_connection_state_error(mac_address, error_message):
	emit_signal("connection_state_error", mac_address, error_message)
