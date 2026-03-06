# TaffyQSL User Manual

TaffyQSL is an Android application for signing amateur radio QSO logs and uploading them to ARRL's Logbook of The World (LoTW). Inspired by TrustedQSL, it provides a mobile-friendly interface for managing certificates, stations, and QSO records.

> ⚠️ Important
>
> TaffyQSL cannot generate new LoTW certificates.
> You must create your certificate using TrustedQSL (TQSL) and export it as a `.p12` file before importing it into the app.

---


## Installation

### Download

TaffyQSL is available through the following channels:

- **GitHub Releases**: Download the latest APK from the [Releases page](https://github.com/sophiel-meow/TaffyQSL/releases)
- **F-Droid**: Available in the F-Droid repository (coming soon)

### Install

1. Download the APK file to your Android device
2. Enable "Install from Unknown Sources" in your device settings if prompted
3. Open the APK file and follow the installation prompts
4. Grant necessary permissions when requested

---

## Quick Start

### Importing Certificates

Before you can sign QSOs, you must import your LoTW certificate.

**Important Notes:**
- TaffyQSL does **not** support generating new certificates
- You must use **TQSL** (TrustedQSL) to export your certificate as a `.p12` file
- **Password is mandatory**: Due to Android Keystore limitations, you must set a password when exporting from TQSL (even if TQSL allows passwordless export, Android requires one)

#### Steps to Import:

1. **Export from TQSL** (on your computer):
   - Open TQSL
   - Go to "Callsign Certificates"
   - Select your certificate → "Save the Callsign Certificate"
   - Choose a location
   - **Set a password** (required for Android)
   - Save the file and transfer to your phone

2. **Import to TaffyQSL**:
   - Open TaffyQSL
   - Navigate to the **Certificates** tab
   - Tap the **+** (Add) button
   - Select your `.p12` file
   - Enter the password you set during export
   - The certificate will be imported and stored securely in Android Keystore

### Creating a Station

After importing your certificate, create a station profile:

1. Navigate to the **Stations** tab
2. Tap the **+** (Add) button
3. Fill in the required fields:
   - **Station Name**: A friendly name
   - **Callsign**: Select from your imported certificates
   - **DXCC Entity**: Auto-filled from the certificate
   - **Grid Square**: Your Maidenhead locator
   - **State/Province**: Optional, select if applicable
   - **County/City**: Optional, appears if state is selected
   - **CQ Zone / ITU Zone**: Optional
   - **IOTA ID**: Optional
4. Tap **Save**

You can create multiple stations for different operating locations.

---

## Signing QSOs

TaffyQSL offers two methods for signing QSO logs:

### Method 1: Sign External ADIF File (Logs Screen)

Use this method to sign an ADIF file from another logging application:

1. Navigate to the **Logs** tab
2. Select the **Sign Log** sub-tab
3. Tap **Select ADIF File** and choose your `.adi` file
4. Select a **Station** from the dropdown
5. (Optional) Set date filters to sign only QSOs within a date range
6. Tap **Sign**
7. The app will process the file and show results:
   - Total QSOs
   - Successfully signed
   - Duplicates (already in database)
   - Date-filtered
   - Invalid records
8. After signing:
   - **Save .tq8**: Export the signed file for manual upload
   - **Upload to LoTW**: Directly upload to LoTW
   - **Close**: Dismiss the dialog

### Method 2: Sign Internal Log (QSO List Screen)

Use this method to sign QSOs from your internal logbook:

1. Navigate to **Logs** tab → **ADIF Files** sub-tab
2. Tap on a log file to open it
3. In the QSO list, tap the **upload icon** (top-right corner)
4. Select a **Station**
5. (Optional) Set date filters
6. Tap **Sign**
7. Follow the same post-signing options as Method 1

**Note**: Both methods check for duplicates against your local database to avoid re-signing the same QSO.

---

## ADIF Logbook

TaffyQSL maintains an internal ADIF logbook for managing your QSOs.

### File Management (Logs → ADIF Files)

#### Create a New Log File:
1. Go to **Logs** tab → **ADIF Files** sub-tab
2. Tap the **+** button (bottom-right)
3. Select **New ADIF Log**
4. Enter a name (e.g., "2026 Portable Ops")
5. Tap **Create**

#### Import an Existing ADIF File:
1. Tap the **+** button
2. Select **Import ADIF**
3. Choose an `.adi` file from your device
4. Enter a display name
5. Tap **Import**
6. The app will show how many QSOs were imported and how many were skipped (duplicates)

#### Rename or Delete a Log File:
1. Tap the **⋮** (three-dot menu) on a log file card
2. Select **Rename** or **Delete**
3. Confirm your action

### Viewing QSOs (QSO List Screen)

Tap on any log file to view its QSOs:

- QSOs are displayed as cards showing:
  - Callsign
  - Date/Time
  - Band, Mode, Frequency etc
- Scroll through the list
- Tap a QSO card to edit it
- Use the delete button to remove a QSO

---

## QSO Edit

### Manual QSO Editor (QSO Edit Screen)

Access by tapping a QSO card or the **Edit** FAB (small, bottom-right):

#### Auto-Inference Features:
- **Frequency → Band**: When you enter a frequency, the band is automatically determined
- **Satellite → Frequencies**: When you select a satellite, TX/RX frequencies and bands are auto-filled from the satellite database
- **Manual Override**: You can manually edit any auto-filled field; the app will respect your changes

#### Saving:
- Tap **Save** to save changes
- **After saving, the form resets** to allow quick entry of the next QSO (useful for logging multiple contacts in sequence)
- Tap **Cancel** or back button to return without saving

### Quick Add (Quick Add Screen)

Access by tapping the **Add** FAB (large, bottom-right):

Quick Add allows you to enter multiple QSOs using a simplified text format. Enter one QSO per line, and the app will parse the information automatically.

#### Supported Formats:

The parser recognizes tokens in the following priority order:

1. **Satellite Alias**: Common satellite names (e.g., `SO50`, `AO91`, `ISS`)
2. **Mode Keyword**: Mode names (e.g., `SSB`, `CW`, `FT8`, `FM`)
3. **Frequency with Unit**:
   - `14074kHz` or `14.074MHz` or `1.2GHz`
4. **Decimal Frequency**:
   - `14.074` (interpreted as MHz)
5. **Callsign**: Amateur radio callsign pattern
6. **Full Date with Separator**:
   - `2026-03-02` or `2026/03/02`
7. **8-Digit Date**:
   - `20260302`
8. **5-6 Digit Frequency**:
   - `14074` (interpreted as kHz)
9. **Short Date with Separator**:
   - `03-02` or `03/02` (current year assumed)
10. **Time (HH:MM)**:
    - `14:30` (UTC)
11. **4-Digit Ambiguous**:
    - Could be time (`1430`), date (`0302`), or frequency (`1230kHz`)
    - The app will make a best guess and show an **ambiguity chip** for you to clarify

#### Ambiguity Resolution:

When the parser encounters ambiguous 4-digit tokens, it shows a **suggestion chip** below the input:
- Tap the chip to alter through alternatives (e.g., "03:02" → "0302 kHz" → "03-02")
- The input text is **modified in place** and re-parsed

#### Example Inputs:

```
BG4KNN 14.074 FT8 03-02 14:30
18:15 SO50 BG4KNN BG4LWS BI4KSR BG4JPO
BG4KNN 7074kHz FT8 20260308 0945
```

---

## LoTW Query

The LoTW Query feature allows you to check your confirmed and uploaded QSOs directly from ARRL's Logbook of The World.

### Setup (First Time):

1. Navigate to **Settings** tab
2. Scroll to **LoTW Credentials**
3. Tap to open the credentials dialog
4. Enter your **LoTW username** and **password**
5. Tap **Save**
6. Credentials are encrypted and stored securely on your device

### Querying QSOs:

1. Navigate to the **LoTW** tab
2. Configure filters:
   - **QSL (Confirmed) / Uploaded**: Toggle between confirmed QSLs and uploaded QSOs
   - **Band**: Select a specific band or "Any"
   - **Mode**: Select a specific mode or "Any"
   - **Since Date**: Start date for the query
   - **More Filters** (tap to expand):
     - **Own Call**: Filter by your callsign
     - **Worked Call**: Filter by the station you worked
     - **Until Date**: End date for the query
     - **Show DXCC Detail**: Include DXCC entity information (forces "Confirmed" mode)
3. Tap **Query**
4. Results are displayed as cards showing:
   - Callsign
   - Frequency, Mode, Band
   - Date and Time
   - Status badge: "✓ Confirmed" or "Uploaded"

### Notes:

- Queries are sent directly to `https://lotw.arrl.org/lotwuser/lotwreport.adi`
- Results are parsed from ADIF format
- No data is stored locally; each query fetches fresh data from LoTW
- If you see an authentication error, verify your credentials in Settings

---

## Additional Features

### Settings

Access via the **Settings** tab:

- **Use Local Time**: Toggle between UTC and local time for QSO timestamps
- **Date Format**: Choose your preferred date format
- **Language**: Change app language (Android 13+)
- **LoTW Credentials**: Manage your LoTW username/password
- **Debug Mode**: Enable additional logging (debug builds only)
- **About**: View app version, source code, and license
- **Open Source Licenses**: View third-party library licenses

### Backup and Export

- **Certificate Backup**: Certificates are stored in Android Keystore and can be exported as encrypted `.p12` files
- **ADIF Export**: Export any log file or signed `.tq8` file
- **Database**: QSO records are stored in a local SQLite database

---

## Support

- **GitHub Issues**: [https://github.com/sophiel-meow/TaffyQSL/issues](https://github.com/sophiel-meow/TaffyQSL/issues)
- **Source Code**: [https://github.com/sophiel-meow/TaffyQSL](https://github.com/sophiel-meow/TaffyQSL)
- **License**: GNU General Public License v3.0

---

## Credits

TaffyQSL is inspired by **TrustedQSL (TQSL)** by ARRL. Special thanks to the amateur radio community for their support and feedback.

73,
The TaffyQSL Project

