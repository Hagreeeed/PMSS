package com.example.lab4.ble

import kotlinx.coroutines.delay

class MockBleScanner {
    suspend fun scan(): List<BleDevice> {
        // Suspending delay leaves the UI thread free and supports cancellation.
        delay(2_000)
        return listOf(BleProfile.DEVICE)
    }
}
