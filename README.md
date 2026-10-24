# 1 MESSAGES

A native Android SMS app that behaves like a real messaging app rather than a demo: it reads
and writes Android's own SMS database through the `ROLE_SMS` default-messaging role, sends
multipart SMS through `SmsManager`, receives `SMS_DELIVER` broadcasts, reports delivery state
only when the platform confirms it, and keeps message bodies out of its own storage.

Kotlin · Jetpack Compose (Material 3) · MVVM · Hilt · Room · DataStore · Navigation Compose ·
WorkManager · Coroutines/Flow · single activity · minSdk 26 · targetSdk 35.

---

## 1. Build status (read this first)

**This repository has not been compiled, and no APK has been produced.** The environment used
to write it had no JDK, no Android SDK, and no network access to `dl.google.com`,
`maven.google.com`, `repo1.maven.org` or `services.gradle.org`, so dependency resolution and
compilation were both impossible. Every claim below about behaviour is a description of the
source, not of an executed build or test run.

What *was* verified, mechanically, by the scripts in `tools/`:

| Check | Command | Result |
| --- | --- | --- |
| XML well-formedness of all resources and the manifest | `python3 tools/verify_project.py` | passes |
| Every `R.string`/`R.plurals` reference in Kotlin exists | `python3 tools/verify_project.py` | passes |
| English ↔ Amharic string parity | `python3 tools/verify_project.py` | passes (504 / 504) |
| No `INTERNET` permission | `python3 tools/verify_project.py` | passes |
| Manifest component classes exist on disk | `python3 tools/verify_project.py` | passes |
| Package declaration matches directory layout, braces balanced | `python3 tools/verify_project.py` | passes |
| Project types used in a file are imported | `python3 tools/check_imports.py` | passes |
| `receiver.member` calls resolve to declared members | `python3 tools/check_refs.py` | passes |
| `stringResource` argument counts match the format strings | `python3 tools/check_string_args.py` | passes |
| Screens only touch state fields / view-model members that exist | `python3 tools/check_ui_state.py` | passes |
| `when` blocks over project enums are exhaustive | `python3 tools/check_when_exhaustive.py` | passes |
| Compose symbols and `by` delegates are imported | `python3 tools/check_compose_imports.py` | passes |
| `ProjectObject.member` references exist | `python3 tools/check_object_refs.py` | passes |

Those scripts are a smoke test, not a compiler. **Expect to fix a few compile errors on the
first real build** — most likely unused imports promoted to errors, Compose API signature
changes, or a delegate/`by` import that the static check cannot see.

To build it (requires network access to Google's Maven repository and the Android SDK):

```bash
# JDK 17, Android SDK platform 35 + build-tools 35.0.0
# The wrapper JAR is not committed (binary), so generate it once if you do not use
# Android Studio, which provides its own Gradle:
gradle wrapper --gradle-version 8.9

./gradlew :app:assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:assembleRelease      # signed with the debug keystore; replace before shipping
./gradlew :app:testDebugUnitTest    # JVM unit tests
./gradlew :app:connectedDebugAndroidTest   # instrumentation tests, needs a device/emulator
```

`google-services.json` is **not** required: there is no Firebase, no analytics and no backend.

---

## 2. What the app does

### Screens

| Area | Screen | Notes |
| --- | --- | --- |
| Inbox | `Chats` | pinned/regular sections, unread-first sorting, multi-select (read, mute, archive, delete), swipe actions, search entry, empty/loading/error states, permission + role banners |
| Inbox | `Starred` | app-starred messages across threads |
| Inbox | `Archived` | hidden from the main list |
| Conversation | thread view | date separators, grouped bubbles, sender names in groups, per-message long-press actions (copy, forward, star, info, select, delete), tap-to-retry for failures, OTP chip, SIM indicator, draft persistence |
| Compose | `New message` | contact picker, free-form numbers, multi-recipient (SMS fan-out or group MMS), validation |
| Contacts | `Contacts` | search, avatar, jump to conversation, permission-gated with a graceful fallback |
| Search | `Search` | message bodies, names and numbers, date hints |
| Settings | `Appearance` | theme (system/light/dark), 6 accents, bubble style, font scale, density, wallpaper, avatars, haptics, reduced motion, language |
| Settings | `Notifications` | new-message alerts, previews, lock-screen privacy, grouping, sound picker, vibration, unread reminders with delay, quiet hours, test notification, system settings link |
| Settings | `Privacy & security` | app lock (BiometricPrompt), hide previews, OTP detection and masking, permission states, threat-model note, delete-all-local-data |
| Settings | `SIM & messaging` | preferred SIM, delivery reports, auto-retry, multipart handling, MMS notes, signature, system SIM settings |
| Settings | `Smart organization` | keyword → category rules, OTP detection, financial-wording warning |
| Settings | `Backup & restore` | password-encrypted export/import via the Storage Access Framework |
| Settings | `Storage & diagnostics` | database/preferences/cache sizes, environment snapshot, sanitised event log, clear cache/log |
| Settings | `About` | version, privacy statement, open-source licences |
| Gate | Onboarding | permission-by-permission rationale, role request, "not now" |
| Gate | App lock | biometric / device-credential prompt on entry |

### Telephony behaviour

* **Send**: `SmsManager.divideMessage` + `sendMultipartTextMessage` with per-part sent and
  delivery `PendingIntent`s. `SendOutcome.Accepted` means *submitted*, never *delivered*;
  success is only recorded when the platform reports `Activity.RESULT_OK`.
* **Receive**: a `SMS_DELIVER` receiver (`BROADCAST_SMS`, a signature permission only the
  system holds) with a `WAP_PUSH_DELIVER` MMS receiver and a `RESPOND_VIA_MESSAGE` service —
  the three components Android requires before it will hand out `ROLE_SMS`.
* **Dual SIM**: `SubscriptionManager` (with the `READ_PHONE_STATE` permission) lists active
  subscriptions; each send can target a `subscriptionId`, and a preferred SIM is stored for
  conversations that never picked one.
* **Offline**: SMS is a cellular service, not an internet service. There is no `INTERNET`
  permission in the manifest, deliberately. Sending requires a SIM and radio coverage; the
  app never fakes success when the radio is off, in airplane mode, or unsubscribed.
* **Outbox**: every send is written to a Room outbox row first so it survives process death,
  can be retried, and cannot be double-sent (duplicates are collapsed on
  `normalisedAddress + bodyHash`).
* **External entry points**: `sms:`/`smsto:`/`mms:`/`mmsto:` links open a prefilled composer,
  `ACTION_SEND` of shared text opens the composer with that text, and notification taps deep
  link straight into the conversation. A `SENDTO` filter is also part of the `ROLE_SMS`
  requirements.

---

## 3. Architecture

```
app/src/main/java/com/onemessages/app
├── MainActivity.kt              single activity, splash, deep link entry
├── OneMessagesApp.kt            @HiltAndroidApp, WorkManager configuration
├── core/Logx.kt                 local-only diagnostics logger with scrubbing
├── contacts/                    ContactsContract reader
├── data/
│   ├── local/                   Room: entities, DAOs, database + migrations
│   ├── preferences/             DataStore settings model and repository
│   ├── repository/              conversation, organization, storage, diagnostics, attachments
│   └── backup/                  AES-256-GCM backup format + crypto
├── di/                          Hilt modules and qualifiers
├── domain/SmartOrganizer.kt     keyword classification + OTP detection (pure Kotlin)
├── model/                       immutable UI/domain models
├── notifications/               channels, notifier, quiet hours, action receiver
├── permissions/                 permission groups and rationale
├── receivers/                   SMS, MMS, status, boot
├── service/                     HeadlessSmsSendService (respond-via-message)
├── telephony/                   SMS provider access, role manager, subscriptions, sender
├── ui/                          theme, components, screens, navigation, view models
├── util/                        phone numbers, time formatting, SMS segmentation
└── worker/                      reminder and retry workers
```

Data ownership is deliberate: **Android's SMS provider is the source of truth for messages.**
Room stores only what the app owns — conversation flags (pin/archive/mute/priority/custom
sound/block), drafts, keyword rules, the send outbox, starred-message ids, and a sanitised
diagnostics log. No message body is ever copied into app storage.

---

## 4. Permissions

| Permission | Why | When |
| --- | --- | --- |
| `SEND_SMS`, `RECEIVE_SMS`, `READ_SMS` | send, receive, show history | onboarding, re-asked only when a feature needs it |
| `READ_CONTACTS` | names and photos instead of numbers | contacts screen / recipient picker; typing a number always works |
| `READ_PHONE_STATE`, `READ_PHONE_NUMBERS` | dual-SIM listing, caller id | SIM settings and per-message SIM choice |
| `POST_NOTIFICATIONS` (API 33+) | new-message alerts and inline reply | onboarding; the app is fully usable without it |
| `USE_BIOMETRIC` | optional app lock | privacy settings |
| `VIBRATE` | notification vibration | notifications |
| `BROADCAST_SMS` | **not requested** — signature permission, held by the system | — |

Deliberately absent: `INTERNET`, `READ_EXTERNAL_STORAGE`/`WRITE_EXTERNAL_STORAGE`
(the app uses the Storage Access Framework), `ACCESS_NETWORK_STATE`, any analytics or ad SDK,
and `QUERY_ALL_PACKAGES`.

### The default SMS role

`ROLE_SMS` (`RoleManager`) is what makes an app *the* messaging app: it grants write access to
`content://sms` and `content://mms-sms`, the `BROADCAST_SMS`/`WAP_PUSH_DELIVER` broadcasts, and
`SMS_DELIVER` custody of incoming messages. Android hands it out only when the app declares the
required components *and* the user explicitly approves a system dialog. If the role is not
held, the app says so on the inbox and in settings, and explains what it cannot do; it never
pretends incoming messages are being stored.

---

## 5. Privacy and threat model

* No account, no server, no cloud, no telemetry, no crash reporting, no ad SDK. No
  `INTERNET` permission exists in the manifest, so the app cannot exfiltrate anything even if
  it tried.
* Message bodies, OTP codes and phone numbers are never logged. `Logx` is debug-only for
  Logcat and, for the on-device diagnostics list, scrubs long digit runs and address-looking
  strings before storing; only short technical strings are passed to it.
* OTP detection is a local regex over the body; the code is displayed in a chip with copy
  support and is never auto-filled into other apps.
* Backups are opt-in, user-initiated, and encrypted with AES-256-GCM under a key derived from
  the user's password with PBKDF2-HMAC-SHA256 (210 000 iterations, 16-byte salt, 12-byte IV).
  There is no recovery: a forgotten password means an unreadable file.
* `allowBackup=false`, so Android's cloud backup never copies app data either. Export is the
  only path off the device and the user runs it.
* The app lock re-arms after 20 seconds in the background, so opening a document picker or a
  permission dialog does not throw the user back to the lock screen mid-flow.
* The app lock is a **convenience gate, not encryption**. It stops someone who picks up an
  unlocked phone from opening the app; it does not encrypt Android's SMS database, does not
  protect against a rooted device, and cannot retract notification text the system already
  displayed.
* Messages already delivered to a contact, or stored by the carrier, cannot be deleted by
  deleting them here. The app states this in the delete confirmations.
* PendingIntents are immutable except where the platform requires mutability (`RemoteInput`
  for inline reply); every receiver that acts on them is non-exported.

---

## 6. Known limitations (honest list)

1. **MMS composition is not implemented.** Android does not expose the MMS PDU encoder that a
   third-party app needs to build an MMS, and the MMS provider is write-protected except for
   the system stack. `SmsManager.sendMultimediaMessage` therefore cannot be used from here.
   What the app does instead:
   * incoming WAP-push notifications are received and surfaced with an explanation;
   * the attachment sheet lets you pick a file and **shares it with another app** (the system
     messaging app, email, a chat app) — it is explicitly labelled as not being sent as MMS by
     1 MESSAGES;
   * the composer is text-only, shows the SMS part count, and never claims a picture was sent.
2. **Incoming MMS bodies/parts are not rendered.** Same reason as above: no public read of MMS
   parts for a non-default app is guaranteed across OEMs, and writing to `content://mms` on
   some devices requires privileges the role alone does not grant.
3. **Group MMS** is not offered; multi-recipient SMS is sent as individual messages per
   recipient (and the UI says so).
4. **Delivery reports depend on the carrier.** When the carrier sends none, the message stays
   `SENT` rather than `DELIVERED`. Nothing is upgraded to "delivered" without a report.
5. **Quiet hours never override Do Not Disturb.** During quiet hours notifications are posted
   on a low-importance channel; the platform's DND policy still wins.
6. **Reminders are best-effort.** WorkManager decides when to run them within the requested
   window; OEM battery managers may delay them further.
7. **SMS received while another app was the default** is visible only if that app wrote it to
   the system database; the app cannot recover messages it never saw.
8. **Search is local and exact-ish.** It matches substrings in bodies, names and numbers via
   the system provider; there is no fuzzy matching and no ranking.
9. **No APK in this repository** — see section 1.
10. **Amharic translations** cover the user-facing UI; rarely seen diagnostics text falls back
    to English on purpose.

---

## 7. Testing

### Unit tests (`app/src/test`) — pure JVM, no device

| Test | Covers |
| --- | --- |
| `PhoneNumbersTest` | digit extraction, validity bounds, local vs international matching, short codes |
| `SmsSegmentsTest` | GSM-7 (160/153) vs UCS-2 (70/67) part counting |
| `QuietHoursTest` | windows inside a day and across midnight, disabled windows, formatting |
| `SmartOrganizerTest` | keyword classification, unknown senders, financial wording, OTP detection limits |
| `BackupCryptoTest` | AES-GCM round trip, fresh IV per file, wrong password, truncated file, wipe |

### Instrumentation tests (`app/src/androidTest`) — need a device or emulator

| Test | Covers |
| --- | --- |
| `AppDatabaseTest` | drafts, outbox state machine, conversation flags, rule uniqueness, stars |
| `RoutesTest` | navigation route encoding for addresses with `+`, spaces and newlines |
| `MessageBubbleTest` | Compose rendering of a bubble and the retry affordance |

> These tests were written but **not executed** here — no Android SDK, no device, no network.

### Manual device checklist

Do this on a real phone with a SIM, not just an emulator (emulators have no real SMS stack):

1. Install, complete onboarding, grant SMS permission, accept the default-SMS role dialog.
2. Send "hello" to your own number: bubble shows *sending* → *sent*; on a carrier with
   delivery reports → *delivered*. Never *delivered* without a report.
3. Send a 300-character message: part count shows 2, both parts arrive, no duplicate send
   when the send button is tapped twice quickly.
4. Airplane mode on, send: it fails with a radio message, the retry affordance appears, and
   nothing is marked delivered. Airplane mode off, tap retry: it sends.
5. Receive an SMS from another phone with the app in the background: notification appears with
   inline reply; reply from the shade and confirm it appears in the thread.
6. Receive with the conversation already open: no notification for that thread.
7. Dual SIM: send from each SIM, confirm the SIM chip and the message's SIM annotation.
8. Long-press a thread: pin, mute, archive, mark read/unread, delete — each reflected in the
   inbox; archived threads appear in Archived.
9. Star a message, open Starred, unstar, confirm it disappears.
10. Search a word from a received message, then a partial phone number.
11. Rotate and kill/restart the app mid-draft: the draft is still there.
12. Enable app lock, background the app, return: biometric prompt; cancel stays locked.
13. Quiet hours on with a window that includes "now": a new message posts silently (check the
    channel is Low); a pinned/priority conversation still alerts if priority is enabled.
14. Backup: export with an 8+ character password, wipe app data, import: drafts/rules/flags
    return, message history is untouched. Import with a wrong password: clear error, no data
    loss.
15. Set language to አማርኛ, restart: UI is Amharic; no truncated buttons in either language.
16. TalkBack: every icon button announces something meaningful; font scale 200% does not clip.
17. Deny SMS permission, reopen: permission screen explains what is missing, no crash.
18. Revoke the default-SMS role while the app is open, return to it: the banner reflects it.

---

## 8. Static verification scripts

```bash
python3 tools/verify_project.py         # resources, manifest, R.* references, EN/AM parity, privacy
python3 tools/check_imports.py          # every project type used is imported
python3 tools/check_refs.py             # receiver.member calls exist on the declared type
python3 tools/check_string_args.py      # stringResource() argument counts vs format strings
python3 tools/check_ui_state.py         # screens vs view-model state/members
python3 tools/check_when_exhaustive.py  # when over an enum covers every entry or has else
python3 tools/check_compose_imports.py  # Compose symbols + delegate imports
python3 tools/check_object_refs.py      # ProjectObject.member references resolve
```

They are intentionally dependency-free (standard library only) so they run anywhere.

---

## 9. Localisation

English is the default resource set; `values-am` carries Amharic. Keys are kept in parity and
the app never hardcodes user-facing English in Compose. Layouts use `start`/`end` and the
manifest sets `android:supportsRtl="true"`, so right-to-left locales mirror correctly.
