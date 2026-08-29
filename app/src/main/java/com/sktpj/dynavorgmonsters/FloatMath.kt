package com.sktpj.dynavorgmonsters

// Keep particle trigonometry in Float world coordinates while delegating to Kotlin's Double math.
internal fun cos(value: Float): Float = kotlin.math.cos(value.toDouble()).toFloat()
internal fun sin(value: Float): Float = kotlin.math.sin(value.toDouble()).toFloat()
