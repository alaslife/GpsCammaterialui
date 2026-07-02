# MD3 GPS Camera

A beautiful, premium, and fully native Android GPS Camera application built using **Jetpack Compose**, **CameraX**, and **Material Design 3 (Material You)**.

Designed and developed with ❤️ in India by **dev Alas**.

---

## 🌟 Key Features

- **Material You Design**: Adheres fully to Material Design 3 guidelines featuring dynamic color themes (monet support), custom glassmorphism containers, sleek bottom navigation, and rounded material sheets.
- **Keyless Web Maps Watermark**: Integrates a free, high-performance web mapping engine that stitches coordinates into real-world geographic roadmaps (Google Maps tile styling & CartoDB) without needing API keys or complex billing accounts.
- **Multiple Map Themes**: Customize the watermark stamp maps with standard **Roadmap**, high-detail **Satellite**, premium **Dark Mode** (CartoDB Dark Matter), or **Minimal Grey** (CartoDB Positron) views.
- **Location Detail HUD**: Compact, expandable dashboard on the Home screen displaying address, coordinates, heading direction, accuracy, and live temperature/weather.
- **Camera & Video Capabilities**: Capture photos and record high-quality videos with customizable resolution options.
- **Flexible Photo/Video Settings**: Customize JPEG quality (Low/Medium/High) and downsampling options to optimize capture and processing speed.
- **Built-in Local Gallery**: View captured geotagged photos and video playbacks directly within the local gallery with historical metadata inspection.

---

## 🛠️ Technology Stack

- **UI Framework**: Jetpack Compose (1.6+)
- **Architecture**: MVVM (Model-View-ViewModel) with Kotlin Coroutines & StateFlow
- **Jetpack Libraries**: CameraX (Core, Camera2, Lifecycle, Video), Compose Navigation, Room (local history tracking), DataStore Preferences
- **Map Tile API**: Public unauthenticated slippy map tiles (Google Maps web server + CartoDB)
- **Local Storage**: Saved directly to device public MediaStore (`DCIM/Pictures`)

---

## 🚀 Local Build & Run

### Prerequisites
- Android Studio Koala / Ladybug or newer
- JDK 17 (pre-bundled with Android Studio as JetBrains Runtime)
- Android SDK (API 34+)

### Steps to build from CLI
1. Open the project in terminal.
2. Build the debug APK using:
   ```cmd
   gradlew.bat assembleDebug
   ```
3. The built APK will be generated at:
   `app/build/outputs/apk/debug/app-debug.apk`

---

## 📜 Licenses & Attributions

- **Map Tiles**:
  - Google Maps Standard/Satellite tile server: strictly used for local viewport rendering as per public web terms.
  - CartoDB Dark Matter & Positron: Map tiles by [CARTO](https://carto.com/basemaps), under [CC BY 3.0](https://creativecommons.org/licenses/by/3.0/). Data by [OpenStreetMap](https://www.openstreetmap.org/copyright), under ODbL.
  
---

*Made with love in India • dev Alas*
