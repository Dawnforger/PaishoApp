package com.paisho.app.storage

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class PersistedSettingsDto(
    val themeMode: String = "LIGHT",
    val showHarmonyLines: Boolean = true,
    val showMoveHints: Boolean = true,
)

@Serializable
data class PersistedServerProfileDto(
    val id: String,
    val name: String,
    val baseUrl: String,
    val playerId: String,
    val playerName: String = "",
    val token: String? = null,
    val lastGameId: String? = null,
    val serverVersion: Int? = null,
)

@Serializable
data class PersistedUserStateDto(
    val settings: PersistedSettingsDto = PersistedSettingsDto(),
    val servers: List<PersistedServerProfileDto> = emptyList(),
    val selectedServerId: String? = null,
)

class LocalUserStateStore(
    private val context: Context,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }
    private val ioMutex = Mutex()
    private val stateFile: File by lazy { File(context.filesDir, "paisho-user-state.json") }

    suspend fun load(): PersistedUserStateDto = withContext(Dispatchers.IO) {
        ioMutex.withLock {
            if (!stateFile.exists()) return@withLock PersistedUserStateDto()
            val raw = runCatching { stateFile.readText() }.getOrDefault("")
            if (raw.isBlank()) return@withLock PersistedUserStateDto()
            runCatching { json.decodeFromString<PersistedUserStateDto>(raw) }
                .getOrElse { PersistedUserStateDto() }
        }
    }

    suspend fun save(state: PersistedUserStateDto) = withContext(Dispatchers.IO) {
        ioMutex.withLock {
            stateFile.parentFile?.mkdirs()
            val tmp = File(stateFile.parentFile, "${stateFile.name}.tmp")
            tmp.writeText(json.encodeToString(PersistedUserStateDto.serializer(), state))
            if (stateFile.exists()) stateFile.delete()
            tmp.renameTo(stateFile)
        }
    }
}
