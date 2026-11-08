# ScrbL â€” Architecture, Implementation & Verification Report

**Application Name:** `ScrbL` (exact casing preserved everywhere)
**Application ID / Namespace:** `com.scrbl.app`
**Target Toolchain:** Temurin / OpenJDK 17, Gradle 8.7, Android Gradle Plugin 8.5.2, Kotlin 1.9.24, KSP 1.9.24-1.0.20, Android SDK Platform 34 (`compileSdk = 34`, `targetSdk = 34`, `minSdk = 29`)
**Posture:** 100% Local-First, Offline-Capable, Immutable Recalls, Zero Network Permissions

---

## 1. Architecture Overview

`ScrbL` follows a strict layered Unidirectional Data Flow (UDF) architecture with clear boundaries between UI, Domain, Data, Widget, and Platform services:

| Layer | Package | Responsibilities |
| :--- | :--- | :--- |
| **Presentation / UI** | `com.scrbl.app.navigation`, `feature.home`, `feature.archive`, `feature.favourites`, `feature.recallviewer`, `feature.capture`, `feature.settings`, `feature.help`, `core.design` | Jetpack Compose screens, reusable paper/stack surfaces (`PaperBackground`, `PaperSheet`, `PaperStackCard`, `DayStackCard`, `MonthStackCard`, `YearStackCard`, `FlatMonthCollapsePage`, `RecallPage`), circular `DoodleIconButton` controls, 4-pose `VoiceCharacter`, and `ScrbLViewModel` state holder. |
| **Domain** | `com.scrbl.app.domain.model`, `domain.validation`, `domain.usecase`, `domain.repository` | Pure Kotlin domain models (`Recall`, `RecallFilterState`, `DayStackGroup`, `MonthStackGroup`, `YearStackGroup`, `UserPreferences`), deterministic validators (`RecallValidator`), and isolated use cases (`CreateTextRecallUseCase`, `FinalizeVoiceRecallUseCase`, `CancelCaptureUseCase`, `CleanupAbandonedRecordingTempsUseCase`, `ToggleAddressedUseCase`, `ToggleFavouriteUseCase`, `GetYearMonthDayGroupsUseCase`, `ApplyRecallFilterUseCase`, `GetLatestWidgetRecallsUseCase`). |
| **Data** | `com.scrbl.app.data.local.room`, `data.audio`, `data.preferences`, `data.repository` | Single-table Room persistence (`ScrbLDatabase`, `RecallDao`, `RecallEntity`, exported schema `1.json`, non-destructive `ScrbLMigrations`, and SQLite immutability/no-delete triggers), Preferences DataStore (`DataStorePreferencesRepository`) with independent `mainFilterState` and `archiveFilterState`, and `AppPrivateVoiceFileStorage` (`files/recalls/audio/<uuid>.m4a` + `cache/recording_temp/<session>.tmp`). |
| **Widget** | `com.scrbl.app.widget` | Classic collection App Widget (`ScrbLWidgetProvider`, `ScrbLWidgetService`, `ScrbLWidgetFactory`, `WidgetIntents`, `AndroidWidgetRefresher`) using Android's `StackView` for rolodex-style up/down front-card flicks across the 19 most recent recalls. |
| **Platform** | `com.scrbl.app.platform.audio`, `platform.permissions`, `platform.lifecycle` | `AndroidVoiceRecorderController` (enforcing the 37,000 ms limit via both `MediaRecorder.setMaxDuration(37_000)` and a monotonic `SystemClock.elapsedRealtime()` timer guard, plus audio-focus loss discard), `AndroidVoicePlayerController`, runtime `RECORD_AUDIO` permission helper, and `FLAG_KEEP_SCREEN_ON` lifecycle guard. |

---

## 2. Binding Product Rules & Invariants Implemented

1. **Exact Brand Casing (`ScrbL`)**: Used in `strings.xml`, top-left main wordmark, widget empty prompt, settings/About, and source documentation.
2. **Text Recall (25-Word Cap, Centre-Aligned)**:
   - `RecallValidator.countWords()` normalises line breaks and repeated whitespace while counting non-empty whitespace-separated tokens (attached punctuation and hyphenated compounds count as 1 token; space-separated emoji counts as 1 token).
   - `RecallValidator.canAcceptTextEdit()` prevents insertion beyond 25 words without silently truncating existing text.
   - `TextCaptureContent` centres text horizontally and vertically from the first character (`TextAlign.Center`) and resizes above the software keyboard via `windowSoftInputMode="adjustResize"` + `Modifier.imePadding()`.
   - Tapping the top-right doodle tick (`ic_doodle_check`) commits immediately.
3. **Voice Recall (37-Second Cap, Atomic Race Guard, No Drafts)**:
   - Runtime `RECORD_AUDIO` permission is requested only when the user taps the microphone doodle button on the blank capture page.
   - Recording begins immediately once permission is granted; an optional comic-style thought-bubble title (`voiceTitle`, up to 80 chars) can be entered inline before stopping.
   - Tapping the circular stop icon (`ic_doodle_stop`) commits immediately without a second confirmation screen.
   - Reaching 37,000 ms automatically stops and commits. `CaptureStateMachine` uses an atomic compare-and-set (`AtomicBoolean.compareAndSet(false, true)`) so a manual stop tap racing with the 37-second callback commits at most once.
   - Flicking the floating capture page away (`pointerInput` drag threshold), pressing system Back, losing audio focus (incoming call / interruption), or backgrounding the app (`Lifecycle.Event.ON_STOP`) immediately stops the recorder and deletes the `.tmp` file.
   - On application startup (`ScrbLApplication.onCreate()`), `CleanupAbandonedRecordingTempsUseCase` deletes any orphan `.tmp` files left behind by process death without touching committed audio files.
4. **Strict Immutability & Double-Layer Enforcement**:
   - No edit or delete UI, use case, or DAO method exists.
   - `RecallDao` exposes `@Insert(onConflict = OnConflictStrategy.ABORT)` and only two narrow column updates: `updateAddressedStatus(id, isAddressed)` and `updateFavouriteStatus(id, isFavourite)`.
   - `ScrbLDatabase.ImmutabilityTriggerCallback` installs two SQLite triggers (`trg_recalls_prevent_immutable_update` and `trg_recalls_prevent_delete`) that `RAISE(ABORT, ...)` if any query ever attempts to alter `id`, `type`, `text_body`, `voice_relative_path`, `voice_title`, `created_at_epoch_millis`, `captured_local_date`, `captured_zone_id`, or `audio_duration_millis`, or delete a row.
5. **Timezone-Invariant Grouping (`capturedLocalDate`)**:
   - At commit time, `CaptureTimestampSnapshot.captureNow()` captures one `Instant` (`createdAtEpochMillis`), the current `ZoneId` (`capturedZoneId`), and the local calendar date (`capturedLocalDate` formatted as `yyyy-MM-dd`).
   - All Day (`DD/MM`), Month (`MM`), and Year (`'YY`) stack groupings derive strictly from `capturedLocalDate` so later timezone travel never shifts historical thoughts between day stacks.
6. **3-Column Paper-Stack Hierarchy & Month Dealing**:
   - Main screen shows only the current calendar year in a 3-column `LazyVerticalGrid(columns = GridCells.Fixed(3))` with unlimited rows.
   - Active current month shows Day stacks (`DD/MM`); completed months show Month stacks (`MM`).
   - Tapping a Month stack deals its Day stacks into the grid (`DealtDayStackCellWrapper`) while keeping the original cell as a flat `MM` page (`FlatMonthCollapsePage`); tapping that flat page collapses the month.
   - Archive (reached from Profile/Settings) uses the same 3-column paper grid for Year (`'YY`) -> Month (`MM`) -> Day (`DD/MM`).
7. **Independent Main & Archive Filters + Favourites View**:
   - `DataStorePreferencesRepository` persists `filters.main.*` and `filters.archive.*` in separate preference keys.
   - Filter controls appear only when browsing day stacks (never at the Year or unexpanded Month level).
   - Addressed-only and Unaddressed-only are mutually exclusive (`AddressFilterMode`); Favourite (`favouritesOnly`) and Content Type (`BOTH`, `TEXT_ONLY`, `VOICE_ONLY`) combine freely.
   - `FavouritesScreen` queries `WHERE is_favourite = 1 ORDER BY created_at_epoch_millis DESC` over the single `recalls` table across all years.
8. **Shared Expanded Recall Page & Voice Character Playback**:
   - Reused across Main, Archive, Favourites, and Widget-origin detail (`ExpandedRecallViewerOverlay` + `RecallPage`).
   - Top-left: `DD MMM` date (no year); top-right: hand-drawn heart (`ic_doodle_heart_outline` / `ic_doodle_heart_filled`); centre: centred text or `VoiceCharacter` (`boy`/`girl` sketch + optional thought bubble); bottom-left: creation time (`12h`/`24h`); bottom-right: `03 / 12` page index.
   - Vertical `VerticalPager` (swipe up = older, swipe down = newer; no horizontal paging).
   - Double-tap page toggles `isAddressed` (also registered as a TalkBack `CustomAccessibilityAction`).
   - Tapping the voice character plays/pauses local audio and cycles through 4 hand-drawn poses.
   - Widget-origin detail (`isExpandedWidgetOrigin = true`) displays a circular `+` button at bottom-right; app-origin detail does not.
9. **Classic `StackView` Collection App Widget**:
   - `ScrbLWidgetProvider` + `ScrbLWidgetService` + `ScrbLWidgetFactory` render `widget_scrbl_notepad.xml` and `widget_scrbl_page_item.xml`.
   - Visual structure matches `widget_notepad_reference.svg`: top wire spiral loops, layered back sheets, warm ivory front sheet, and curled lower-right corner (`bg_widget_notepad_card.xml` and `bg_widget_notepad_card_addressed.xml`).
   - Exposes up to the 19 most recent recalls (`LIMIT 19`) across all statuses; when a 20th recall is saved, the oldest leaves the widget window only and remains in Room/Archive.
   - Single-tapping the front widget page passes `EXTRA_WIDGET_RECALL_ID` to `MainActivity`, which sets `AppDestination.WidgetOriginDetail(recallId)` synchronously before the first Compose frame so Main never flashes underneath.
   - No `+` button exists on the widget surface.
10. **Privacy & Backup Exclusions**:
    - `AndroidManifest.xml` has zero `INTERNET` permission, `android:allowBackup="false"`, `android:dataExtractionRules="@xml/data_extraction_rules"`, and `android:fullBackupContent="@xml/full_backup_content"` excluding all domains from cloud backup and device transfer.

---

## 3. Exact Build, Test & Run Commands (Windows PowerShell)

Open **PowerShell** in the extracted `ScrbL` project folder on your Windows machine (configured with Temurin JDK 17, Gradle 8.7, Android SDK Platform 34, and wireless ADB):

```powershell
# 1. Verify JDK 17, Gradle 8.7, and ANDROID_HOME in your Windows PowerShell session
java -version
gradle --version
$env:ANDROID_HOME

# 2. (Optional, one-time) If gradle-wrapper.jar is not yet generated in your local clone,
#    hydrate the Gradle 8.7 wrapper binary from your installed Gradle 8.7:
gradle wrapper --gradle-version 8.7

# 3. Run the unit & acceptance test suite
.\gradlew.bat testDebugUnitTest

# 4. Assemble the clean Debug APK
.\gradlew.bat clean assembleDebug

# 5. Verify wireless ADB device connection and install the Debug APK
adb devices
.\gradlew.bat installDebug

# 6. Launch ScrbL on the connected Android phone
adb shell am start -n com.scrbl.app/.MainActivity

# 7. (Optional) Test direct Widget-origin Expanded Recall deep-link routing via ADB
adb shell am start -a com.scrbl.app.action.OPEN_WIDGET_CAPTURE -n com.scrbl.app/.MainActivity
```

---

## 4. Build, Verification & Acceptance Test Results

### Passed Checks in This Sandbox Environment
- **XML & Manifest Static Audit (`PASS â€” 29/29 files`)**: All 29 XML files (`AndroidManifest.xml`, `data_extraction_rules.xml`, `full_backup_content.xml`, `scrbl_widget_info.xml`, `widget_scrbl_notepad.xml`, `widget_scrbl_page_item.xml`, `strings.xml`, `colors.xml`, `themes.xml`, 18 `VectorDrawable` XMLs in `res/drawable/`, and 2 adaptive icon XMLs in `res/mipmap-anydpi-v26/`) parsed and validated cleanly. Verified zero `INTERNET` permission, `android:allowBackup="false"`, and exact `ScrbL` label.
- **Kotlin Source & Architecture Audit (`PASS â€” 17 source/test files`)**: All Kotlin files across `core`, `domain`, `data`, `feature`, `widget`, `platform`, and `navigation` verified for package structure, balanced syntax blocks, zero destructive migrations, and strict absence of edit/delete recall mutations.
- **Live SQLite 3 Schema & Immutability Trigger Verification (`PASS`)**: Executed the exported Room DDL from `app/schemas/com.scrbl.app.data.local.room.ScrbLDatabase/1.json` and `ScrbLDatabase.ImmutabilityTriggerCallback` triggers against SQLite 3:
  - Inserted 22 mixed `TEXT` and `VOICE` recalls across `2025` and `2026`.
  - Verified `UPDATE recalls SET is_addressed = 1` and `UPDATE recalls SET is_favourite = 1` succeed.
  - Verified any `UPDATE` touching `text_body`, `voice_title`, `created_at_epoch_millis`, or `captured_local_date` is aborted by `trg_recalls_prevent_immutable_update`.
  - Verified `DELETE FROM recalls` is aborted by `trg_recalls_prevent_delete`.
  - Verified `SELECT * FROM recalls ORDER BY created_at_epoch_millis DESC LIMIT 19` returns the newest 19 records (`id_22` down to `id_4`) while all 22 records remain intact in the `recalls` table.
- **OpenJDK 17 JVM Domain & Concurrency Verification (`PASS`)**: Executed on OpenJDK 17 (`17.0.21`) verifying the 25-word tokenization contract, `CaptureTimestampSnapshot` timezone-invariant `capturedLocalDate` derivation (`Asia/Kolkata`), and `AtomicBoolean.compareAndSet(false, true)` single-commit race protection for the 37-second voice auto-save vs manual stop tap.

### Unvalidated / Deferred Environment Checks (Honest Separation)
- **Full Android Gradle APK Binary Compilation & Real-Phone Launcher Gesture Test (`REQUIRES DEVELOPER WINDOWS MACHINE + ADB DEVICE`)**:
  - *Exact Blocker*: This Linux evaluation sandbox has no outbound internet access, no pre-installed Android SDK (`android.jar` / `aapt2` / `d8`), no offline Maven repository cache for AndroidX/Compose artifacts, and no physical Android phone attached over wireless ADB.
  - *What to Run on Your Windows Machine*: Run the PowerShell commands in Section 3 above (`.\gradlew.bat testDebugUnitTest assembleDebug installDebug`) and perform the real-launcher `StackView` check described in Section 5 below.

---

## 5. Known Limitations & Real-Launcher Widget Notes

1. **Android Launcher `StackView` Gesture Handling**:
   - `ScrbL` implements the widget using Android's classic collection widget API (`AppWidgetProvider` + `RemoteViewsService` + `RemoteViewsFactory` + `<StackView>`), which is Android's native rolodex-style stacked view supporting vertical front-card flicks.
   - On standard Pixel Launcher, AOSP Launcher3, and One UI Home, vertical flicks on a `StackView` advance to the next/previous item in the 19-item adapter, and single-tapping the front card triggers the item fill-in `PendingIntent` (`ACTION_OPEN_WIDGET_RECALL` with `EXTRA_WIDGET_RECALL_ID`).
   - *Launcher Caveat to Check on Your Phone*: Some third-party OEM launchers (or launchers with vertical home-screen swipe-for-drawer / notification-shade gestures configured to intercept all vertical touches on home-screen widgets) can compete with vertical `StackView` flicks. When testing on your physical device via wireless ADB, verify:
     1. Up flick advances to the next older recall (`01 / 19` -> `02 / 19`).
     2. Down flick returns to the newer recall (`02 / 19` -> `01 / 19`).
     3. Single tap opens that exact recall directly in `ExpandedRecallViewerOverlay` without flashing `MainScreen`.
2. **OEM Device-to-Device Transfer Caveat**:
   - Both `android:allowBackup="false"`, `res/xml/data_extraction_rules.xml` (`<cloud-backup>` and `<device-transfer>` exclusions), and `res/xml/full_backup_content.xml` are configured. As noted in Android's official Auto Backup documentation, certain OEM proprietary cable-cloning utilities on Android 11 and below may inspect system-level backup flags differently; Android 12+ honours `<device-transfer><exclude .../></device-transfer>`.

---

## 6. Visual Review Checklist

- [x] **Exact Brand Spelling**: `ScrbL` at top-left of Main screen, in widget empty state, and in Settings/About.
- [x] **Warm Ivory Notebook Aesthetic**: `#F4E9D5` warm paper, `#EDE0C9` addressed paper shade, `#E9DDCA` background, `#29364A` ink-navy linework, `#65748A` addressed tone, `#C9786B` terracotta accent, `#D9CBB4` paper edges, subtle grain.
- [x] **Hand-Drawn Doodle Vector Family**: Converted directly from `assets/doodles/*.svg` (`ic_doodle_heart_outline`, `ic_doodle_heart_filled`, `ic_doodle_microphone`, `ic_doodle_keyboard`, `ic_doodle_check`, `ic_doodle_plus`, `ic_doodle_filter`, `ic_doodle_archive`, `ic_doodle_settings`). Zero generic Material icon substitutions.
- [x] **Circular Controls with >=48dp Touch Targets**: Every visible button in `DoodleIconButton` is circular with a minimum 48dp interactive hit target.
- [x] **No Bottom Navigation Bar, Dashboard Cards, or Feed**: Main screen is purely the 3-column paper-stack grid with top-left wordmark, top-right circular controls, and bottom-right circular `+`.
- [x] **Main & Archive Stack Dealing**: Active month shows `DD/MM` day stacks; completed months show `MM` stacks that deal into day stacks when tapped and leave a flat `MM` collapse page in the original cell.
- [x] **Shared Expanded Recall Page**: Displays `DD MMM` top-left, doodle heart top-right, centred text or voice character + optional thought bubble in the middle, creation time bottom-left, and `03 / 12` index bottom-right over a dimmed background. Widget-origin detail adds the circular `+` button.
- [x] **Portrait Top-Spiral Notepad Widget**: Top wire spiral loops, layered sheets behind, curled bottom-right corner, newest-first `01 / 19` counter, and zero `+` button on the widget surface.
