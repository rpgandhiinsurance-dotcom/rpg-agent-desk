# RPG Daily Brief (Android)

An Android app for RPG Insurance Advisory that shows the same **daily brief** as the scheduled
Claude task: birthdays, renewals, follow-ups, leads, payments and open claims, read live from
Airtable. A notification arrives at about 8:00 AM IST each day.

The app is **read-only**: it only sends `GET` requests to Airtable and never creates, changes or
deletes a record. Call and WhatsApp buttons just open the dialer or a WhatsApp chat; you send
any message yourself.

## Project layout

| Module | What it is |
|---|---|
| `brief/` | Plain Kotlin (no Android). Airtable client, field IDs, all brief rules, text rendering. Unit-tested. |
| `app/` | Android app: Jetpack Compose UI, encrypted token storage, offline cache, WorkManager daily job, notification. |

Key files:

- `brief/.../Schema.kt` has every table and field ID, **checked against the Meta API on 7 Nov 2026**.
- `brief/.../BriefBuilder.kt` has the six rules, in the brief's order.
- `brief/.../BriefService.kt` loads the five tables in parallel. A failed table is reported by name; it never shows up as "nothing due".
- `app/.../work/DailyScheduler.kt` + `DailyBriefWorker.kt` handle the 8 AM IST refresh and notification.
- `app/.../data/Settings.kt` stores the token in EncryptedSharedPreferences.

## Brief rules

All dates are compared with **today in Asia/Kolkata**, whatever time zone the phone is set to.

1. **Birthdays**: Date of Birth day and month falls today or within the next 3 days. One line per client (names are matched case- and space-insensitively across policies), with the age they're turning. Flags **No mobile** when none of their policies has one. A 29 Feb birthday shows on 28 Feb in other years.
2. **Renewals**: `Status = Active` and End Date exactly 30, 15, 7, 3, 1 or 0 days away, or already past (marked **Overdue**). Flags **No mobile**.
3. **Follow-ups due**: Done unticked and Due Date today or earlier.
4. **Leads to call**: Stage not Won or Lost (an empty stage counts as open) and Next Follow-up today or earlier.
5. **Payments due**: Paid Date empty and Due Date today or earlier.
6. **Open claims**: a count of claims in Intimated, Documents pending, Under review or Approved, naming the clients whose claims are in Documents pending.

Empty sections are hidden. If every table loads and every section is empty, the app shows
**Nothing due today**. If a table fails, it says which one and why (bad token, no network,
rate limit…).

## Setup

### 1. Create an Airtable token

At <https://airtable.com/create/tokens> create a personal access token with:

- Scopes: `data.records:read`, `schema.bases:read`
- Access: only the RPG Insurance base (`appjzr6iFMV1cZPh9`)

The token is **not** in the APK. Paste it into the app's **Settings** screen. It's stored with
EncryptedSharedPreferences (key in the Android Keystore), excluded from backups, and sent only to
`api.airtable.com`. **Check connection** confirms that the token works and that every table and
field the brief relies on still exists.

### 2. Build

Requires Android Studio (Ladybug or newer) or the Android SDK with platform 35, and JDK 17+.

```bash
./gradlew :brief:test          # unit tests for the brief logic
./gradlew :app:assembleDebug   # app/build/outputs/apk/debug/app-debug.apk
```

Install with `adb install app/build/outputs/apk/debug/app-debug.apk`, or open the project in
Android Studio and press Run. A release build needs your own signing key (`*.jks` is gitignored).

## Features

- Brief screen with a card per section and pull-to-refresh plus a refresh button.
- **Call** and **WhatsApp** buttons on any line with a mobile number. Numbers without a country code are assumed to be Indian (+91).
- **No mobile** and **Overdue** badges.
- Tap a line to open that record in Airtable.
- **Share** the brief as plain text, in the same format as the old notification.
- Offline: the last successful brief is saved on the phone and shown at once. A banner appears when it isn't from today.
- Daily notification at about 8:00 AM IST (WorkManager, waits for a network connection). Can be switched off in Settings, which also has a "send now" test button.

## Notes and decisions

- **Field IDs, not names.** Requests use `returnFieldsByFieldId=true` and `fields[]=fld…`, so renaming a column in Airtable doesn't break the app.
- **Filtering happens in the app, not with `filterByFormula`.** Airtable formulas refer to fields by name, so they would break on a rename, and the base is small (about 141 policies, which is 2 pages). The rules also stay in one unit-tested place.
- **Lapsed:** Policy Status has a third option, `Lapsed`, that the original handoff didn't mention. Only `Active` policies count for renewals.
- **Cache:** the last brief is a JSON file in `noBackupFilesDir` instead of a Room database. There is only one document to keep, so Room would add a lot of setup for no benefit.
- **Timing:** WorkManager runs a periodic job anchored to the next 8:00 AM IST. Android may delay it somewhat under Doze or battery saver. Exact alarms would need an extra permission.
