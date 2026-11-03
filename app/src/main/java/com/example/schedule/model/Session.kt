package com.example.schedule.model

import com.google.gson.annotations.SerializedName

data class Session(
    @SerializedName("sessionName") val sessionName: String,
    @SerializedName("sessionTime") val sessionTime: String,
    @SerializedName("dayOffset")   val dayOffset: Int = 0,
    /** Optional display name from the JSON (e.g. "SQ"). Overrides the built-in label. */
    @SerializedName("label")       val label: String? = null,
    /** Optional length in minutes. Built-in defaults are used when missing. */
    @SerializedName("duration")    val duration: Int? = null
)
