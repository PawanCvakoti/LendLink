package com.lendlink.data.local

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.lendlink.data.model.DamageReport

class Converters {
    @TypeConverter
    fun fromDamageReport(value: DamageReport?): String? {
        return Gson().toJson(value)
    }

    @TypeConverter
    fun toDamageReport(value: String?): DamageReport? {
        return Gson().fromJson(value, DamageReport::class.java)
    }
}
