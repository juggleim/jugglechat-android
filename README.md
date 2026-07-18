# JuggleChat Android — Open-Source Instant Messaging App

[English](README.md) | [简体中文](README.zh-CN.md)

JuggleChat Android is a complete, runnable **Android instant messaging app** built with Java, the JuggleIM SDK, and ZEGO. It is an open-source reference project for developers building a mobile chat app with one-to-one and group messaging, contacts, social feeds, message search, and real-time audio/video calls.

Use this repository to evaluate the JuggleIM Android SDK, learn how production-style chat features fit together, or bootstrap your own Android IM application.

## Feature Preview

<table>
  <tr>
    <td align="center">
      <strong>Messaging, Contacts, and Groups</strong><br>
      <sub>Login · Conversations · Rich messages · Voice messages · Search · Group chat</sub>
    </td>
    <td align="center">
      <strong>Calls, Group Management, and Moments</strong><br>
      <sub>Audio/video calls · Incoming calls · Group settings · Social feed · Favorites</sub>
    </td>
  </tr>
  <tr>
    <td width="50%">
      <img src="images/img.png" alt="JuggleChat Android messaging app screens showing login, conversation list, chat messages, voice messages, contact search, and group chat">
    </td>
    <td width="50%">
      <img src="images/img_1.png" alt="JuggleChat Android IM app screens showing video calls, group management, social moments, favorites, contacts, and incoming calls">
    </td>
  </tr>
</table>

## Why JuggleChat?

- **Runnable end-to-end demo** — follow the flow from authentication to conversations, messages, groups, moments, and calls.
- **Real chat app patterns** — study unread counts, pagination, message actions, session recovery, multi-device login handling, and incoming-call overlays.
- **Integration reference** — see how an Android client connects an IM SDK, an application backend, and ZEGO real-time communication.
- **Readable Java architecture** — a single app module with clear boundaries between UI, domain repositories, SDK integration, HTTP services, and events.
- **A practical starting point** — fork the project for an internal messenger, community chat, customer-support app, or social product prototype.

## Features

### Messaging and conversations

- One-to-one chat and group chat
- Text, image, voice, file, and merged/forwarded messages
- Message replies, reactions, translation, recall, favorites, and pinned messages
- Conversation pagination, unread badges, pinning, and mute settings
- Message history and in-chat search

### Contacts and groups

- Registration, login, session restoration, and multi-device session handling
- Friend requests and contact management
- Group creation, announcements, QR codes, roles, and member management
- Search across friends, groups, and messages

### Social and real-time communication

- Moments/social feed with posts, images, likes, and comments
- One-to-one and multi-party audio/video calls powered by ZEGO
- Incoming and ongoing call overlays
- CameraX and ML Kit barcode/QR code scanning

## Technology Stack

| Area | Technology |
|---|---|
| Platform | Android, Java |
| Android support | minSdk 24, targetSdk 34 |
| Build | Android Gradle Plugin 8.4.0, Gradle 8.6, JDK 17 |
| Instant messaging | JuggleIM Android SDK `1.8.44` |
| Audio/video calls | Juggle call extension `1.8.25`, ZEGO Express `3.17.3` |
| App architecture | Repository layer, SDK facade, EventBus event bridge |
| UI and media | AndroidX, Material Components, Glide, CameraX, ML Kit |
| Networking and data | Retrofit, Gson, Protobuf, Qiniu SDK |
| Security | AndroidX Security Crypto |

## Architecture

JuggleChat keeps SDK callbacks and application UI loosely coupled. `JIMChatCore` is the single facade around the JuggleIM SDK; repositories coordinate domain data, and EventBus distributes connection, conversation, and message updates to the UI.

```text
Android UI
   ├── Domain repositories ── JIMChatCore ── JuggleIM SDK
   ├── HTTP service layer ───────────────── Application backend
   └── Call UI ────────────── Juggle call extension ── ZEGO
```

Main source directory: `app/src/main/java/com/juggle/im/android/`

| Package | Responsibility |
|---|---|
| `app/` | Application shell, authentication screens, settings, and profile |
| `auth/` | Session persistence, authentication guard, and startup routing |
| `chat/` | Conversations, messages, contacts, groups, search, calls, and moments |
| `core/` | JuggleIM SDK facade (`JIMChatCore`) |
| `server/` | HTTP services and data transfer objects |
| `event/` | EventBus event definitions |
| `service/` | Foreground and keep-alive services |
| `utils/`, `widget/` | Shared utilities and custom Android views |

For design decisions, module boundaries, and known implementation details, start with the [project knowledge base](docs/knowledge/index.md).

## Quick Start

### Prerequisites

- Android Studio (latest stable version recommended)
- JDK 17
- Android SDK 34
- An Android device or emulator running Android 7.0 (API 24) or later

### 1. Clone and open the project

Clone this repository, then open the root directory in Android Studio and wait for Gradle sync to finish.

### 2. Configure the services

Update `app/src/main/java/com/juggle/im/android/model/ConfigUtils.java`:

| Field | Description |
|---|---|
| `appKey` | JuggleIM application key |
| `appServerUrl` | Application backend base URL |
| `imServer` | JuggleIM WebSocket server URL |
| `zegoId` | ZEGO AppID used for audio/video calls |

The values committed to this repository are for demonstration and testing only. Use your own services, credentials, and signing configuration before distributing an app. Do not commit production secrets.

### 3. Build and run

Run the `app` configuration from Android Studio, or build a debug APK from the command line:

```bash
./gradlew :app:assembleDebug
```

## Development Commands

| Command | Purpose |
|---|---|
| `./gradlew :app:assembleDebug` | Build a debug APK |
| `./gradlew :app:assembleRelease` | Build a release APK |
| `./gradlew :app:testDebugUnitTest` | Run unit tests |
| `./gradlew :app:lintDebug` | Run Android Lint |

## Frequently Asked Questions

### Is JuggleChat a complete Android chat app or only an SDK sample?

It is a complete client-side reference app. It includes authentication screens, conversations, rich messages, contacts, group management, search, a social feed, and audio/video call UI. A compatible application backend, JuggleIM service, and ZEGO configuration are still required.

### Can I use this project to build my own instant messaging app?

Yes. The project is intended for learning, SDK evaluation, prototyping, and secondary development. Before commercial use, review the licenses and terms of every SDK and service, replace all demo credentials, and complete your own privacy and security review.

### Does the Android chat demo support group messaging and video calls?

Yes. It demonstrates one-to-one and group messaging plus one-to-one and multi-party audio/video calls.

### Is the app written in Kotlin or Java?

The application code is written primarily in Java. This makes the repository useful to Android teams maintaining Java codebases or comparing Java integration patterns for an IM SDK.

### What backend does JuggleChat need?

The client connects to an application HTTP backend for business data, JuggleIM for real-time messaging, and ZEGO for audio/video media. Configure these endpoints and credentials in `ConfigUtils.java`.

## Contributing

Issues and pull requests are welcome. When reporting a bug, include the Android version, device model, reproduction steps, expected result, and relevant logs. Keep changes focused and run the smallest relevant build, unit test, or lint task before submitting a pull request.

If this Android IM project helps you, consider starring the repository. Stars and detailed issue reports help more Android developers discover and improve JuggleChat.

## Security and Production Use

- Replace the example server endpoints, application keys, ZEGO AppID, and signing files.
- Review notification, camera, microphone, media, and foreground-service permissions.
- Add your own privacy policy, data-retention rules, and account-deletion flow where required.
- Verify SDK licensing, service pricing, and regional compliance before commercial deployment.
- Never treat the included demo configuration as a production security baseline.

## Project Scope

JuggleChat Android is maintained as a learning, evaluation, and secondary-development reference. It is not a hosted messaging service and does not replace the backend, operational monitoring, security hardening, or compliance work required by a production chat application.

## License

JuggleChat Android is licensed under the [Apache License 2.0](LICENSE).
