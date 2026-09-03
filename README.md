# MS Screen Reader v1.23
Mobile Speak Screen Reader — Android project.

## Status
- v1.23: **GitHub Actions build fail fix — `accessibility_service_config.xml` me galat attribute names the.**
  `android:flags` naam ki koi attribute `<accessibility-service>` par hoti hi nahi — sahi
  naam `android:accessibilityFlags` hai. `android:canRequestAccessibilityButton` bhi ek
  real attribute nahi tha; accessibility-button request `accessibilityFlags` ke andar
  `flagRequestAccessibilityButton` flag se hoti hai, alag boolean attribute se nahi. Dono
  fix kiye — AAPT ka "attribute not found" build error is wajah se aa raha tha.
- v1.22: **Accessibility Settings me service sahi tarike se pehchani jaaye.**
  `accessibility_service_config.xml` me `android:isAccessibilityTool="true"`
  (API 31+) add kiya, taaki Settings me yeh ek generic "downloaded
  service" ki jagah asli accessibility tool ke roop me dikhe. Android
  13+ ka "Restricted Settings" (sideloaded APK par pehli baar toggle
  grey rehna) code se bypass nahi ho sakta — user ko ek baar App Info
  > (⋮) menu > "Allow restricted settings" karna hoga. Real-device
  testing abhi bhi pending. See `docs/REMAINING_WORK.md` for full detail.
- v1.21: **6 usability fixes from real-usage feedback (TalkBack-inspired), launcher-conflict and app-list-scroll-stuck deferred.**
  Checkbox → SwitchCompat everywhere (5 settings screens); Cancel button
  added to Main Menu; new "MS Screen Reader" on/off switch on the main
  screen reflecting the real system accessibility-service state
  (`Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES`); volume-key gating
  reviewed (already a single clean gate in `onKeyEvent`, no leftover
  state) and full-shutdown cleanup confirmed in `onDestroy()`; nav-bar
  double-announce fix (`navigationBarButtonLabel()` in
  `MSScreenReaderService` - Back/Home/Recents now announce once, by a
  fixed label matched on resource ID rather than text, immune to
  gesture-nav vs 3-button-nav differences); new vibration/haptic
  feedback feature in `SoundSchemeManager` (`vibrationEnabled` setting,
  default on, independent of the sound-scheme folder). Real-device
  testing still pending for all of it.
- v1.20: **Verbosity: remaining 4 feasible items.** Added
  `countRepeatedSymbolsEnabled`, `speakTextFormattingEnabled`,
  `speakNotificationsWhenScreenOffEnabled`, `speakLettersWithExamplesEnabled`
  to `VerbositySettingsActivity`/`SettingsRepository`. Wired into
  `NodeNavigator` (repeated-symbol collapsing and bold/italic/underline
  detection in `announceableDescription()`, NATO-style letter examples
  in `stepCharacter()`) and `NotificationReader` (screen-off check via
  `PowerManager.isInteractive`). Keyboard echo and pitch-changes-on-
  delete are left out - they need a separate IME hook / delete-key
  detection, outside the current architecture.
- v1.15: **Verbosity settings** (TalkBack-style). New `VerbositySettingsActivity`
  (reachable from both the Main Menu and Detail Settings) with 7 toggles
  and a punctuation-level picker: speak element type, container
  entering/exiting (with optional item count), usage hints, window
  names, element-ID fallback for unlabelled buttons, capital-letter
  announcement, and punctuation spelled out (none/some/all). Wired into
  `NodeNavigator` - `describe()` was split into `rawText()` (stable,
  for stepping/clipboard/remember-focus matching) and
  `announceableDescription()` (the decorated version swipe-navigation
  actually speaks).
- v1.14: **Accessibility volume, separate from music volume**. TTS
  speech and earcons now use `AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY`
  together with the new `flagEnableAccessibilityVolume` service flag,
  which is the same mechanism TalkBack itself uses (confirmed against
  both Android's official accessibility-service guide and TalkBack's
  AOSP source). With both in place, Android automatically routes
  volume-key presses to the dedicated accessibility stream whenever
  this service is speaking/earcon-ing, and back to music/ringer/etc.
  the rest of the time — no key-interception code needed. See
  `TtsManager`'s kdoc.
- v1.13: **Actions dispatch, Main Menu, Reading Granularity settings,
  Remember Focus**. Single-finger gesture dispatch now actually
  consults gesture-launches-app / per-app override / global override
  before falling back to the hardcoded default (see
  `MSScreenReaderService.resolveAction()`). Swipe up-then-right opens
  a new `MainMenuActivity` (screen reader menu); TOGGLE_SPEECH moved to
  swipe right-then-left to make room. New `GranularitySettingsActivity`
  lets the person pick which reading granularities the cycling gesture
  visits. New "remember focus per app" restores accessibility focus to
  the last element used in an app when returning to it (e.g. WhatsApp).
- v1.0: empty skeleton/blueprint.
- v1.1: core accessibility event handling + TTS.
- v1.2: custom sound scheme (user's own folder + sound files per event).
- v1.3: gestures (swipe navigation, TalkBack-style).
- v1.4: notification reading with filtering.
- v1.5: all 8 L-shaped/diagonal swipe gestures wired up (quick
  settings, recent apps, jump to first/last element, suspend/resume
  voice feedback).
- v1.6: **call handling**. Announces incoming calls and
  finished-call duration via TTS, and can answer a ringing call with a
  volume-button press (both togglable from the main screen, with a
  button to grant the required call permissions). Power-button-ends-call
  is intentionally not implemented — Android doesn't let a third-party
  accessibility service intercept the power key at all.

See `docs/REMAINING_WORK.md` for what's implemented vs. pending
(per-app gesture scheme, per-app notification mute UI, remaining
settings UI, etc.).

Real-device testing is required — this has not been run on a physical
device or emulator.
