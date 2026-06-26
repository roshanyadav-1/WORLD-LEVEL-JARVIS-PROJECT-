# 🤖 HYBRID AUTOMATION BLUEPRINT: THE ULTRA-ROBUST AGENT ENGINE

Auto-clickers (Accessibility taps on indices) are highly brittle. When an app updates its interface, background colors, bounds, or layout structures, indices shift, causing traditional layout-dependent agents to fail.

To solve this, we define the **Hybrid Automation Engine (HAE)** for A.R.I.S. This architecture prioritizes **Native System Controls, App Actions, and Deep Links** first. Accessibility taps (Screen Interaction) are preserved strictly as a **graceful fallback mechanism** when API-level/Intent integration is missing.

---

## 🗺️ 1. Architecture Overview (The Hybrid Flow)

```
                       [ USER REQUEST ]
                              │
                              ▼
                      [ THINKING PHASE ]
             (Identify Intent, Activity, & Action)
                              │
             ┌────────────────┴────────────────┐
             ▼                                 ▼
   [ SPECIFIC INTENT AVAILABLE ]    [ NO SPECIAL INTENT IN CATALOG ]
             │                                 │
             ▼                                 ▼
   [ Is Target App Installed? ]        [ Run Fallback Screen Sensing ]
       │              │                        │
       ├─► (Yes)      └─► (No)                 ▼
       │                  │            [ Detect Interactive Indices ]
       ▼                  ▼                    │
 [ Launch Intent/ ]  [ Launch Web-Link/ ]      ▼
 [   Deep Link    ]  [ Browser Fallback ]  [ Perform Accessibility Taps ]
       │                  │                    │
       ├───────────────────────────────────────┤
       ▼
 [ Verify State / Refresh Screen State ]
```

---

## 🚀 2. Universal Deep Links & Intent Registry

We map popular apps to universal Android Intents and Deep Links to bypass multi-step UI navigations:

### A. YouTube Automation
* **Traditional Clicker Path**: Open YouTube -> Tap Search icon -> Type query -> Tap enter.
* **HAE Native Intent Path**: 
  * Direct Search: `vnd.youtube://results?search_query={query}` or system `android.intent.action.SEARCH` with package `com.google.android.youtube`.
  * Play Video Direct: `vnd.youtube:{video_id}`.

### B. Spotify Automation
* **Traditional Clicker Path**: Open Spotify -> Tap Search tab -> Tap search bar -> Type artist/song -> Tap play.
* **HAE Native Intent Path**:
  * Direct Search/Browse: `spotify:search:{query}` or launch search intent.
  * Direct Play: `spotify:track:{track_id}` or `spotify:playlist:{playlist_id}`.

### C. Google Maps & Geolocation
* **Traditional Clicker Path**: Open Maps -> Find search bar -> Type location -> Tap Navigate.
* **HAE Native Intent Path**:
  * Navigate: `google.navigation:q={latitude},{longitude}` or `google.navigation:q={address}`.
  * Search/Pin: `geo:0,0?q={address}` (supported via `OpenMaps` intent).

### D. System Settings Toggles
* **Traditional Clicker Path**: Open Settings -> Tap Network -> Tap WiFi -> Direct Toggle -> Go back.
* **HAE Native Settings Intent Path**:
  * Direct Settings Actions: 
    * WiFi Settings: `Settings.ACTION_WIFI_SETTINGS`
    * Bluetooth Settings: `Settings.ACTION_BLUETOOTH_SETTINGS`
    * Battery/Power: `Settings.ACTION_BATTERY_SAVING_SETTINGS`

---

## 🛠️ 3. Concrete Implementation Blueprint inside A.R.I.S

We implement/register brand new specialized native handlers to make this happen.

### I. `WebSearchIntent`
Allows searching Google/the web directly without opening Chrome, finding the input element, typing, and clicking enter.

```kotlin
// com.blurr.voice.intents.impl.WebSearchIntent
class WebSearchIntent : AppIntent {
    override val name: String = "WebSearch"
    override fun description(): String = "Perform a direct web search on Google/the default search engine."
    override fun parametersSpec(): List<ParameterSpec> = listOf(
        ParameterSpec("query", "string", true, "The text to search for.")
    )
    override fun buildIntent(context: Context, params: Map<String, Any?>): Intent? {
        val query = params["query"]?.toString()?.trim() ?: return null
        if (query.isEmpty()) return null
        return Intent(Intent.ACTION_WEB_SEARCH).apply {
            putExtra(android.app.SearchManager.QUERY, query)
        }
    }
}
```

### II. `OpenAppSettingsIntent`
Instantly opens settings for any app to let the agent modify app permissions or force stop, avoiding fragile UI navigations in Android Settings.

```kotlin
// com.blurr.voice.intents.impl.OpenAppSettingsIntent
class OpenAppSettingsIntent : AppIntent {
    override val name: String = "OpenAppSettings"
    override fun description(): String = "Directly open system Settings page for a specified application package."
    override fun parametersSpec(): List<ParameterSpec> = listOf(
        ParameterSpec("package_name", "string", true, "The package name of the app (e.g., 'com.instagram.android').")
    )
    override fun buildIntent(context: Context, params: Map<String, Any?>): Intent? {
        val packageName = params["package_name"]?.toString()?.trim() ?: return null
        if (packageName.isEmpty()) return null
        return Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
        }
    }
}
```

### III. `YouTubeSearchIntent`
Allows searching or playing the requested query instantly on YouTube without relying on multi-step UI elements.
```kotlin
// com.blurr.voice.intents.impl.YouTubeSearchIntent
class YouTubeSearchIntent : AppIntent {
    override val name: String = "YouTubeSearch"
    // Opens vnd.youtube://results?search_query=... with browser web fallback
}
```

### IV. `SpotifySearchIntent`
Enables direct Spotify track/artist searching to bypass navigation menus completely.
```kotlin
// com.blurr.voice.intents.impl.SpotifySearchIntent
class SpotifySearchIntent : AppIntent {
    override val name: String = "SpotifySearch"
    // Launches spotify:search:... with web fallback
}
```

### V. `SystemSettingsIntent`
Enables jumping straight to system panels like Wi-Fi, Bluetooth, battery saver, etc., with perfect precision.
```kotlin
// com.blurr.voice.intents.impl.SystemSettingsIntent
class SystemSettingsIntent : AppIntent {
    override val name: String = "SystemSettings"
    // maps wifi, bluetooth, display, battery, accessibility, etc.
}
```

### VI. `PlayStoreIntent` (New)
Allows opening Google Play Store directly to search for any app or navigate to a target app details page directly, bypassing multi-step interactive screen steps.
```kotlin
// com.blurr.voice.intents.impl.PlayStoreIntent
class PlayStoreIntent : AppIntent {
    override val name: String = "PlayStore"
    // Opens market://search?q=... or market://details?id=... with direct web browser fallbacks
}
```

### VII. `WhatsAppIntent` (New)
Provides precise deep linking to open WhatsApp straight into a chat thread with a target phone number, option to auto-prefill a text message, or open WhatsApp's launcher generally.
```kotlin
// com.blurr.voice.intents.impl.WhatsAppIntent
class WhatsAppIntent : AppIntent {
    override val name: String = "WhatsApp"
    // Opens whatsapp://send?phone=...&text=... supporting dynamic sanitization of phone formatting with universal web fallbacks
}
```

### VIII. `AddCalendarEventIntent` (New)
Provides calendar integration to directly schedule events, titles, descriptions, locations, and flexible start/end datetime values.
```kotlin
// com.blurr.voice.intents.impl.AddCalendarEventIntent
class AddCalendarEventIntent : AppIntent {
    override val name: String = "AddCalendarEvent"
    // Inserts dynamic events via CalendarContract.Events with robust datetime parsing support
}
```

### IX. `CameraIntent` (New)
Enables opening the device's native camera immediately to capture a picture or capture a video.
```kotlin
// com.blurr.voice.intents.impl.CameraIntent
class CameraIntent : AppIntent {
    override val name: String = "Camera"
    // Opens camera/video using MediaStore still recording configurations instantly
}
```

---

## 🛠️ 4. System Improvements & Bug Fixes

We do not just add new code; we fix underlying issues to keep the engine reliable:

* **EmailCompose Parameter Fix**: Fixed a native crash/break where the `EXTRA_EMAIL` intent extra was populated with a raw state string starting with `"mailto:"` (e.g. `arrayOf("mailto:user@example.com")`), which breaks default mail clients on devices. Recast it to support true, comma-separated plain text email strings correctly mapped to `Intent.EXTRA_EMAIL`.

---

## 📈 5. Fallback Execution Matrix (When things fail)

The Executor implements a robust Try-Catch fallback sequence:

| Requested Action | Tier 1 (Native) | Tier 2 (Direct Deep Link) | Tier 3 (Accessibility Clicker Fallback) |
| :--- | :--- | :--- | :--- |
| **Send WhatsApp** | Intent with target phone | Deep link `api.whatsapp.com` | Open contact -> Simulated clicks |
| **Play Music** | Spotify custom Intent API | Open `spotify:search` URI | Open Spotify -> Screen Interaction |
| **Set Alarm** | `AlarmClock.ACTION_SET_ALARM` | - | Open clock app -> Swipe & select hour |
| **Search Video** | `vnd.youtube` Search Intent | Open YouTube Web URL | Open YouTube app -> Tap element [Search] |
| **Toggle System Setting** | Directly writing Secure Settings (if permitted) | System Settings deep intent (e.g., `ACTION_WIFI_SETTINGS`) | Swipe down status bar -> Multiple clicker taps |

---

## 📝 5. Model System Instructions update

We train the model to be **Intent-First**. We inject exact guidelines in the Prompt:
1. "Before trying to find search boxes or buttons, look at the `<intents_catalog>`."
2. "If a direct intent like `SendSMS`, `SetAlarm`, `WebSearch`, or `OpenMaps` matches the user's intent, you **MUST** use it."
3. "Only fallback to physical tapping (`tap_element`) if no native intent can perform the task."
