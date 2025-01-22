extends Node2D

# TODO: Update to match your plugin's name
var _plugin_name = "RxAndroidBleGd"
var _android_plugin

func _ready():
	if Engine.has_singleton(_plugin_name):
		_android_plugin = Engine.get_singleton(_plugin_name)
		request_permissions()
	else:
		printerr("Couldn't find plugin " + _plugin_name)


func request_permissions():
	if OS.get_name() == "Android":
		var permissions = [
			"android.permission.ACCESS_FINE_LOCATION",
			"android.permission.ACCESS_COARSE_LOCATION",
			"android.permission.BLUETOOTH_CONNECT",
			"android.permission.BLUETOOTH_SCAN"
		]
		for permission in permissions:
			if not OS.request_permission(permission):
				print("Permission requested:", permission)
			else:
				print("Permission already granted:", permission)


func _on_ButtonHello_pressed():
	printerr(_plugin_name)
	if _android_plugin:
		# TODO: Update to match your plugin's API
		_android_plugin.helloWorld()
	else:
		printerr("no plugin loaded")

func _on_ButtonScan_pressed():
	if _android_plugin:
		_android_plugin.startScan()
	else:
		printerr(_plugin_name)


func _on_pressed() -> void:
	pass # Replace with function body.
