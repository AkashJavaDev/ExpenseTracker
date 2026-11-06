# Expense Tracker

An offline Android app for tracking monthly spending. Add amounts to categories, see where each
month's money went, and export a clean PDF or picture report. No internet, no permissions, no ads.

Built with **Kotlin**, **Jetpack Compose** (Material 3) and **Room**.

## Features

**Home**
- Month header with ◀ ▶ navigation, the month's total, and a *Fixed & one-time · Daily* split.
- One card per category, with every amount shown as a chip. Tap a chip to edit or delete it (with Undo).
- Categories can be grouped (e.g. *Food → Morning / Afternoon / Night / Snacks*).
- "Show only when used" categories stay hidden in months where nothing was spent on them.
- Adding while viewing a past month files the amount in that month.

**Month summary**: donut chart plus per-category share bars, biggest first.

**Categories**: drag to reorder, rename, group or ungroup, delete (warns how many amounts go with it).

**Print / export**
- This month or all months, as a PDF or a picture.
- Print, save to a file, or share through any app.

**Spent / Received tabs** (share version): track money coming in as well. The header shows what's
**left this month** (green) or how much you're **over by** (red).

**Privacy**
- No permissions and no internet access. Data stays in the app's private storage.
- Cloud backup and device-to-device transfer are turned off.
- Reports are shared through a private `FileProvider`, limited to the app's report cache.

## Two versions

One codebase builds two apps, chosen with Gradle product flavors:

| Flavor | App id | Starts with | Extras |
|---|---|---|---|
| `share` | `com.expensetracker.app.share` | General categories, no data | Spent / Received tabs |
| `mine` | `com.expensetracker.app` | The author's personal categories | — |

They have different app ids, so both can be installed on the same phone. **Use `share`** if you
just want to try the app.

`mine` can import personal spending notes from `app/src/minePrivate/`, which is not in this
repository. Without that folder it builds from `app/src/minePublic/` and starts empty.

## Build

**Requirements**
- Android SDK with platform 35 (Android Studio installs this).
- JDK 17 or 21. Gradle 8.9 does not run on newer JDKs.

> **Before building, remove the `org.gradle.java.home=...` line from `gradle.properties`.**
> It points to a JDK folder on the author's PC. Without it, Gradle uses your `JAVA_HOME` or
> Android Studio's JDK setting.

**Android Studio:** open the project folder, pick a variant under *Build Variants*
(`shareDebug` is a good start), then Run.

**Command line:**

```bash
./gradlew assembleShareDebug
```

The APK lands in `app/build/outputs/apk/share/debug/`. On Windows use `gradlew.bat`.

Release builds are shrunk with R8. Signing release builds needs the author's keys (kept outside
the project), so use the debug variants for your own builds.

## Project layout

```
app/src/main/java/com/expensetracker/app/
  MainActivity.kt      Navigation between screens
  data/                Room entities, DAO, database, migrations, starter data import
  ui/                  Home, month summary, categories, print dialog, theme
  report/              PDF / picture rendering, print, save and share
app/src/share/         Starter categories for the share version
app/src/mine/          Starter categories for the mine version
app/src/minePublic/    Empty stand-in for the private spending notes
```

Amounts are stored in paise (`Long`), so totals never pick up rounding errors.

## Tech

Kotlin 2.0 · Jetpack Compose (BOM 2024.12.01) · Material 3 · Room 2.6 · Navigation Compose ·
[Reorderable](https://github.com/Calvin-LL/Reorderable) for drag-to-reorder · min SDK 26 (Android 8.0) · target SDK 35
