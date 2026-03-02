<div align="center">

# TaffyQSL

<p align="center">
  <img src="taffyQSL_noblack.svg" alt="Taffy QSL icon" style="width: 192px" />
</p>

<p><br/></p>


<p align="center">
  <a href="https://github.com/sophiel-meow/TaffyQSL/releases/latest">
    <img src="https://img.shields.io/badge/Download-APK-brightgreen?style=for-the-badge&logo=android" alt="Download APK">
  </a>
</p>

  [![license](https://img.shields.io/badge/license-GPLv3-blue.svg)](LICENSE) 
  [![kotlin](https://img.shields.io/badge/Kotlin-2.3-orange.svg)](https://kotlinlang.org) 
  [![android](https://img.shields.io/badge/Android-16-brightgreen.svg)](https://developer.android.com)


</div>

---



TaffyQSL is a 100% free, libre, and privacy-respecting amateur radio logbook for Android.  
It supports ADIF file management, QSO signing, and LoTW integration, with **hardware-backed key protection** via the Android Keystore.  

Designed for amateur radio operators who value privacy and security, TaffyQSL keeps your certificates and logs safely on your device.


## Features

- Create, edit, and export ADIF files  
- QSO signing compatible with LoTW  
- Hardware-backed private key protection (if supported by your device)  
- Natural-language QSO parser for fast and easy logging  
- Satellite and DXCC entity support  
- Customizable date and time display formats  


## Privacy & Security

- **Keys are generated and stored locally** in the Android Keystore  
- **Private keys never leave your device**  
- **No telemetry or tracking**  
- **100% free & open-source**  
- Designed to protect your privacy while interacting with LoTW  


## Installation

- **Minimum Android version:** Android 11 (API 30)  
- Download the latest APK from the [Releases](https://github.com/sophiel-meow/TaffyQSL/releases) page  
- F-Droid release will be available soon


## Build from Source

TaffyQSL is written in Kotlin using Gradle Kotlin DSL.

```bash
git clone git@github.com:sophiel-meow/TaffyQSL.git
cd TaffyQSL
./gradlew assembleDebug
````

**Requirements:**

* Kotlin
* Latest Android Studio
* Minimum SDK 30


## License

TaffyQSL is licensed under the **GNU General Public License v3.0**.
Not affiliated with ARRL or LoTW.
All trademarks belong to their respective owners.


## Disclaimer

* This software is provided *as-is*, without any warranty.
* Use at your own risk.
* LoTW is a trademark of the American Radio Relay League, Inc.
* TaffyQSL is an independent project and is not endorsed by ARRL.
* Hardware-backed encryption depends on your device's capabilities


## Contributing

Contributions are welcome!

* Please open an issue for feature requests or bug reports
* Pull requests are accepted for bug fixes and improvements
* Discussions, issues, and contributions are welcome in **English (preferred), Chinese, or Japanese**


## TODO

* [ ] F-Droid release optimization
* [ ] PIN & Biometric support
* [ ] Additional satellite support
* [ ] Backup support
* [ ] Online query / sync for QRZ.com
* [ ] Statistics functions
