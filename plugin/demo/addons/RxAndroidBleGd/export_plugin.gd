@tool
extends EditorPlugin

# A class member to hold the editor export plugin during its lifecycle.
var export_plugin : AndroidExportPlugin

func _enter_tree():
	# Initialization of the plugin goes here.
	export_plugin = AndroidExportPlugin.new()
	add_export_plugin(export_plugin)


func _exit_tree():
	# Clean-up of the plugin goes here.
	remove_export_plugin(export_plugin)
	export_plugin = null


class AndroidExportPlugin extends EditorExportPlugin:
	# ✅: Update to your plugin's name.
	var _plugin_name = "RxAndroidBleGd"

	func _supports_platform(platform):
		if platform is EditorExportPlatformAndroid:
			return true
		return false

	func _get_android_libraries(platform, debug):
		if debug:
			return PackedStringArray([_plugin_name + "/bin/debug/" + _plugin_name + "-debug.aar"])
		else:
			return PackedStringArray([_plugin_name + "/bin/release/" + _plugin_name + "-release.aar"])

	func _get_android_dependencies(platform, debug):
		# TODO: Add remote dependices here.
		if platform is EditorExportPlatformAndroid:
			var dependencies = PackedStringArray([
				"com.polidea.rxandroidble3:rxandroidble:1.19.0",
				"io.reactivex.rxjava3:rxandroid:3.0.2",
				"io.reactivex.rxjava3:rxjava:3.1.5"
			])
			return dependencies
		return PackedStringArray([])

	func _get_android_dependencies_maven_repos(platform, debug):
		return PackedStringArray([
			"https://repo.maven.apache.org/maven2/",
			"https://s01.oss.sonatype.org/content/repositories/releases/"
		])

	func _get_android_manifest_element_contents(platform, debug):
		return """
			<uses-permission android:name="android.permission.BLUETOOTH"/>
			<uses-permission android:name="android.permission.BLUETOOTH_ADMIN"/>
			<uses-permission android:name="android.permission.BLUETOOTH_CONNECT"/>
			<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION"/>
		"""

	func _get_android_manifest_application_element_contents(platform, debug):
		return """
			<meta-data android:name="org.godotengine.plugin.v2.RxAndroidBleGd" 
					android:value="org.godotengine.plugin.android.rxble3.GodotAndroidPlugin"/>
		"""


	func _get_name():
		return _plugin_name
