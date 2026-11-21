package com.example.lab4.cloud

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.google.gson.reflect.TypeToken
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidCloudStore(context: Context) : PendingStore {
    private val preferences = context.getSharedPreferences("cloud_outbox", Context.MODE_PRIVATE)
    val deviceId: String = preferences.getString("device_id", null) ?: UUID.randomUUID().toString().also {
        check(preferences.edit().putString("device_id", it).commit())
    }

    override suspend fun load(): List<SensorData> = withContext(Dispatchers.IO) {
        val json = preferences.getString("pending", "[]") ?: "[]"
        // Do not silently clear an unreadable outbox.
        CloudClient.gson.fromJson<List<SensorData>>(json, object : TypeToken<List<SensorData>>() {}.type)
            ?: throw IOException("Unreadable outbox")
    }

    override suspend fun save(samples: List<SensorData>) = withContext(Dispatchers.IO) {
        if (!preferences.edit().putString("pending", CloudClient.gson.toJson(samples)).commit()) {
            throw IOException("Cannot persist Pending queue")
        }
    }
}

class NetworkStatus(context: Context) {
    private val manager = context.getSystemService(ConnectivityManager::class.java)
    fun isOnline(): Boolean {
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
