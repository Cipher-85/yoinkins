package com.apkpackager.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "last_known_good")

@Singleton
class LastKnownGoodStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private fun key(owner: String, repo: String, branch: String) =
        stringPreferencesKey("$owner/$repo/$branch")

    fun observe(owner: String, repo: String, branch: String): Flow<String?> =
        context.dataStore.data.map { it[key(owner, repo, branch)] }

    suspend fun set(owner: String, repo: String, branch: String, sha: String) {
        context.dataStore.edit { it[key(owner, repo, branch)] = sha }
    }

    suspend fun clear(owner: String, repo: String, branch: String) {
        context.dataStore.edit { it.remove(key(owner, repo, branch)) }
    }
}
