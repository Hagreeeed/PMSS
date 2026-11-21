package com.example.lab5.history

import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class SensorRepository(private val dao: SensorDao) {
    val measurements = dao.observeLatest(actionTriggered = false, limit = 50)
        .distinctUntilChanged().map { it.asReversed() }
    val activations = dao.observeLatest(actionTriggered = true, limit = 50).distinctUntilChanged()

    suspend fun record(record: SensorRecord) = dao.insert(record)
}
