package com.example.tubelight

import android.content.Context
import org.json.JSONObject

/** User settings, saved on the device. */
class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("tubelight", Context.MODE_PRIVATE)

    var blockAds: Boolean
        get() = sp.getBoolean("blockAds", true)
        set(v) = sp.edit().putBoolean("blockAds", v).apply()

    var backgroundPlay: Boolean
        get() = sp.getBoolean("backgroundPlay", true)
        set(v) = sp.edit().putBoolean("backgroundPlay", v).apply()

    var hideShorts: Boolean
        get() = sp.getBoolean("hideShorts", false)
        set(v) = sp.edit().putBoolean("hideShorts", v).apply()

    var hideComments: Boolean
        get() = sp.getBoolean("hideComments", false)
        set(v) = sp.edit().putBoolean("hideComments", v).apply()

    var loop: Boolean
        get() = sp.getBoolean("loop", false)
        set(v) = sp.edit().putBoolean("loop", v).apply()

    var speed: Float
        get() = sp.getFloat("speed", 1f)
        set(v) = sp.edit().putFloat("speed", v).apply()

    /** Not saved: set by the "End of video" sleep timer option. */
    var sleepAtEnd: Boolean = false

    fun toJson(): String = JSONObject()
        .put("blockAds", blockAds)
        .put("backgroundPlay", backgroundPlay)
        .put("hideShorts", hideShorts)
        .put("hideComments", hideComments)
        .put("loop", loop)
        .put("speed", speed.toDouble())
        .put("sleepAtEnd", sleepAtEnd)
        .toString()
}
