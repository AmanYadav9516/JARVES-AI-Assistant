# 🚀 JARVES AI - Voice-Controlled Pro AI Assistant for Android

**JARVES** is a powerful hands-free AI Phone Assistant for Android (14, 15, 16, 17 support) built to perform daily smartphone automation using voice commands in both **Hindi** and **English**. Powered by Google Gemini API integration and offline intelligent intent execution.

---

## 📥 Direct APK Download Link

Anyone can download and install the compiled APK file directly on their Android phone:

👉 **[Download JARVES.apk (v1.0.0)](https://github.com/AmanYadav9516/JARVES-AI-Assistant/releases/download/v1.0.0/JARVES.apk)**

---

## 🌟 Key Features

- **🗣️ Dual Language Support**: Understands Hindi, English, and Hinglish ("Rahul ko call lagao", "Camera kholo", "Remind me after 24 minutes to buy snacks").
- **⚡ Multitask Task Queue**: Queue multiple voice commands or delayed tasks; JARVES processes them sequentially.
- **🔔 Foreground Notification Bar**: Real-time status notification bar showing active task, pending tasks, and one-tap delete button.
- **🗣️ Wake-Word ("JARVES")**: Runs as a background service listening for hotwords or mic triggers when screen is active.
- **🎨 Dark Theme UI**: Sleek futuristic dark design with status orb, mic trigger, and Gemini API key configuration.

---

## 📱 Supported Voice Commands

| Category | Example Voice Commands (Hindi / English) |
|---|---|
| **Calls** | *"Rahul ko call lagao"*, *"Call Mom"* |
| **SMS** | *"Send text message to mom I am coming in 2 hours"* |
| **Camera** | *"Camera kholo"*, *"Photo khicho"* |
| **Apps** | *"WhatsApp kholo"*, *"YouTube par Arijit Singh ke gane chalao"* |
| **Maps** | *"Maps me Jaipur ka rasta dikhao"* |
| **Alarms & Reminders** | *"Alarm 6 baje ka laga do"*, *"Remind me after 24 minutes to buy snacks"* |
| **System** | *"Flashlight on করো"*, *"Wi-Fi off करो"*, *"Delete my calling task"* |

---

## 🛠️ Tech Stack & Architecture

- **Language**: Kotlin 1.9
- **Platform**: Android SDK 26 to 36 (Android 8.0 to Android 17)
- **AI Engine**: Google Gemini REST API + Intelligent Rule Parser Fallback
- **Service**: Android Foreground Service (`microphone` & `specialUse`)

---

## 📲 How to Install

1. Download **[JARVES.apk](https://github.com/AmanYadav9516/JARVES-AI-Assistant/releases/download/v1.0.0/JARVES.apk)** on your Android phone.
2. Tap the downloaded file to install.
3. Allow necessary permissions (Microphone, Contacts, Calls, SMS, Camera, Notifications).
4. (Optional) Enter your Google Gemini API Key in the top right `API Key` button for enhanced AI comprehension.
