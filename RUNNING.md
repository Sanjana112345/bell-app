# How to run and check College Bell

Two parts: **getting it running** (once), and **checking it actually works** (the part
that matters — a bell app that looks fine on screen can still miss bells).

---

## Part 1 — Get it running

### What you need

- **Android Studio** — https://developer.android.com/studio (free). Install it with the
  default options; it brings its own Java and Android SDK, so you don't install those
  separately.
- A **tablet or phone**, or the built-in emulator if you don't have one yet.

> **Don't want Android Studio?** You don't need it — see
> *[Build without Android Studio](#build-without-android-studio)* below. That route is
> already installed and working on this machine: `gradle test assembleDebug` builds the
> APK. You lose the emulator and the Logcat viewer, nothing else.

### Open it

1. Start Android Studio → **Open** → pick the `college-bell` folder (the one with
   `settings.gradle.kts` in it). Not a file inside it — the folder itself.
2. Wait. The first open downloads Gradle and the Android SDK: **10–20 minutes** on a
   normal connection, and the progress bar sits still for long stretches. This only
   happens once.
3. You're ready when the bottom status bar says **"Gradle sync finished"**.

If it complains about the Gradle wrapper, use **File → Sync Project with Gradle Files**,
or open the terminal inside Studio and run `gradle wrapper`.

### Run it on a real tablet (preferred)

1. On the tablet: **Settings → About → tap "Build number" seven times**. This unlocks
   Developer options.
2. **Settings → Developer options → USB debugging → ON**.
3. Plug it into the computer. The tablet shows "Allow USB debugging?" — tick *Always*
   and accept.
4. In Android Studio the device name appears in the dropdown at the top. Pick it and
   press the green **▶ Run** button.

### Or run it on the emulator

In Android Studio: **Tools → Device Manager → Create Device** → pick any tablet →
choose a system image of **API 34 or 35** → Finish. Then select it in the dropdown and
press ▶.

The emulator is fine for checking the screens and the scheduling. It cannot tell you
anything about your PA amplifier, and OEM autostart quirks don't exist on it — so the
real tablet is still the one that counts.

### Build without Android Studio

Android Studio is only a GUI over Gradle and the Android SDK — you can do the whole
thing from a terminal. **This has been set up and verified on this machine already**, so
if you are on it, skip to *Everyday commands* below.

You need three things, all of which install into your home folder and uninstall by
deleting a folder:

| What | Where it went |
|---|---|
| JDK 21 (the compiler — a JRE is not enough) | `~/tools/jdk-21.0.12.1+1` |
| Gradle 8.9 | `~/tools/gradle-8.9` |
| Android SDK (platform 35, build-tools 35, platform-tools) | `~/Android/Sdk` |

#### One-time setup

```bash
mkdir -p ~/tools/dl && cd ~/tools/dl

# 1. JDK 21
curl -L -o jdk21.tar.gz \
  "https://api.adoptium.net/v3/binary/latest/21/ga/linux/x64/jdk/hotspot/normal/eclipse"
tar xzf jdk21.tar.gz -C ~/tools/

# 2. Gradle
curl -L -O "https://services.gradle.org/distributions/gradle-8.9-bin.zip"
unzip -q gradle-8.9-bin.zip -d ~/tools/

# 3. Android SDK command-line tools
curl -L -o cmdline-tools.zip \
  "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
mkdir -p ~/Android/Sdk/cmdline-tools
unzip -q cmdline-tools.zip -d ~/Android/Sdk/cmdline-tools/
mv ~/Android/Sdk/cmdline-tools/cmdline-tools ~/Android/Sdk/cmdline-tools/latest

# 4. SDK packages (accept the licences, then install)
export JAVA_HOME=~/tools/jdk-21.0.12.1+1
SDKM=~/Android/Sdk/cmdline-tools/latest/bin/sdkmanager
yes | $SDKM --licenses
$SDKM "platform-tools" "platforms;android-35" "build-tools;35.0.0"
```

Then point the project at the SDK — one line, and it is deliberately **not** committed
to git because the path differs per machine:

```bash
echo "sdk.dir=$HOME/Android/Sdk" > ~/Documents/per/college-bell/local.properties
```

Finally, put these in your `~/.bashrc` so every new terminal has them:

```bash
export JAVA_HOME=$HOME/tools/jdk-21.0.12.1+1
export ANDROID_HOME=$HOME/Android/Sdk
export PATH=$PATH:$HOME/tools/gradle-8.9/bin:$ANDROID_HOME/platform-tools
```

#### Everyday commands

From the `college-bell` folder:

```bash
gradle test              # run the 12 scheduling tests  (~15s once warm)
gradle assembleDebug     # build the APK
gradle test assembleDebug   # both
```

The APK lands at **`app/build/outputs/apk/debug/app-debug.apk`** (about 17 MB). Install
it on a plugged-in phone or tablet:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Or just copy that file to the device and tap it.

The first build downloads the Android Gradle Plugin, Kotlin and Compose — **4 minutes or
so**. Every build after that is seconds.

#### What you give up

No emulator, no Logcat viewer, no layout preview, no Device Manager. For this app that
is a fair trade — `adb logcat | grep Bell/` covers debugging — but if you have no
physical Android device to test on, you need Android Studio for its emulator.

> **On `gradle wrapper`:** normally you would generate a wrapper (`gradle wrapper`) so
> `./gradlew` works without Gradle installed. It needs to reach `services.gradle.org` to
> validate the distribution, so if that is blocked on your network, just use the `gradle`
> command directly as shown above.

### Build an installable APK

To hand the file to someone instead of plugging in a cable:

**Build → Build Bundle(s)/APK(s) → Build APK(s)**

A notification links to the file, which lands at
`app/build/outputs/apk/debug/app-debug.apk`. Copy it to the tablet and open it. The
tablet will ask you to allow "Install unknown apps" for whatever you opened it with.

---

## Part 2 — Check that it works

### The fast check: does a bell ring at all

1. Open the app. The **Today** tab should show a green **"Running — Regular Weekday"**
   banner. (On a Saturday or Sunday it will be red — that's correct, nothing is assigned
   to weekends by default. Go to **Calendar** and assign a timetable to today if you want
   to test right now.)
2. Note the current time, then **Timetables → Regular Weekday → Add bell**. Set the time
   to **two minutes from now**. Save.
3. Go back to **Today**. The "NEXT BELL" card should show your new bell and count down.
4. **Lock the screen and put the tablet down.** Don't keep the app open — the whole point
   is that it rings without anyone watching.
5. It should ring at the minute you set.

If that works, the core of the system works.

### Run the unit tests

These check the scheduling rules — which timetable applies on which day, what the next
bell is, holidays beating everything. No phone needed.

Right-click `app/src/test/java/com/college/bell/ScheduleTest.kt` → **Run 'ScheduleTest'**.

All 12 should pass, in about a second. Or from a terminal:

```
gradle test
```

The report lands at `app/build/reports/tests/testDebugUnitTest/index.html`.

### Install adb

`adb` is the command-line tool for talking to the tablet. You need it for the Doze test
(item 6 below) and for reading logs outside Android Studio.

**You probably already have it.** Android Studio installs it as part of the SDK — it is
just not on your PATH, so the `adb` command isn't found until you add it.

Check first:

```
adb version
```

If that prints a version number, skip the rest of this section.

#### Windows

Android Studio put it at `%LOCALAPPDATA%\Android\Sdk\platform-tools`. Add that folder
to your PATH:

1. Press Start, type **"environment variables"**, open *Edit the system environment
   variables*
2. **Environment Variables…** → under *User variables* select **Path** → **Edit** → **New**
3. Paste `%LOCALAPPDATA%\Android\Sdk\platform-tools`
4. OK out of all three dialogs, then **open a new terminal** (the old one keeps the old PATH)

#### Linux

```
sudo apt install adb          # Debian / Ubuntu / Mint
sudo dnf install android-tools   # Fedora
```

Or use the copy Android Studio installed, at `~/Android/Sdk/platform-tools`.

#### Mac

```
brew install android-platform-tools
```

Or use Android Studio's copy at `~/Library/Android/sdk/platform-tools`.

#### Without Android Studio

Download **SDK Platform-Tools** on its own (about 10 MB) from
https://developer.android.com/tools/releases/platform-tools — unzip it anywhere and add
that folder to your PATH.

#### Check it sees the tablet

Plug the tablet in, with USB debugging already turned on, and run:

```
adb devices
```

You want a line ending in **`device`**:

```
List of devices attached
R52T20ABCDE     device
```

- **Nothing listed** → bad cable (plenty of charging cables carry no data), or USB
  debugging is off.
- **`unauthorized`** → look at the tablet screen and accept the "Allow USB debugging?"
  prompt. Tick *Always allow*.
- **`no permissions` (Linux)** → add yourself to the `plugdev` group, then log out and
  back in: `sudo usermod -aG plugdev $USER`

### Watch what the app is doing

You don't need adb for this — the **Logcat** panel at the bottom of Android Studio does
the same job. With the tablet plugged in, type `Bell/` in its filter box. You'll see
lines like:

```
Bell/Scheduler  Next bell: Lunch at 2026-10-19T12:30
Bell/Receiver   Received com.college.bell.RING
Bell/Service    RING: Lunch for 10s
```

Same thing from a terminal, if you have adb:

```
adb logcat | grep Bell/
```

**Run it live, before the bell is due — don't dump the log afterwards.** Phones log
heavily (sensors, wifi, system services) and the buffer wraps within a minute or two on
some devices, so `adb logcat -d` after the fact will often show nothing from the app.
Verified on a OnePlus CPH2613: the app's lines were gone seconds later. If you need
after-the-fact logs, enlarge the buffer first:

```
adb logcat -G 16M
```

A more reliable record than the log: the app's own **History** tab, which is written to
disk on every ring and survives restarts.

Either way, this is the quickest way to answer "did it even try?" when a bell doesn't sound.

---

## The acceptance run

Before this replaces the manual bell, go through all eight. Items 5–7 are the ones that
catch real-world failures — don't skip them because the app "looks fine".

| # | Test | Pass means |
|---|---|---|
| 1 | Set a bell 2 min out, **lock the screen**, walk away | It rings |
| 1b | Check **History** afterwards | The bell is listed with no FAILED marker |
| 2 | Ring manually (Today → RING BELL NOW), press STOP mid-ring | Sound stops; the next scheduled bell still shows in NEXT BELL |
| 3 | Mark today a holiday in **Calendar** | Today shows an orange "Holiday" banner; nothing rings |
| 4 | Turn on **flight mode**, set a bell 2 min out | It still rings (the app needs no internet) |
| 5 | **Restart the tablet.** Don't open the app. Set a bell before you restart | It rings after the reboot |
| 6 | Leave it **untouched for 3+ hours** with a bell scheduled | It rings (this is the Doze test) |
| 7 | **Plug into the actual PA amplifier** and stand in a corridor | You hear it where students are |
| 8 | **Settings → Save backup**, then uninstall, reinstall, **Restore backup** | Timetables and calendar come back |

**Item 5 is the one that fails most often**, and it's never a code problem — Xiaomi,
Oppo, Vivo and Realme tablets each have a security app that blocks apps from starting
after a restart. Find it in the tablet's own settings and allow College Bell. Then run
test 5 again.

**Item 6** can be forced instead of waited out, with the tablet plugged in:

```
adb shell dumpsys battery unplug
adb shell dumpsys deviceidle force-idle
```

...then wait for a bell. Put the tablet back to normal afterwards:

```
adb shell dumpsys deviceidle unforce
adb shell dumpsys battery reset
```

(If `adb` isn't found, see **Install adb** above.)

**Item 7 cannot be skipped.** The app playing a sound successfully does *not* prove the
amplifier produced noise in a corridor. That needs a person standing in the corridor.

---

## When something is wrong

| What you see | What it is |
|---|---|
| Red banner: "No timetable assigned to today" | Nothing is set for this weekday. **Calendar** tab → Weekly pattern. |
| Bell appears in **History** marked FAILED | The app tried; the sound file wouldn't play. Usually a custom sound that was moved or deleted — set that bell back to the built-in sound. |
| Nothing in History for that time at all | The app was asleep and never woke. Battery optimisation or autostart — see **Settings → Device checks**. |
| Rings, but nobody hears it | Volume, cable, or amplifier. The app forces alarm volume to maximum on every ring, so it is almost always the cable or the amp. |
| App won't build | **File → Invalidate Caches → Invalidate and Restart**, then let it sync again. |
