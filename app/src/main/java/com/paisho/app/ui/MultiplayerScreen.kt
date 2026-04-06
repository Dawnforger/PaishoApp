package com.paisho.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun MultiplayerScreen(
    state: MultiplayerSession,
    games: List<ExistingGameSummary>,
    onConfigure: (baseUrl: String, playerId: String, playerName: String) -> Unit,
    onCreateOnlineGame: () -> Unit,
    onRefreshOnlineGame: () -> Unit,
    onJoinOnlineGame: (String) -> Unit,
    onOpenLocalSetup: () -> Unit,
) {
    var baseUrl by remember(state.baseUrl) { mutableStateOf(state.baseUrl.orEmpty()) }
    var playerId by remember(state.playerId) { mutableStateOf(state.playerId.orEmpty()) }
    var playerName by remember(state.playerName) { mutableStateOf(state.playerName.orEmpty()) }
    var joinGameId by remember(state.joinGameIdInput) { mutableStateOf(state.joinGameIdInput) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Multiplayer (Correspondence)", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "Configure your NAS server URL and player identity, then login/create/join.",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it },
                        label = { Text("Server URL (e.g. http://192.168.1.50:8080)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = playerId,
                        onValueChange = { playerId = it },
                        label = { Text("Player ID (unique)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = playerName,
                        onValueChange = { playerName = it },
                        label = { Text("Display name (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onConfigure(baseUrl.trim(), playerId.trim(), playerName.trim()) }
                        ) {
                            Text("Save + Login")
                        }
                        TextButton(onClick = onOpenLocalSetup) {
                            Text("Local Game Setup")
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Status: ${if (state.configured) "configured" else "not configured"} | " +
                            "Token: ${if (state.token != null) "ready" else "missing"}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "Current online game: ${state.gameId ?: "none"}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (!state.lastError.isNullOrBlank()) {
                        Text(
                            "Last error: ${state.lastError}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Online actions", fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onCreateOnlineGame, enabled = state.configured) {
                            Text("Create Online Game")
                        }
                        Button(onClick = onRefreshOnlineGame, enabled = state.gameId != null) {
                            Text("Refresh")
                        }
                    }
                    OutlinedTextField(
                        value = joinGameId,
                        onValueChange = { joinGameId = it },
                        label = { Text("Join by Game ID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = { onJoinOnlineGame(joinGameId.trim()) },
                        enabled = state.configured && joinGameId.isNotBlank()
                    ) {
                        Text("Join Online Game")
                    }
                }
            }
        }

        item {
            Text("Saved local games", fontWeight = FontWeight.SemiBold)
        }
        if (games.isEmpty()) {
            item { Text("No saved local games yet.") }
        } else {
            items(games) { game ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(game.title, fontWeight = FontWeight.Medium)
                        Text(game.subtitle, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
