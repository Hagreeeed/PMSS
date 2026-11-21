package com.example.lab7.ui.analytics

import com.example.lab7.domain.model.Reading

/** ЛР5: історія з Room. Екран лише показує дані, тому Action у нього немає. */
data class AnalyticsState(val measurements: List<Reading> = emptyList(), val events: List<Reading> = emptyList())
