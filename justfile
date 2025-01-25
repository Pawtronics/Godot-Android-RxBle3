
install:
    # cargo install yek
    eget bodo-run/yek  --asset musl

yek:
    # yek 0.13.8
    # Arguments:
    # [directories]...  Directories to process [default: .]

    # Options:
    #     --max-size <max-size>      Maximum size per chunk (e.g. '10MB', '128KB', '1GB' or '100K' tokens when --tokens is used) [default: 10MB]
    #     --tokens                   Count size in tokens instead of bytes
    #     --debug                    Enable debug output
    #     --output-dir <output-dir>  Output directory for chunks
    # -h, --help                     Print help
    # -V, --version                  Print version

    # wsl: windows subsystem for linux example
    ./yek . --max-size 8K | clip.exe

build:
    #  .\build-watch.ps1
    ./gradlew assemble build
    ls -la ./plugin/build/outputs/aar

buildloop:
    .\build-watch.ps1

logs:
    adb logcat *:S godot:V RxAndroidBleGd:V bluetooth:V bt_stack:V [BT]:V

lesslogs:
    adb logcat *:S godot:V RxAndroidBleGd:V 

prompt:
    # files-to-prompt . plugin/src/main/AndroidManifest.xml --ignore-gitignore --ignore "plugin/build/*" --ignore "THIRDPARTY.md" --ignore "README.md" -e md -e kt -e kts -e godot -e java -e gd -e md -e gradle -e cfg -e tres -e tcsn  | clip.exe
    files-to-prompt . plugin/src/main/AndroidManifest.xml --ignore-gitignore --ignore "plugin/build/*" --ignore "THIRDPARTY.md" --ignore "README.md" -e kt -e kts -e godot -e java -e gd -e md -e cfg -e tres -e tcsn  | clip.exe
    # yek


check-permissions:
    adb shell dumpsys package org.godotengine.android.plugin.demo | grep permission
    #adb shell pm grant org.godotengine.android.plugin.demo android.permission.BLUETOOTH
    #adb shell pm grant org.godotengine.android.plugin.demo android.permission.BLUETOOTH_ADMIN
    #adb shell pm grant org.godotengine.android.plugin.demo android.permission.ACCESS_FINE_LOCATION
    #adb shell pm grant org.godotengine.android.plugin.demo android.permission.ACCESS_COARSE_LOCATION
    #adb shell pm grant org.godotengine.android.plugin.demo android.permission.BLUETOOTH_SCAN

