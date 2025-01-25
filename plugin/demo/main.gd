# main.gd
extends Node2D


# Reference to the BleManager singleton
var ble_manager = BleManager
var GodotOS = OS


func _ready():
	if ble_manager:
		request_permissions()
		#ble_manager._connect_plugin_signals()
		print("main+ble_manager is _ready")
		ble_manager.connect("scan_progress", Callable(self, "_on_scan_progress"))
		ble_manager.connect("ble_device_found", Callable(self, "_on_ble_device_found"))
	else:
		printerr("BleManager singleton not found")

func request_permissions():
	print("GodotOS Type:", typeof(GodotOS)) # Should print "Class"
	print("GodotOS:", GodotOS)	
	if GodotOS.get_name() == "Android":
		var permissions = [
			"android.permission.ACCESS_FINE_LOCATION",
			"android.permission.ACCESS_COARSE_LOCATION",
			"android.permission.BLUETOOTH_CONNECT",
			"android.permission.BLUETOOTH_SCAN"
		]

		if OS.has_method("request_permissions"):
			OS.request_permissions()
			print("Requested permissions:", permissions)
		else:
			print("Permission request API not available")
			
# UI Button Handlers (Assuming you're using Godot's UI system with signals connected to these methods)

func _on_ButtonScanPair_pressed():
	ble_manager.start_scan("Pawtronics-RD1")
	

func _on_scan_progress(seconds_left):
	print("main.gd on_scan_progress ", seconds_left)
	#var button = get_node("ButtonScan")
	#if button:
		#if seconds_left > 0:
			#button.text = "Scanning... %d" % seconds_left
		#else:
			#button.text = "Start Scan"

func _on_ButtonHello_pressed():
	if ble_manager:
		var diagnostics = ble_manager.get_diagnostics()
		print(diagnostics)
		ble_manager.show_debug_toast("Diagnostics fetched!")
	else:
		printerr("BleManager not initialized")


#func _on_ButtonConnect_pressed(mac_address: String):
	#ble_manager.connect_device(mac_address)

#func _on_ButtonWrite_pressed(mac_address: String, characteristic_uuid: String, value_hex: String):
	#ble_manager.write_characteristic(mac_address, characteristic_uuid, value_hex)
#
## Example: Handling a specific button to write a fixed value to RD1


#func _on_scan_started():
	#print("Scan started")
#
#func _on_scan_stopped():
	#print("Scan stopped")
#
#
#func _on_device_discovered(mac_address, device_name):
	#print("Device discovered: %s (%s)" % [mac_address, device_name])
	## Optionally, display in UI or store in a list
#
#func _on_scan_error(error_message):
	#printerr("Scan error: %s" % error_message)
#
#func _on_connect_started(mac_address):
	#print("Connecting to %s" % mac_address)
#
#func _on_connected(mac_address):
	#print("Connected to %s" % mac_address)
#
#func _on_connect_error(mac_address, error_message):
	#printerr("Connection error with %s: %s" % [mac_address, error_message])
#
#func _on_disconnected(mac_address):
	#print("Disconnected from %s" % mac_address)
#
#func _on_read_success(mac_address, characteristic_uuid, value):
	#print("Read from %s [%s]: %s" % [mac_address, characteristic_uuid, value])
#
#func _on_read_error(mac_address, characteristic_uuid, error_message):
	#printerr("Read error from %s [%s]: %s" % [mac_address, characteristic_uuid, error_message])
#
#func _on_write_success(mac_address, characteristic_uuid, value):
	#print("Write to %s [%s]: %s" % [mac_address, characteristic_uuid, value])
#
#func _on_write_error(mac_address, characteristic_uuid, error_message):
	#printerr("Write error to %s [%s]: %s" % [mac_address, characteristic_uuid, error_message])
#
#func _on_notification_received(mac_address, characteristic_uuid, value):
	#print("Notification from %s [%s]: %s" % [mac_address, characteristic_uuid, value])
#
#func _on_notification_error(mac_address, characteristic_uuid, error_message):
	#printerr("Notification error from %s [%s]: %s" % [mac_address, characteristic_uuid, error_message])
#
#func _on_pairing_started(mac_address):
	#print("Pairing started with %s" % mac_address)
#
#func _on_pairing_initiated(mac_address):
	#print("Pairing initiated with %s" % mac_address)
#
#func _on_pairing_failed(mac_address, error_message):
	#printerr("Pairing failed with %s: %s" % [mac_address, error_message])
#
#func _on_pairing_error(mac_address, error_message):
	#printerr("Pairing error with %s: %s" % [mac_address, error_message])
#
#func _on_request_mtu_success(mac_address, granted_mtu):
	#print("MTU request successful for %s: %d" % [mac_address, granted_mtu])
#
#func _on_request_mtu_error(mac_address, error_message):
	#printerr("MTU request error for %s: %s" % [mac_address, error_message])
#
#func _on_connection_state_changed(mac_address, connection_state):
	#print("Connection state for %s: %s" % [mac_address, connection_state])
#
#func _on_connection_state_error(mac_address, error_message):
	#printerr("Connection state error for %s: %s" % [mac_address, error_message])


func _on_buttonToggle_pressed() -> void:
	print("_on_buttonToggle_pressed()")
	for mac_address in BleManager.devices.keys():
		var device = BleManager.get_device(mac_address)
		if device:
			device.toggle_pwm()
		else:
			printerr("Device not found for MAC: ", mac_address)	
	
