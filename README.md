# College Bell

An Android app that rings the college bell on schedule, by itself, with nobody
touching the tablet.

One app. No server, no login, no internet. Everything is stored on the tablet.

**Setting it up or testing it? → [RUNNING.md](RUNNING.md)**

---

## For whoever maintains this

**You should never need to edit code.** Timetables, bell times, sounds, holidays
and the PIN are all changed on the tablet's screen. Code changes are only needed
to add new *features*.

### Running the app day to day

| You want to | Do this |
|---|---|
| Change a bell time | **Timetables** tab → tap the timetable → tap the bell |
| Add an exam-day schedule | **Timetables** → New timetable → then **Calendar** → Add a specific date |
| Declare a holiday | **Calendar** tab → Add a holiday |
| Ring the bell by hand | **Today** tab → RING BELL NOW |
| Stop a bell that's ringing | **Today** tab → STOP BELL (future bells are not affected) |
| See what rang | **History** tab |
| Back it up | **Settings** → Save backup (gives you one .json file) |

### The three things that break bells

None of these are bugs in the app. Check them once, on the tablet that stays put.
**Settings → Device checks** tells you where each one stands.

1. **Battery optimisation** must be OFF for this app. Android will otherwise put the
   app to sleep and bells will be missed. Settings → Device checks → Fix.
2. **Autostart** must be ON. Xiaomi, Oppo, Vivo and Realme tablets each have their own
   security app that blocks apps from starting after a restart. Find it, allow this app.
   Then actually test it: restart the tablet, don't open the app, see if the next bell rings.
3. **Keep it plugged in.** A flat tablet rings nothing. Ideally on the same UPS as the
   amplifier.

### If a bell doesn't ring

Work down this list:

1. Is the tablet on, charged, and not in flight mode? (Flight mode is fine, actually —
   this app needs no internet.)
2. Does **Today** show a green "Running" banner? Red means no timetable is assigned
   to today — fix it on the **Calendar** tab.
3. Is the media/alarm volume up, and is the cable to the amplifier seated?
4. Does **History** show the bell with "FAILED"? That means the app tried and the sound
   file would not play — usually a custom sound that was deleted or moved. Set that
   bell back to the built-in sound.
5. Nothing in History at all for that time? The app was asleep. Go back to the three
   things above — it will be number 1 or number 2.

---

## For a developer

### Build it and check it

See **[RUNNING.md](RUNNING.md)** — installing Android Studio, running on a tablet or the
emulator, building an APK, reading the logs, and the eight-test acceptance run to go
through before this replaces the manual bell.

Short version: open the `college-bell` folder in Android Studio, wait for the Gradle sync,
press Run. Tests are `ScheduleTest.kt` → right-click → Run (12 tests, no phone needed).

### How it works

Eight Kotlin files. Read them in this order:

| File | What it does |
|---|---|
| `Data.kt` | The data classes, and load/save of the single `config.json` |
| `Schedule.kt` | Which timetable applies on a date, and which bell is next. Pure logic, no Android. |
| `Scheduler.kt` | Sets the alarm with `AlarmManager` |
| `BellReceiver.kt` | Woken by the alarm and by reboot; rings, then arms the next |
| `BellService.kt` | Plays the sound. **The IoT hook goes here.** |
| `MainActivity.kt` | The five tabs |
| `ui/*.kt` | One file per tab |

**Only one alarm is ever set** — the next bell, and nothing else. When it fires, the
receiver rings the bell and immediately arms the one after it. The chain keeps itself
going. It is also re-armed on boot, on app update, when the clock changes, whenever
anything is saved, and whenever the app is opened.

The precedence rule, highest first:

1. the date is a **holiday** → no bells
2. the date has a **specific timetable** → use it
3. the **weekday** has a timetable → use it
4. otherwise → no bells, and the dashboard says so in red

### Adding the IoT relay later

Everything about "a bell happened" goes through one function, `trigger()` in
`BellService.kt`. Add the HTTP or MQTT call to the relay board right beside the
existing `playAudio()` call:

```kotlin
private fun trigger(label: String, soundUri: String?, durationSec: Int): String? {
    // relayClient.post("/ring", durationSec)   <-- add this
    return playAudio(soundUri, durationSec)
}
```

Run both outputs during the changeover, then delete whichever one you no longer want.
Nothing else in the app has to change.

### Deliberately left out

Not oversights — decisions. Add them when someone actually asks:

teacher phone sync, cloud backup, email alerts, PDF/Excel export, Excel import,
a 30-day deleted-bell recycle bin, sign-in lockout timers, inactivity sign-out,
multi-editor conflict handling, low-battery and PA-disconnection alerts.

Backup is covered by **Settings → Save backup**: one JSON file you can put on Drive
or a pen drive.
