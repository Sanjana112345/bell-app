# Sharing the app and the code

Two different things, with different rules:

- **The app** (an APK file) → teachers, the office, whoever installs it on the tablet
- **The code** → whoever maintains or changes it later

---

## Part 1 — Sharing the app

### First, stop using the debug APK

The file you get from `gradle assembleDebug` is signed with a **throwaway key that is
unique to the computer it was built on**. That causes a specific, painful problem:

> Build version 1 on your laptop, hand it out. Later someone builds version 2 on a
> different computer. Android sees a different signature and **refuses to update** —
> the app must be uninstalled first, which wipes every timetable on the device.

So before you share anything, make a proper signing key. You do this **once, ever**.

### Create your signing key (once)

```bash
export JAVA_HOME=$HOME/tools/jdk-21.0.12.1+1

$JAVA_HOME/bin/keytool -genkeypair -v \
  -keystore college-bell.jks \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias college-bell
```

It asks for a password (twice) and some name/organisation details. The details don't
matter much — put the college name. **The password does matter.**

Then create `keystore.properties` next to it:

```properties
storeFile=college-bell.jks
storePassword=whatever-you-chose
keyAlias=college-bell
keyPassword=whatever-you-chose
```

Both files are already in `.gitignore` and will never be committed.

### ⚠️ Back up the keystore properly

`college-bell.jks` and its password are the single most important thing in this project.

- **Lose them** → you can never update the app on any device that has it. Only a full
  uninstall-and-reinstall works, and that erases all timetables.
- Put a copy somewhere that survives this laptop dying: the college's Google Drive, a
  password manager, or a printed envelope in the principal's safe. All three, ideally.
- The *code* can be rewritten. The keystore cannot be recreated.

### Build the release APK

```bash
gradle assembleRelease
```

Output: **`app/build/outputs/apk/release/app-release.apk`**

Check it actually got signed:

```bash
$HOME/Android/Sdk/build-tools/35.0.0/apksigner verify --print-certs \
  app/build/outputs/apk/release/app-release.apk
```

### Hand it out

Any of these work — it's a single file:

- WhatsApp, email, or Google Drive link
- Copy to the tablet over USB
- `adb install -r app-release.apk` with the device plugged in

Whoever installs it has to allow **"Install unknown apps"** for whichever app they opened
the file with (Files, Chrome, WhatsApp). Android prompts for this; it's normal for an app
that isn't from the Play Store.

### Should this go on the Play Store?

No. It's an internal tool for one college. The Play Store would mean a developer account
(US$25), a privacy policy, data-safety declarations, and review delays every time you
change a bell time — for an app that three people install once. Sideload the APK.

---

## Part 2 — Sharing the code

### What's in and what's out

A git repository has been initialised here with a first commit: **30 files, about 376 KB**.

Deliberately **not** committed (see `.gitignore`):

| Not committed | Why |
|---|---|
| `local.properties` | Your Android SDK path — different on every machine |
| `keystore.properties`, `*.jks` | Your signing key and password |
| `build/`, `.gradle/` | Generated — rebuilt from source in seconds |

Anyone who clones this needs only to create their own `local.properties`. That's covered
in [RUNNING.md](RUNNING.md).

### Push it to GitHub

There's no `gh` command installed, so do it through the website:

1. Go to https://github.com/new
2. Name it `college-bell`. **Pick Private** unless you want it public.
3. Do **not** tick "Add a README" — you already have one, and it causes a conflict.
4. Create, then run what GitHub shows you, which will be:

```bash
cd ~/Documents/per/college-bell
git remote add origin https://github.com/YOUR-USERNAME/college-bell.git
git branch -M main
git push -u origin main
```

GitHub will ask for a password — it does **not** accept your account password. Use a
**personal access token** instead: github.com → Settings → Developer settings → Personal
access tokens → Tokens (classic) → Generate, tick `repo`, copy it, paste as the password.

### Before you make it public

Check you haven't committed anything private:

```bash
git log --stat             # what went in
git ls-files | grep -iE "keystore|\.jks|local.properties"   # should print nothing
```

If that last command prints anything, **stop** — the file is in history and deleting it
later doesn't remove it. Easiest fix at this stage is to delete `.git` and start the
repository again.

### Handing over to the next maintainer

Point them at three files, in this order:

1. **[README.md](README.md)** — what it does, the day-to-day operations table, and the
   three device settings that silently break bells
2. **[RUNNING.md](RUNNING.md)** — building it, running it, and the eight-test acceptance run
3. **[SHARING.md](SHARING.md)** — this file, especially the keystore warning

And tell them the keystore password. In person, not over WhatsApp.

---

## What never gets shared

- `college-bell.jks` and its password — not in git, not in chat, not in email
- `keystore.properties`
- The app's PIN, if you set one in Settings

The APK is fine to share freely. It contains no secrets — timetables live on each device,
not in the app file.
