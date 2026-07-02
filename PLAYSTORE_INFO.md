# Play Store Listing Metadata

Use the following text blocks when listing **MD3 GPS Camera** on the Google Play Console.

---

## 1. Title / App Name
`MD3 GPS Camera - Geotag Stamp`

## 2. Short Description (Max 80 characters)
`Premium Material You GPS Camera. Add real-world map watermarks to photos & videos`

## 3. Full Description (Max 4000 characters)
```text
Capture, geotag, and preserve your moments with MD3 GPS Camera! Developed with ❤️ in India by Alas.

MD3 GPS Camera is a premium, fully native Android camera utility designed with Material Design 3 (Material You) guidelines. It dynamically matches your device's accent theme, providing a sleek, modern glassmorphic interface that stands out.

Perfect for land surveys, site inspections, field work, construction logging, traveling, and outdoor photography, MD3 GPS Camera automatically embeds precise geographic details onto your photos and videos.

🌟 KEY FEATURES:

• Keyless Web Map Stamps: Embed a real, highly detailed roadmap or satellite view of your exact location on photos and videos. No API keys or complex setups required!
• Styled Map Themes: Select from Roadmap (Standard), Satellite, Dark Mode (CartoDB Dark Matter), or Minimalist Grey (CartoDB Positron) to match your aesthetic.
• Location Dashboard HUD: A compact and expandable Home card showing live address, latitude/longitude, heading direction, accuracy, and local weather.
• Photo & Video Quality Settings: Configure photo downsampling (Low, Medium, High resolution) and video frame qualities to optimize capture processing speeds.
• Custom Text Overlays: Add site names, survey notes, project IDs, or custom tags directly on the stamp.
• Local Privacy First: All photos and video recordings are compiled locally on your device. No cloud storage, no location tracking, and no data collection.
• Local Media History & Gallery: Inspect your geotagged history, play back watermarked videos, and share directly with colleagues.

PERMISSIONS EXPLAINED:
- ACCESS_FINE_LOCATION: To query precise GPS coordinates (Latitude, Longitude, Altitude, Bearing, Accuracy) and physical address for watermarking.
- CAMERA: To take photos and record video streams.
- RECORD_AUDIO: To capture sound during video recordings.
- WRITE_EXTERNAL_STORAGE (MediaStore): To save files directly into your device's public DCIM directory.

Download MD3 GPS Camera today and start tagging your site photos with premium precision!
```

---

## 4. Google Play Console Setup Guidance

### Core Declaration Tasks:
1. **Target SDK & API**: Target SDK is configured to **API 36 (Android 15)**.
2. **Sensitive Permissions Declaration**:
   - Location Permission Declaration: Explain that `ACCESS_FINE_LOCATION` is needed to geotag photos/videos in real-time.
   - Camera Permission Declaration: Standard camera usage.
3. **Data Safety Section answers**:
   - **Does your app collect or share any of the required user data types?** -> **No**. (All data is processed strictly locally. Coordinate data sent to CartoDB/Google Map tile servers is unauthenticated and solely used to download standard layout graphic tiles).
   - **Are all of the user data collected by your app encrypted in transit?** -> **Yes** (tile fetching is performed over secure HTTPS).
   - **Do you provide a way for users to request that their data be deleted?** -> **Yes** (a "Reset Application Data" action is provided in app settings to wipe the database).
4. **Privacy Policy URL**:
   - Google requires a public URL for your Privacy Policy. You can host the text inside `PRIVACY_POLICY.md` on a free platform like GitHub Pages, GitBook, or Google Sites, and input that URL in the console.
