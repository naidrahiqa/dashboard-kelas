# Dashboard Kelas (Tekajeduwa)

An Android classroom seat-arrangement dashboard built with **Kotlin + Jetpack Compose (Material 3)**. Set up the class layout once, then rotate or reshuffle seats each period with a single tap. The Gradle project is named **Tekajeduwa** (`rootProject.name` in `settings.gradle`); the app's launcher label is *Dashboard Kelas* (`com.tekajeduwa.app`).

## Features

- **Seat board** — 4 columns (`B1`–`B4`) with sizes `[4, 4, 5, 4]` (column 3 is one desk deeper), shown front-of-class first, with a live summary of the current period and filled-seat count.
- **Assign / edit students** — tap any seat to type a name, clear it, or cancel. Names persist in `SharedPreferences` (`tekajeduwa`), keyed per seat plus a `period` counter.
- **Bulk fill (Isi Nama Cepat)** — paste one name per line; choose *fill empty seats only* or *replace everything*. Names are placed front-to-back across the layout.
- **Shuffle (Acak)** — randomly redistributes the existing names with a ~3 second scramble animation, then saves the result.
- **No-repeat seating** — both **Acak** and **Rolling Periode** share the same randomiser, and a student is never placed in a seat they have already used within the last `JENDELA_RIWAYAT` (8) periods. Seat history is persisted per student (`riwayat_kursi`, a JSON map of name → recent seats) and is also seeded from the seats currently occupied, so even the very first shuffle after an update refuses to hand a student back their own seat. Assignment uses randomized bipartite matching (Kuhn's algorithm), so a conflict-free layout is always found when one exists; if the window makes it impossible (very small boards), the oldest entries are dropped and the search repeats. A summary dialog reports how many students got a fresh seat versus how many had to repeat. **Reset** clears this history too. Measured on the 17-seat board: 0 repeats across 60 consecutive periods, and repeated presses never produce the same layout.
- **Rolling Periode** — advances the period counter, reshuffles seats at random with the no-repeat rule above, and shows the new layout for that period in a dialog. (The old deterministic rotation — one column right, one desk forward — was removed: it produced the same diagonal pattern every period.)
- **MBG list (Daftar MBG)** — a separate roster screen for tracking who picked up the free-meal (MBG) portion, with per-student status: *Ambil*, *Belum Ngambil*, *Gak Ngambil*, or *Dikasih Temen*, plus live totals per status. Names can be added one at a time or pasted in bulk (duplicate names are ignored); tapping a row renames, re-statuses, or deletes that student. Stored as JSON under `daftar_mbg` and independent of the seat board — **Reset** on this screen only clears the MBG list.
- **Reset** — confirmation dialog that clears all names and sets the period back to 0.
- **Theming** — follows the system dark/light setting; uses dynamic Material You color schemes on Android 12+, with a green fallback scheme on older devices.
- Summary cards for period and student count, edge-to-edge layout, scrollable board.

## Build

```bash
./gradlew assembleDebug
# output: app/build/outputs/apk/debug/app-debug.apk
```

Requires a JDK (the CI uses **Temurin 21**; sources target JVM 17) and the Android SDK for `compileSdk 36`.

## Install

Download the APK from the repository's **Releases** page and allow installation from unknown sources when prompted.

## CI/CD

The **Build & Release** workflow ([`.github/workflows/build.yml`](.github/workflows/build.yml)) runs on:

| Trigger | Result |
| ------- | ------ |
| Push to `main` | Builds a debug APK, uploads it as an artifact, and refreshes the rolling `latest` release (`Build Terbaru`) |
| Tag `v*` | Builds the APK as `DashboardKelas-<tag>.apk` and creates a GitHub Release with generated notes |
| `workflow_dispatch` | Manual build (artifact only) |

Steps: checkout → JDK 21 (Temurin) → Gradle setup → `./gradlew assembleDebug --no-daemon` → copy APK to `dist/` → upload artifact → release step.

Note: CI publishes **debug** APKs (release builds are not signed in this repo).

## Requirements

- Android 7.0+ (`minSdk 24`; `targetSdk`/`compileSdk` 36).
- JDK 21 for building via CI, JDK 17+ locally.

## Project layout

```
app/src/main/java/com/tekajeduwa/app/
├── MainActivity.kt   # Compose UI, view switch (kursi/MBG), dialogs, theming, shuffle animation
├── MbgLogic.kt       # MBG roster model, statuses, persistence
└── SeatLogic.kt      # seat model, no-repeat randomiser (Kuhn matching + history window), persistence
```
