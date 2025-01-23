# Demo App

Explanation of BleManager.gd:

Signals: Defined to relay BLE events from the plugin to the main application.
Initialization (_ready): Checks for the plugin singleton and connects its signals to internal handler methods.
High-Level Methods: Methods like start_scan, connect, write_characteristic, etc., abstract the plugin's functionality.
Signal Handlers: Each handler receives signals from the plugin and re-emits them, allowing main.gd to connect to BleManager.gd's signals instead of the plugin's directly.


Configure BleManager.gd as an Autoload Singleton:

1. Go to Project Settings:
    In Godot, navigate to Project > Project Settings > Autoload.
2. Add BleManager.gd as a Singleton:
    Name: BleManager
    Path: res://path_to_your_script/BleManager.gd
    Check: Enable
3. Save Changes:
    This makes BleManager accessible globally via BleManager in any GDScript.

