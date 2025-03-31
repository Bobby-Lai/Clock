# ViewSonic Android Assignment

This project is an Android application designed to demonstrate a multi-timezone clock display with floating window functionality, built with modern Android development practices.

## Key Features

### 1. Multi-Timezone Clock Display
- Displays multiple timezones with real-time updates.
- Implements efficient API polling with local time offset caching to minimize network calls.
- Supports configurable refresh rates: 1, 5, or 10 minutes.
- Supports English and Traditional Chinese (zh-TW) localization.

### 2. Floating Window Functionality
- Allows displaying up to 5 timezone clocks as floating windows.
- Implements draggable floating windows with close functionality.

## Technical Implementation

### Architecture
- Follows **MVVM (Model-View-ViewModel)** architecture with **Jetpack Compose** for the UI.
- Uses **Kotlin Coroutines** and **Flow** for asynchronous operations.
- Implements the **Repository pattern** for data management.

### Key Components

#### `ClockViewModel`
- Manages time data, refresh rates, and language settings.
- Implements time offset caching to reduce API calls.
- Handles automatic hourly calibration with a remote time server.

#### `TimeOffsetManager`
- Calculates and manages time offsets between the remote API and the local device.
- Provides fallback mechanisms for network failures.

#### `FloatingClockService`
- A foreground service for displaying floating clock windows.
- Handles multiple floating windows with touch detection for dragging and interaction.

#### `LanguageManager`
- Manages application language settings using **SharedPreferences**.
- Ensures consistent localization across configuration changes.

#### `PermissionCheckActivity`
- Handles system overlay permission requests.
- Implements a user-friendly permission flow.
- Ensures proper functionality of floating windows.
