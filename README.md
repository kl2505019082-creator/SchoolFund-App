# School Fund and Event Budgeting Mobile Application

Offline-first Android app for class teachers to manage class funds and event budgets.
Final Year Project 2 - Ridaniel Uwais bin Eizar Azlan, Universiti Poly-Tech Malaysia (FCOM).

## Features
- Record income and expense, with receipt photo (camera or gallery)
- Automatic balance, with a separate balance for each event
- Transaction history, PDF report, backup and restore
- Bahasa Melayu / English and dark mode
- Request Funds module (online, Firebase): teachers send funding requests to PIBG officers

## Tech
Kotlin, Android Studio, SQLite (offline data), Firebase Auth and Firestore (Request Funds only).

## Build
1. Open the project in Android Studio.
2. For Request Funds, add your own `app/google-services.json` from the Firebase console.
   The offline features build and run without it.
3. Run on Android 7.0 (API 24) or newer.

## Demo video
https://www.youtube.com/watch?v=V3LbD2893G0
