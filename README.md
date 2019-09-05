# P2oggle
[Jump to source](app/src/main/java/eu/biqqles/p2oggle/) | [Project thread]() | [Screenshots]()

The [Lenovo P2](https://en.wikipedia.org/wiki/Lenovo_P2) features a somewhat unusual hardware switch (referred to by Lenovo as the "one-key power saver"). On the stock ROM this switch toggles a battery saving mode but in aftermarket development it has remained unused - until now. P2oggle is an app which enables this switch and allows you to assign toggleable "actions" to it. Currently, these include:

|Flashlight|Silent mode|Battery saver|Aeroplane mode|
|:---:|:---:|:---:|:---:|
|**Wi-Fi**|**Mobile data**|**Bluetooth**|**NFC**|

Actions can be configured separately for when the screen is on or off. If you have suggestions for actions you think would be useful, please make them in the project thread and I will be happy to add them.

### Requirements

Since the Android input stack [more or less ignores](https://source.android.com/devices/input#understanding-hid-usages-and-event-codes) the existence of hardware switches, P2oggle uses the kernel's input event interface (evdev) directly. It therefore **requires a rooted device**.

The service that listens for switch input consumes minimal resources: in my testing battery usage has never risen above 0% with memory consumption averaging about 20 MB.

P2oggle is compatible with the stock ROM and should work with all custom ROMs and kernels. I wrote it for the `P2a47` (global variant) but it should also be fully compatible with the `P2c72` (domestic variant). If it works for you please let me know.

P2oggle is released under the Mozilla General Public License version 2.0.

### Changelog
#### 0.1
- Initial release