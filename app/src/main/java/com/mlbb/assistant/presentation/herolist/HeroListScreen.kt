package com.mlbb.assistant.presentation.herolist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.mlbb.assistant.domain.model.Hero
import com.mlbb.assistant.presentation.common.components.LoadingSpinner
import com.mlbb.assistant.presentation.common.components.MLBBTextField
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeroListScreen(viewModel: HeroListViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { viewModel.loadHeroes() }

    // Show non-fatal error as snackbar when heroes are already loaded
    LaunchedEffect(state.error) {
        if (state.error != null && state.heroes.isNotEmpty()) {
            snackbarHostState.showSnackbar("Sync failed: ${state.error}")
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Hero Browser") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            MLBBTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = "Search hero",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    state.isLoading -> LoadingSpinner()
                    state.heroes.isNotEmpty() -> {
                        val filteredHeroes = remember(state.heroes, searchQuery) {
                            state.heroes.filter {
                                it.name.contains(searchQuery, ignoreCase = true)
                            }
                        }
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(filteredHeroes, key = { it.id }) { hero ->
                                HeroCard(hero = hero)
                            }
                        }
                    }
                    state.error != null -> Text(
                        text = "Error: ${state.error}",
                        modifier = Modifier.padding(16.dp)
                    )
                    else -> Text(
                        text = "No heroes available",
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun HeroCard(hero: Hero) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            AsyncImage(
                model = hero.imageUrl,
                contentDescription = hero.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
            )
            Text(text = hero.name, style = MaterialTheme.typography.titleMedium)
            Text(text = "Role: ${hero.role}", style = MaterialTheme.typography.bodySmall)
            Text(text = "Win Rate: ${String.format(Locale.US, "%.1f", hero.winRate * 100)}%")
            Text(text = "Pick Rate: ${String.format(Locale.US, "%.1f", hero.pickRate * 100)}%")
            Text(text = "Ban Rate: ${String.format(Locale.US, "%.1f", hero.banRate * 100)}%")
        }
    }
}
