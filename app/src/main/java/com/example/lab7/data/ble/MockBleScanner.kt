package com.example.lab7.data.ble

import kotlinx.coroutines.delay

class MockBleScanner {
    suspend fun scan(): List<BleDevice> {
        delay(2_000)
        return listOf(BleProfile.DEVICE)
    }
}
