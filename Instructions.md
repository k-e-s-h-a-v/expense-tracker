Android SMS Expense Tracker Setup

How to use this code in Android Studio:

Create a new Project: Open Android Studio and select New Project -> Empty Activity (Make sure Jetpack Compose is enabled, which is default in modern Android Studio versions).

Language: Select Kotlin.

Copy the Activity: Replace the contents of your default MainActivity.kt with the ExpenseTrackerActivity.kt code provided above (be sure to fix the package com.example.expensetracker line at the very top to match your actual package name).

Update Manifest: Open your AndroidManifest.xml and ensure you add the <uses-permission android:name="android.permission.READ_SMS" /> line exactly as shown in the file generated.

Dependencies: Make sure your app/build.gradle (or build.gradle.kts) contains the Compose ViewModel dependency (usually included by default, but if not, add it):

implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2")


How the logic works:

Reading: It queries content://sms/inbox using Android's ContentResolver.

Regex Filtering: It checks if the message has words like debited, spent, or paid, and then uses a Regex pattern ((?i)(?:rs\\.?|inr)\\s?([\\d,]+\\.?\\d*)) to pluck out the exact numerical amount.

Remembering Merchants: When you tap on a transaction, a dialog pops up asking you to assign a Merchant name. This is saved to SharedPreferences linked to the Sender ID (e.g., VM-HDFCBK -> Amazon). The next time the app reads your messages, it automatically applies "Amazon" to all messages from that sender!