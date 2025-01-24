# BleManager.gd
extends Node

## ⚠️ BleManager *MUST AVOID* including any Pawtronics device logic
# Acts as a bridge between Godot UI and the Android plugin.
# Listens for signals emitted by GodotAndroidPlugin.kt and re-emits simplified versions for the UI.
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
signal device_found(mac_address, device_name)
signal scan_error(error_message)
signal connect_started(mac_address)
signal connected(mac_address)
signal connect_error(mac_address, error_message)
signal disconnected(mac_address)
signal read_characteristic_success(mac_address, characteristic_uuid, value)
signal read_characteristic_error(mac_address, characteristic_uuid, error_message)
signal write_characteristic_success(mac_address, characteristic_uuid, value)
signal write_characteristic_error(mac_address, characteristic_uuid, error_message)
signal notification_received(mac_address, characteristic_uuid, value)
signal notification_error(mac_address, characteristic_uuid, error_message)
signal pairing_started(mac_address)
signal pairing_initiated(mac_address)
signal pairing_failed(mac_address, error_message)
signal pairing_error(mac_address, error_message)
signal request_mtu_success(mac_address, granted_mtu)
signal request_mtu_error(mac_address, error_message)
signal connection_state_changed(mac_address, connection_state)
signal connection_state_error(mac_address, error_message)

var _plugin_name = "RxAndroidBleGd"
var _android_plugin


var scan_timer: Timer = null
var remaining_time: int = 30  # 30-second scan timeout

func get_diagnostics() -> String:
	if _android_plugin:
		return _android_plugin.getDiagnostics()
	return "Diagnostics unavailable"

func show_debug_toast(message: String):
	if _android_plugin:
		_android_plugin.showDebugToast(message)


func _ready():
	if Engine.has_singleton(_plugin_name):
		_android_plugin = Engine.get_singleton(_plugin_name)
		if _android_plugin:
			_connect_plugin_signals()
			print("BleManager is _ready")			
	else:
		printerr("Couldn't find plugin " + _plugin_name)


func _connect_plugin_signals():
	var ble_signals = [
		"ble_scan_started",
		"ble_scan_stopped",
		"ble_device_found",
		"ble_scan_error",
		#"connect_started",
		#"connected",
		#"connect_error",
		#"disconnected"
		# FUTURE: 
		#_android_plugin.connect("read_characteristic_success", self, "_on_read_success")
		#_android_plugin.connect("read_characteristic_error", self, "_on_read_error")
		#_android_plugin.connect("write_characteristic_success", self, "_on_write_success")
		#_android_plugin.connect("write_characteristic_error", self, "_on_write_error")
		#_android_plugin.connect("notification_received", self, "_on_notification_received")
		#_android_plugin.connect("notification_error", self, "_on_notification_error")
		#_android_plugin.connect("pairing_started", self, "_on_pairing_started")
		#_android_plugin.connect("pairing_initiated", self, "_on_pairing_initiated")
		#_android_plugin.connect("pairing_failed", self, "_on_pairing_failed")
		#_android_plugin.connect("pairing_error", self, "_on_pairing_error")
		#_android_plugin.connect("request_mtu_success", self, "_on_request_mtu_success")
		#_android_plugin.connect("request_mtu_error", self, "_on_request_mtu_error")
		#_android_plugin.connect("connection_state_changed", self, "_on_connection_state_changed")
		#_android_plugin.connect("connection_state_error", self, "_on_connection_state_error")
		]

	# connect all the signals
	for signalx in ble_signals:
		if has_signal(signalx):
			var method_name = "_on_" + signalx
			if has_method(method_name):
				print("signal connected ", signalx, " to ", method_name)
				connect(signalx, Callable(self, method_name))
			else:
				printerr("Method not found for signal:", signalx)
		else:
			printerr("Signal not found in BleManager:", signalx)


func start_scan(device_name = "", mac_address = "", service_uuid = ""):
	if _android_plugin:
		_android_plugin.startScan(device_name, mac_address, service_uuid)
		_start_scan_timer()
	else:
		printerr("BLE Plugin not loaded")

func _start_scan_timer():
	if scan_timer == null:
		scan_timer = Timer.new()
		scan_timer.wait_time = 1
		scan_timer.autostart = true
		scan_timer.connect("timeout", Callable(self, "_on_scan_timer_tick"))
		add_child(scan_timer)
	scan_timer.start()
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
	if _android_plugin:
		_android_plugin.stopScan()
		if scan_timer:
			scan_timer.stop()
		# get_node("ButtonScan").text = "Start Scan"
		# emit_signal("scan_stopped")
		# emit_signal("scan_progress", 0)  
	else:
		printerr("BLE Plugin not loaded")


func connect_device(mac_address):
	if _android_plugin:
		_android_plugin.connectToDevice(mac_address)
	else:
		printerr("BLE Plugin not loaded")

func disconnect_device(mac_address):
	if _android_plugin:
		_android_plugin.disconnectDevice(mac_address)
	else:
		printerr("BLE Plugin not loaded")
		
func read_characteristic(mac_address, characteristic_uuid):
	if _android_plugin:
		_android_plugin.readCharacteristic(mac_address, characteristic_uuid)
	else:
		printerr("BLE Plugin not loaded")

func write_characteristic(mac_address, characteristic_uuid, value_hex):
	if _android_plugin:
		_android_plugin.writeCharacteristic(mac_address, characteristic_uuid, value_hex)
	else:
		printerr("BLE Plugin not loaded")

func subscribe_notifications(mac_address, characteristic_uuid):
	if _android_plugin:
		_android_plugin.subscribeToNotifications(mac_address, characteristic_uuid)
	else:
		printerr("BLE Plugin not loaded")

func unsubscribe_notifications(mac_address, characteristic_uuid):
	if _android_plugin:
		_android_plugin.unsubscribeFromNotifications(mac_address, characteristic_uuid)
	else:
		printerr("BLE Plugin not loaded")

func pair_device(mac_address):
	if _android_plugin:
		_android_plugin.pairDevice(mac_address)
	else:
		printerr("BLE Plugin not loaded")

func request_mtu(mac_address, mtu_size):
	if _android_plugin:
		_android_plugin.requestMtu(mac_address, mtu_size)
	else:
		printerr("BLE Plugin not loaded")

# Signal handler methods to re-emit or process signals

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

func _on_ble_device_found(mac_address, device_name):
	emit_signal("device_found", mac_address, device_name)

func _on_scan_error(error_message):
	emit_signal("scan_error", error_message)

func _on_connect_started(mac_address):
	emit_signal("connect_started", mac_address)

func _on_connected(mac_address):
	emit_signal("connected", mac_address)

func _on_connect_error(mac_address, error_message):
	emit_signal("connect_error", mac_address, error_message)

func _on_disconnected(mac_address):
	emit_signal("disconnected", mac_address)

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

func _on_pairing_initiated(mac_address):
	emit_signal("pairing_initiated", mac_address)

func _on_pairing_failed(mac_address, error_message):
	emit_signal("pairing_failed", mac_address, error_message)

func _on_pairing_error(mac_address, error_message):
	emit_signal("pairing_error", mac_address, error_message)

func _on_request_mtu_success(mac_address, granted_mtu):
	emit_signal("request_mtu_success", mac_address, granted_mtu)

func _on_request_mtu_error(mac_address, error_message):
	emit_signal("request_mtu_error", mac_address, error_message)

func _on_connection_state_changed(mac_address, connection_state):
	emit_signal("connection_state_changed", mac_address, connection_state)

func _on_connection_state_error(mac_address, error_message):
	emit_signal("connection_state_error", mac_address, error_message)
