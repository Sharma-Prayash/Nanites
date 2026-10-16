# PS-01 On-Device App Risk Auditor (Mobile Security · Medium · 6 hours · Working APK)

Find suspicious Android apps through their permissions, special access and an explainable risk score.

## Problem
Build an Android app (or reusable module) that scans all user-installed apps, extracts declared and granted
permissions and special access, organizes them into categories, and assigns each app a risk level with
human-readable reasons. A non-technical user must answer: Q1 Which apps can do the most harm? Q2 Why is each
flagged? Q3 What can I do about it right now?

## Nine required categories
1. Special access (HIGHEST WEIGHT): accessibility service, overlay (SYSTEM_ALERT_WINDOW), device admin,
   notification listener, REQUEST_INSTALL_PACKAGES, PACKAGE_USAGE_STATS
2. SMS: READ_SMS, RECEIVE_SMS, SEND_SMS
3. Contacts and call logs: READ_CONTACTS, READ_CALL_LOG
4. Camera and microphone: CAMERA, RECORD_AUDIO
5. Location: ACCESS_FINE_LOCATION, ACCESS_BACKGROUND_LOCATION
6. Phone and identity: READ_PHONE_STATE, READ_PHONE_NUMBERS, CALL_PHONE
7. Storage and media: READ_MEDIA_IMAGES, MANAGE_EXTERNAL_STORAGE
8. Background and persistence: RECEIVE_BOOT_COMPLETED, FOREGROUND_SERVICE, REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
9. Network: INTERNET, ACCESS_NETWORK_STATE (low alone, high in combination)

## Must have
01 Inventory: name, icon, package, version, install date, installer source, target SDK; system-app toggle
02 Declared vs granted, with protection level (normal, dangerous, signature, special)
03 Nine categories per app and device-wide summary
04 Enabled accessibility services, active device admins, enabled notification listeners, declared overlay and install-packages
05 Score 0–100, four bands (Low, Medium, High, Critical), ≥3 plain-language reasons for every High/Critical app
06 Detail screen: "Why flagged" panel, button to system settings, uninstall button

## Should have
07 Combination rules (SMS + internet + boot from sideloaded source; accessibility + overlay; non-MDM device admin)
08 Context mismatch (torch/calculator/wallpaper with SMS, contacts or call log)
09 Rescan on install/update; notify on High/Critical
10 Alert when an update adds sensitive permissions

## Stretch
11 SHA-256 of base APK vs threat intel (hashes only, never the APK)
12 Static checks: exported components, cleartext traffic, debuggable
13 On-device ML model (pre-trained on public dataset), shown beside rule score; rules stay the explanation source
14 Shareable PDF report

## Constraints
On-device only, no root, no ADB for end user · No user data leaves device except consented hash lookups ·
Offline first · QUERY_ALL_PACKAGES allowed for the hackathon · No live malware

## Deliverables
1 Working APK on judging device/emulator by end of hour 5 · 2 One-page scoring rubric (weights + combination rules) ·
3 Source repo link · 4 Three-minute live demo on the organizer test kit (not team-chosen apps)
