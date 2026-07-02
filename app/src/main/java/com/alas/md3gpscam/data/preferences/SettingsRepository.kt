package com.alas.md3gpscam.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    companion object {
        val SELECTED_TEMPLATE = stringPreferencesKey("selected_template")
        val GRID_LINES_ENABLED = booleanPreferencesKey("grid_lines_enabled")
        val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
        val TIMER_SECONDS = intPreferencesKey("timer_seconds")
        val IMAGE_RATIO = stringPreferencesKey("image_ratio")
        val MAP_TYPE = stringPreferencesKey("map_type")
        val HAPTIC_FEEDBACK_ENABLED = booleanPreferencesKey("haptic_feedback_enabled")
        val SPEED_UNIT = stringPreferencesKey("speed_unit")
        val ALTITUDE_UNIT = stringPreferencesKey("altitude_unit")
        val RECORD_AUDIO_IN_VIDEO = booleanPreferencesKey("record_audio_in_video")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLORS_ENABLED = booleanPreferencesKey("dynamic_colors_enabled")
        val CUSTOM_NOTE = stringPreferencesKey("custom_note")
        val PHOTO_QUALITY = stringPreferencesKey("photo_quality")
        val VIDEO_QUALITY = stringPreferencesKey("video_quality")
        val STAMP_MAP_TYPE = stringPreferencesKey("stamp_map_type")
    }

    val photoQuality: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[PHOTO_QUALITY] ?: "medium"
    }

    val videoQuality: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[VIDEO_QUALITY] ?: "1080p"
    }

    val stampMapType: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[STAMP_MAP_TYPE] ?: "standard"
    }

    val customNote: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[CUSTOM_NOTE] ?: ""
    }

    val selectedTemplate: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[SELECTED_TEMPLATE] ?: "basic"
    }

    val gridLinesEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[GRID_LINES_ENABLED] ?: false
    }

    val soundEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[SOUND_ENABLED] ?: true
    }

    val timerSeconds: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[TIMER_SECONDS] ?: 0
    }

    val imageRatio: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[IMAGE_RATIO] ?: "4:3"
    }

    val mapType: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[MAP_TYPE] ?: "normal"
    }

    val hapticFeedbackEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[HAPTIC_FEEDBACK_ENABLED] ?: true
    }

    val speedUnit: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[SPEED_UNIT] ?: "kmh"
    }

    val altitudeUnit: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[ALTITUDE_UNIT] ?: "meters"
    }

    val recordAudioInVideo: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[RECORD_AUDIO_IN_VIDEO] ?: true
    }

    val themeMode: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[THEME_MODE] ?: "system"
    }

    val dynamicColorsEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[DYNAMIC_COLORS_ENABLED] ?: true
    }

    suspend fun setSelectedTemplate(templateId: String) {
        context.dataStore.edit { preferences ->
            preferences[SELECTED_TEMPLATE] = templateId
        }
    }

    suspend fun setGridLinesEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[GRID_LINES_ENABLED] = enabled
        }
    }

    suspend fun setSoundEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SOUND_ENABLED] = enabled
        }
    }

    suspend fun setTimerSeconds(seconds: Int) {
        context.dataStore.edit { preferences ->
            preferences[TIMER_SECONDS] = seconds
        }
    }

    suspend fun setImageRatio(ratio: String) {
        context.dataStore.edit { preferences ->
            preferences[IMAGE_RATIO] = ratio
        }
    }

    suspend fun setMapType(type: String) {
        context.dataStore.edit { preferences ->
            preferences[MAP_TYPE] = type
        }
    }

    suspend fun setHapticFeedbackEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[HAPTIC_FEEDBACK_ENABLED] = enabled
        }
    }

    suspend fun setSpeedUnit(unit: String) {
        context.dataStore.edit { preferences ->
            preferences[SPEED_UNIT] = unit
        }
    }

    suspend fun setAltitudeUnit(unit: String) {
        context.dataStore.edit { preferences ->
            preferences[ALTITUDE_UNIT] = unit
        }
    }

    suspend fun setRecordAudioInVideo(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[RECORD_AUDIO_IN_VIDEO] = enabled
        }
    }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[THEME_MODE] = mode
        }
    }

    suspend fun setDynamicColorsEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[DYNAMIC_COLORS_ENABLED] = enabled
        }
    }

    suspend fun setCustomNote(note: String) {
        context.dataStore.edit { preferences ->
            preferences[CUSTOM_NOTE] = note
        }
    }

    suspend fun setPhotoQuality(quality: String) {
        context.dataStore.edit { preferences ->
            preferences[PHOTO_QUALITY] = quality
        }
    }

    suspend fun setVideoQuality(quality: String) {
        context.dataStore.edit { preferences ->
            preferences[VIDEO_QUALITY] = quality
        }
    }

    suspend fun setStampMapType(type: String) {
        context.dataStore.edit { preferences ->
            preferences[STAMP_MAP_TYPE] = type
        }
    }
}
