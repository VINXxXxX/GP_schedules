package com.example.schedule.model

import com.google.gson.annotations.SerializedName

data class Session(
    @SerializedName("sessionName") val sessionName: String,
    @SerializedName("sessionTime") val sessionTime: String,
    @SerializedName("dayOffset")   val dayOffset: Int = 0
)