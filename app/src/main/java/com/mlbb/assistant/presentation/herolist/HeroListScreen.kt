// File: app/src/main/java/com/mlbb/assistant/presentation/herolist/HeroListScreen.kt
package com.mlbb.assistant.presentation.herolist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.mlbb.assistant.presentation.common.components.LoadingSpinner
import com.mlbb.assistant.presentation.common.components.MLBBTextField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeroListScreen(
    navController: NavController,
    viewModel: HeroListViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.loadHeroes()
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Hero Browser") }) }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            MLBBTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = "Search hero",
                modifier = Modifier.padding(16.dp)
            )
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    state.isLoading -> LoadingSpinner()
                    state.heroes.isNotEmpty() -> {
                        val filteredHeroes = state.heroes.filter {
                            it.name.contains(searchQuery, ignoreCase = true)
                        }
                        LazyColumn {
                            items(filteredHeroes) { hero ->
                                HeroCard(hero = hero)
                            }
                        }
                    }
                    state.error != null -> Text("Error: ${state.error}")
                    else -> Text("No heroes available")
                }
            }
        }
    }
}

@Composable
fun HeroCard(hero: com.mlbb.assistant.domain.model.Hero) {
    Card(modifier = Modifier.padding(8.dp)) {
        Column(modifier = Modifier.padding(8.dp)) {
            AsyncImage(
                model = hero.imageUrl,
                contentDescription = hero.name,
                modifier = Modifier.fillMaxSize()
            )
            Text(text = hero.name, style = MaterialTheme.typography.titleMedium)
            Text(text = "Role: ${hero.role}", style = MaterialTheme.typography.bodySmall)
            Text(text = "Win Rate: ${String.format("%.1f", hero.winRate * 100)}%")
            Text(text = "Pick Rate: ${String.format("%.1f", hero.pickRate * 100)}%")
            Text(text = "Ban Rate: ${String.format("%.1f", hero.banRate * 100)}%")
        }
    }
}