package com.example.lab7.di

import android.content.Context
import android.util.Log
import com.example.lab7.BuildConfig
import com.example.lab7.data.ble.MockBleConnector
import com.example.lab7.data.device.AndroidActuator
import com.example.lab7.data.device.AndroidNetworkMonitor
import com.example.lab7.data.device.AndroidSensorSource
import com.example.lab7.data.device.TfliteLightClassifier
import com.example.lab7.data.local.AppDatabase
import com.example.lab7.data.remote.ApiFactory
import com.example.lab7.data.remote.HubCommandSource
import com.example.lab7.data.repository.DefaultReadingRepository
import com.example.lab7.data.repository.DefaultRuleRepository
import com.example.lab7.domain.ReadingRepository
import com.example.lab7.domain.RuleRepository
import com.example.lab7.domain.SmartPipeline
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Manual DI: усі залежності створюються тут один раз на процес.
 * Екрани отримують готові об'єкти через фабрики ViewModel і нічого не створюють самі.
 */
class AppContainer(context: Context) {
    private val app = context.applicationContext

    /** Область життя застосунку: не тримає посилань на Activity. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val database = AppDatabase.create(app)
    private val cloudApi = ApiFactory.create(BuildConfig.HUB_URL) { Log.d("LightHttp", it) }

    private val deviceId: String = app.getSharedPreferences("device", Context.MODE_PRIVATE).let { prefs ->
        prefs.getString("id", null) ?: UUID.randomUUID().toString().also { prefs.edit().putString("id", it).apply() }
    }

    val readings: ReadingRepository = DefaultReadingRepository(database.readingDao(), cloudApi, deviceId)
    val rules: RuleRepository = DefaultRuleRepository(database.ruleDao())
    val ble = MockBleConnector(appScope, logger = { Log.i("LightBle", it) })

    val pipeline = SmartPipeline(
        sensors = AndroidSensorSource(app),
        classifierFactory = { TfliteLightClassifier(app) },
        readings = readings,
        rules = rules,
        network = AndroidNetworkMonitor(app),
        actuator = AndroidActuator(app) { Log.w("LightActuator", it) },
        hub = ble,
        remote = HubCommandSource(cloudApi),
        scope = appScope,
        computeDispatcher = Dispatchers.Default,
        logger = { Log.i("LightSystem", it) },
    )
}
