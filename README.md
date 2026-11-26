# CallGuard 🛡️

> **Zero Ads. 100% Free & Open-Source. Privacy-First Call & SMS Protection.**

CallGuard is a modern, offline-first Android application designed to protect your smartphone from unwanted calls, spam, and telemarketers without compromising your personal data. Operating entirely on-device with no backend servers, user accounts, or internet permission required, CallGuard gives you complete control over your communication experience.

---

## ✨ Features

- **🚫 Advanced Call & SMS Blocking:** Automatically block spam, telemarketers, robocalls, and custom blacklisted numbers.
- **🔒 100% Privacy-First:** Zero ads, no cloud accounts, no third-party tracking, and **no internet permission** required in the manifest. All data stays securely on your device.
- **📞 Smart Caller ID & Dialer:** Clean, fast, and lightweight native dialer alternative that displays caller information seamlessly.
- **🎨 Jetpack Compose UI:** Built with modern Android development standards using Kotlin and Jetpack Compose for a smooth, responsive, and intuitive interface.
- **⚡ Lightweight & Fast:** Optimized for performance with minimal battery consumption and instant response times.

---

## 🏗️ Architecture & Tech Stack

- **Language:** [Kotlin](https://kotlinlang.org/) (96%+)
- **UI Framework:** [Jetpack Compose](https://developer.android.com/jetpack/compose)
- **Core Components:** 
  - `InCallService` & Telecom APIs for call management and screening.
  - Local SQLite / Room database for secure, on-device logs and blacklists.
  - Modular package structure separating dialer, call logging, contacts, and blocking rules.

---

## 📋 Requirements

- **JDK:** JDK 21 (Temurin 21 recommended)
- **Android SDK:** Compile SDK 34/35+, Min SDK 29 (Android 10+)
- **Build System:** Gradle (via Gradle Wrapper)

---

## 🚀 Getting Started & Building from Source

Follow these steps to clone, build, and run CallGuard locally:

1. **Clone the repository:**
   ```bash
   git clone https://github.com/sunnyygautam/callguard.git
   cd callguard
   ```

2. **Configure your Android SDK:**
   Ensure your `ANDROID_HOME` environment variable is set, or create a `local.properties` file in the root directory pointing to your SDK:
   ```properties
   sdk.dir=/path/to/your/android/sdk
   ```

3. **Build the Debug APK:**
   ```bash
   ./gradlew :app:assembleDebug
   ```
   The generated APK will be located at:  
   `app/build/outputs/apk/debug/app-debug.apk`

4. **Install and Launch (via ADB):**
   ```bash
   ./gradlew :app:installDebug
   adb shell am start -n com.callguard/.MainActivity
   ```

---

## ⚙️ Permissions & Setup

Upon launching CallGuard for the first time, you will be guided to grant essential system roles for full functionality:
- **Default Phone App:** Required to manage incoming/outgoing calls and provide the custom in-call screen.
- **Default SMS App:** Required to handle text messages and filter unwanted spam SMS.
- **Call Screening Role:** Enables real-time evaluation of incoming numbers against your local filter rules.

---

## 🤝 Contributing

Contributions are always welcome! Whether it's reporting a bug, proposing a new feature, or submitting a pull request:
1. Fork the Project (`https://github.com/sunnyygautam/callguard/fork`)
2. Create your Feature Branch (`git checkout -b feature/AmazingFeature`)
3. Commit your Changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the Branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

---

## 📄 License

Distributed under the **Apache 2.0 License**. See `LICENSE` for more information.
