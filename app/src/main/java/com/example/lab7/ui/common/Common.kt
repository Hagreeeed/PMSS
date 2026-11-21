package com.example.lab7.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.lab7.LightTrackerApp
import com.example.lab7.di.AppContainer
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Фабрика ViewModel, яка бере залежності з AppContainer. */
inline fun <reified VM : ViewModel> factory(crossinline create: (AppContainer) -> VM): ViewModelProvider.Factory =
    viewModelFactory { initializer { create((this[APPLICATION_KEY] as LightTrackerApp).container) } }

val Green = Color(0xFF2E7D32)
val Red = Color(0xFFC62828)
val Amber = Color(0xFFFFB300)
val Grey = Color(0xFF78909C)

@Composable
fun StatusDot(color: Color, size: Dp = 14.dp) {
    Box(Modifier.size(size).background(color, CircleShape))
}

fun lux(value: Float?): String = value?.let { String.format(Locale.US, "%.1f", it) } ?: "—"

fun percent(value: Float): String = "${(value * 100).toInt()} %"

private val timeFormat = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())
fun time(millis: Long): String = timeFormat.format(Instant.ofEpochMilli(millis))
