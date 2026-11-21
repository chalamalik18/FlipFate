package com.flipfate.lbsroc.entry

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.entryStore by preferencesDataStore(name = "launch_preferences")

internal class EntrySettings(context: Context) {
    private val store = context.applicationContext.entryStore
    private val credentialKey = stringPreferencesKey("access_token")
    private val pageKey = stringPreferencesKey("resource_address")

    suspend fun savedPage(): String? {
        val values = store.data.first()
        return values[pageKey]?.takeIf { it.isNotBlank() }
    }

    suspend fun savePage(destination: BrowserDestination) {
        store.edit { values ->
            val credential = destination.credential
            if (credential == null) values.remove(credentialKey) else values[credentialKey] = credential
            values[pageKey] = destination.url
        }
    }
}
