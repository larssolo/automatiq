---
title: Automatiq — Privacy Policy
---

# Automatiq — Privacy Policy

_Last updated: 15 October 2026_

Automatiq ("the app") is an SMS automation app for Android, published by Lars Sohl, founder of kodekid.app ("we"). This policy explains what data the app uses, what leaves your phone, and why.

**In short:** Automatiq works on your phone. There is no account, no server of ours that stores your messages, no ads and no analytics. Text leaves your phone in only two cases, both started by you: when you use an **AI feature** (sent to Google's Gemini API with your own key), and when you **report an AI-written text** to us.

## 1. Data the app uses on your phone

To do its job, the app reads and stores the following **only on your device**:

| Data | Why |
|---|---|
| Your macros (names, messages, recipient phone numbers, schedules, settings) | To send the messages you set up. Stored in the app's private database. |
| Incoming SMS messages | To match auto-reply macros (sender / keyword) and reply to them. Messages are not stored, except a short preview of what the app itself sent, in the app's log. |
| Phone state and call log | To detect a missed or declined call and text the caller back (missed-call macros). |
| Contacts | To show contact names for recipient numbers and personalise messages (`{modtager}`). Contacts are read on the device and never uploaded. |
| Bluetooth and Wi-Fi state, charging state | For device-state triggers (e.g. "when my car's Bluetooth connects"). |
| Notifications, exact alarms, battery-optimisation exemption | So scheduled messages are sent on time and you can see/approve what was sent. |

None of this is sent to us.

**Android backup.** If you have Android backup turned on, Android may include the app's data (your macros and settings) in **your own** Google backup, encrypted by Android. Your Gemini API key and AI settings are excluded from this backup. We have no access to your Android backup.

**Export/Import.** The app can export a backup file of your macros at your request. That file is saved wherever you choose. Your API key is only included if you explicitly turn that option on.

## 2. Data that leaves your phone

### 2.1 AI features — Google Gemini (optional)

AI features are off until you add your own Gemini API key **and** agree to an in-app notice. When you use them, the app sends text directly from your phone to Google's Gemini API:

- **AI auto-replies:** the text of the incoming SMS you are replying to, and your optional instruction.
- **AI variations:** your macro's message, and your optional instruction.
- **AI compose:** the description you type.

Phone numbers and contact names are **not** sent. The request goes directly to Google using **your** API key — it does not pass through us, and we never see it. Google processes it under the [Gemini API Additional Terms of Service](https://ai.google.dev/gemini-api/terms) and [Google's Privacy Policy](https://policies.google.com/privacy). You can stop at any time by removing your key in **Settings → AI**.

### 2.2 Reporting an AI-written text (optional)

If an AI-written text is offensive, harmful or wrong, you can tap **Report**. The app then sends us:

- the AI-written text you reported,
- the reason you picked and your optional note,
- the AI model name and the app version.

No phone numbers, contact names or incoming messages are included, and no identifier of you or your device. Reports are stored in a database hosted by Neon (EU, Frankfurt) and used only to improve the app's AI safeguards. They are deleted when no longer needed, and at the latest after 12 months. If a report can't be sent right away, the app keeps it on your phone and retries automatically.

### 2.3 Location (only in the separately distributed "full" version)

The version of Automatiq on Google Play does **not** use location. A separately distributed version offers a location trigger; there, the place you set and your location are processed on your device by Google Play services geofencing and are not sent to us.

## 3. What we don't do

- We don't sell or share your data with anyone for advertising.
- We don't use analytics, ads or tracking SDKs.
- We don't require an account.

## 4. Security

All data sent off your phone (Gemini requests, reports) is encrypted in transit (HTTPS). Data on your phone is kept in the app's private storage, which other apps cannot read.

## 5. Children

Automatiq is not directed at children under 13 and is not designed for them.

## 6. Your choices and rights

- Don't add a Gemini key, or remove it, to keep all text on your phone.
- Uninstalling the app deletes all data the app stored on your phone.
- Reports contain no identifier of you, so we cannot link a report to you. If you want a report deleted, contact us with its text and roughly when you sent it, and we will delete it.

If you are in the EU/EEA, you have rights under the GDPR, including access, rectification, erasure and the right to complain to your data-protection authority (in Denmark: Datatilsynet).

## 7. Changes

We will update this page if the app's data handling changes, and change the date at the top.

## 8. Contact

Lars Sohl, founder of kodekid.app — hello@larssohl.dk
