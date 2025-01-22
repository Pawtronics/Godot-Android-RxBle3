



build:
    ./gradlew assemble build
    ls -la ./plugin/build/outputs/aar

logs:
    adb logcat *:S godot:V RxAndroidBleGd:V


prompt:
   files-to-prompt . plugin/src/main/AndroidManifest.xml --ignore-gitignore --ignore "plugin/build/*" --ignore "THIRDPARTY.md" --ignore "README.md" -e md -e kt -e kts -e godot -e java -e gd -e md -e gradle -e cfg -e tres -e tcsn  | clip.exe
   # yek 

check-permissions
    adb shell dumpsys package org.godotengine.android.plugin.demo | grep permission
    #adb shell pm grant org.godotengine.android.plugin.demo android.permission.BLUETOOTH
    #adb shell pm grant org.godotengine.android.plugin.demo android.permission.BLUETOOTH_ADMIN
    #adb shell pm grant org.godotengine.android.plugin.demo android.permission.ACCESS_FINE_LOCATION
    #adb shell pm grant org.godotengine.android.plugin.demo android.permission.ACCESS_COARSE_LOCATION
    #adb shell pm grant org.godotengine.android.plugin.demo android.permission.BLUETOOTH_SCAN

