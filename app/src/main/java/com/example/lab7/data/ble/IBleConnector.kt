package com.example.lab7.data.ble

import kotlinx.coroutines.flow.StateFlow

/** UI працює з цим інтерфейсом, тому mock можна замінити на BluetoothGatt. */
interface IBleConnector {
    val state: StateFlow<BleUiState>
    fun scan()
    fun connect()
    /** Пошук і підключення до першого знайденого хаба. */
    fun quickConnect()
    fun disconnect()
    fun sendData(data: String)
    fun readData()
    fun setNotifications(enabled: Boolean)
    fun simulateInterference()
}
