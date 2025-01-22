

build:
    ./gradlew assemble
    ls -la ./plugin/build/outputs/aar\

logs:
    adb logcat *:S godot:V


prompt:
    files-to-prompt . --ignore-gitignore --ignore "THIRDPARTY.md" --ignore "README.md" -e md -e godot -e gd -e md -e gradle -e cfg -e tres -e tcsn  | clip.exe
