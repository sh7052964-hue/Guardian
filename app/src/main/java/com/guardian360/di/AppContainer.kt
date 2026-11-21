package com.guardian360.di

import android.content.Context
import com.example.R
import com.google.firebase.firestore.FirebaseFirestore
import com.guardian360.common.PreferencesManager
import com.guardian360.database.GuardianRoomDatabase
import com.guardian360.deviceowner.DevicePolicyController
import com.guardian360.firebase.GuardianFirestoreRepository
import com.guardian360.location.LocationTracker
import com.guardian360.notifications.NotificationHelper
import com.guardian360.screentime.UsageStatsTracker

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val databaseId: String by lazy { appContext.getString(R.string.firestore_database_id) }
    val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance(databaseId) }
    val firestoreRepository: GuardianFirestoreRepository by lazy { GuardianFirestoreRepository(firestore) }
    val roomDatabase: GuardianRoomDatabase by lazy { GuardianRoomDatabase.getInstance(appContext) }
    val preferencesManager: PreferencesManager by lazy { PreferencesManager(appContext) }
    val devicePolicyController: DevicePolicyController by lazy { DevicePolicyController(appContext) }
    val usageStatsTracker: UsageStatsTracker by lazy { UsageStatsTracker(appContext) }
    val locationTracker: LocationTracker by lazy { LocationTracker(appContext) }
    val notificationHelper: NotificationHelper = NotificationHelper(appContext)
}
