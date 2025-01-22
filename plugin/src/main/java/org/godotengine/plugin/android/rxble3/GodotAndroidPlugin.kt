// ✅: Updated to match plugin's package name.
package org.godotengine.plugin.android.rxble3

import android.util.Log
import android.widget.Toast
import android.content.Context


// ⚠️ REMINDER to update plugin/export_scripts_template/export_plugin.gd function _get_android_dependencies() 
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.core.Single
import com.polidea.rxandroidble3.RxBleClient
import com.polidea.rxandroidble3.scan.ScanFilter
import com.polidea.rxandroidble3.scan.ScanSettings
import java.util.UUID

import org.godotengine.godot.Godot
import org.godotengine.godot.plugin.GodotPlugin
import org.godotengine.godot.plugin.UsedByGodot

class GodotAndroidPlugin(godot: Godot): GodotPlugin(godot) {


    // uncomment this line to generate "couldn't find plugin RxAndroidBleGd" (**SOLVED**)
    private val rxBleClient: RxBleClient = RxBleClient.create(godot.getActivity() as Context)

    // OR this one: (**ALSO SOLVED**)
    private val disposables: CompositeDisposable = CompositeDisposable()

    // this causes a segfault:
    // private val disposables by lazy { CompositeDisposable() }

    private val targetDeviceName = "Pawtronics-RD1"
    private val characteristicUuid = "00002a06-0000-1000-8000-00805f9b34fb"

    override fun getPluginName() = BuildConfig.GODOT_PLUGIN_NAME

    /**
     * Example showing how to declare a method that's used by Godot.
     *
     * Shows a 'Hello World' toast.   (this works when the plugin loads properly, but not when the plugin is missing)
     */

    private val TAG = "RxAndroidBleGd"

    // override fun getPluginName() = BuildConfig.GODOT_PLUGIN_NAME.also {
    //     Log.v(TAG, "Plugin name retrieved: $it")
    // }
    
    init {
        Log.v(TAG, "GodotAndroidPlugin initialized")
    }
    

    
    @UsedByGodot
    fun helloWorld() {
        runOnUiThread {
            Toast.makeText(activity, "Hello Droid5", Toast.LENGTH_LONG).show()
            Log.v(pluginName, "Hello droidish")
        }
    }


    private fun debugToast(message: String) {
        runOnUiThread {
            Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
        }
        Log.v(TAG, message)
    }

    @UsedByGodot
    fun startScan() {
        Log.v(TAG, "startScan() called")
        debugToast("BLE Scan Started")

        disposables.add(
            rxBleClient.scanBleDevices(
                ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),
                ScanFilter.Builder().setDeviceName(targetDeviceName).build()
            )
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ scanResult ->
                Log.v(pluginName, "Found device: ${scanResult.bleDevice.macAddress}")
                connectToDevice(scanResult.bleDevice.macAddress)
            }, { throwable ->
                Log.e(pluginName, "Scan failed: ${throwable.message}")
            })
            // TODO gracefully handle: 
            //  Scan failed: Bluetooth disabled (code 1)
            //  Scan failed: Location Permission missing (code 3)

        )
    }

    private fun connectToDevice(macAddress: String) {
        val device = rxBleClient.getBleDevice(macAddress)
        val characteristicUUID = UUID.fromString("00002a06-0000-1000-8000-00805f9b34fb")

        disposables.add(
            device.establishConnection(false)
                .flatMapSingle { connection -> connection.writeCharacteristic(characteristicUUID, byteArrayOf(0x01)) }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ response: ByteArray ->
                    Log.v(pluginName, "Write successful: ${response.contentToString()}")
                }, { throwable: Throwable ->
                    Log.e(pluginName, "Connection failed: ${throwable.message}")
                })
        )
    }


    /*

    fun initBleClient() {
        val activity = godot.activity ?: throw IllegalStateException("Activity is null")
        rxBleClient = RxBleClient.create(activity as Context)
    }
    */

    override fun onMainDestroy() {
        disposables.clear()
    }
    
}
