package com.example.lab2.ble

import kotlinx.coroutines.flow.StateFlow

/** UI depends on this contract; the mock can later be replaced with BluetoothGatt. */
interface IBleConnector {
    val state: StateFlow<BleUiState>
    fun scan()
    fun connect()
    fun disconnect()
    fun sendData(data: String)
    fun readData()
    fun setNotifications(enabled: Boolean)
    fun simulateInterference()
}
