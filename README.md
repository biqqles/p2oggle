# P2oggle
[Jump to source](app/src/main/java/eu/biqqles/p2oggle/) | [Project thread](https://forum.xda-developers.com/devdb/project/?id=34190) | [Screenshots](https://forum.xda-developers.com/devdb/project/?id=34190#screenshots)

The [Lenovo P2](https://en.wikipedia.org/wiki/Lenovo_P2) features a somewhat unusual hardware switch (referred to by Lenovo as the "one-key power saver"). On the stock ROM this switch toggles a battery saving mode but in aftermarket development it has remained unused - until now. P2oggle (read: "*P-Toggle*") is an app which enables this switch and allows you to assign toggleable "actions" to it. Currently, these include:


<details>
  <summary>Available actions</summary>

  - Flashlight
  - Battery saver
  - Aeroplane mode
  - Wi-Fi
  - Mobile data
  - Bluetooth
  - NFC
  - Location
  - Silent
  - Vibrate
  - Alarms only
  - Priority only
  - Total silence
  - Play/pause
  - Caffeine
</details>

Actions can be configured separately for when the screen is on or off. If you have suggestions for actions you think would be useful, please make them in the project thread and I will be happy to add them.

Additionally you can configure P2oggle to broadcast switch events for other apps.

### Requirements

Since the Android input stack [more or less ignores](https://source.android.com/devices/input#understanding-hid-usages-and-event-codes) the existence of hardware switches, P2oggle uses the kernel's input event interface (evdev) directly. It therefore **requires a rooted device**. You will need a superuser binary that includes supolicy and BusyBox, e.g. Magisk.

The service that listens for switch input consumes minimal resources: in my testing battery usage has never risen above 0% with memory consumption averaging about 25 MB.

P2oggle is compatible with the stock ROM and should work with all custom ROMs and kernels. I wrote it for the `P2a42` (global variant) but it should also be fully compatible with the `P2c72` (domestic variant). If it works for you please let me know.

If you are on stock you will probably want to disable the power saver mode from being bound to the switch in order to use P2oggle properly. You can do that by running `su -c pm hide com.lenovo.powersetting` in a terminal emulator or adb shell. Replace `hide` with `unhide` to reverse.

P2oggle is released under the Mozilla General Public License version 2.0.

### Changelog
#### 0.3
Changes in this release:

- App should now be compatible with Android 10
- Minimised number of su processes to reduce toast notification spam from Magisk and lag on enabling service

#### 0.2
New in this release:

- New actions: *Vibrate*, *Silent*, *Play/pause*, *Location*, *Priority only*, *Alarms only*
- New option: *Emit broadcasts for other apps*
- Overlay theming options added
- Action *Silent mode* renamed to *Total silence* to better represent its function
- Better error messages on initial setup

Bug fixes in this release:

- Overlay should now be completely reliable
- Disabling *Start on boot* now actually works
- Service now immediately unbinds from switch when disabled
- Disabled *Notification settings* and *Hide* intents on N, where using these options would prevent the overlay from working
- Fixed superuser access not being detected until app restart

#### 0.1
- Initial release