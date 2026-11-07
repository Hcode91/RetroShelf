package io.retroshelf

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import coil.compose.AsyncImage
import coil.imageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { RetroShelf() } }
}
private val darkScheme = darkColorScheme(primary = Color(0xFFBAABFF), secondary = Color(0xFF7CF4C0), background = Color(0xFF090E1B), surface = Color(0xFF141B2D), surfaceVariant = Color(0xFF1B2438))
private val lightScheme = lightColorScheme(primary = Color(0xFF6442B9), secondary = Color(0xFF006B56))
@Composable
fun RetroShelf(vm: ShelfViewModel = viewModel()) {
    val dark by vm.dark.collectAsStateWithLifecycle()
    MaterialTheme(colorScheme = if(dark) darkScheme else lightScheme) {
        val nav = rememberNavController()
        val snack = remember { SnackbarHostState() }
        val entry by nav.currentBackStackEntryAsState()
        val route = entry?.destination?.route
        LaunchedEffect(vm) { vm.messages.collect { snack.showSnackbar(it) } }
        Scaffold(containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snack) },
            topBar = { @OptIn(ExperimentalMaterial3Api::class) TopAppBar(title = { Text("RetroShelf", fontWeight = FontWeight.ExtraBold) }, navigationIcon = {
                if(route !in listOf("home", "search", "favorites", "downloads", "settings")) IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            }) },
            bottomBar = { NavigationBar {
                listOf(Triple("home", "Home", Icons.Default.Home), Triple("search", "Search", Icons.Default.Search), Triple("favorites", "Saved", Icons.Default.Favorite), Triple("downloads", "Downloads", Icons.Default.Download), Triple("settings", "Settings", Icons.Default.Settings)).forEach { (path, label, icon) ->
                    NavigationBarItem(selected = route == path, onClick = { nav.navigate(path) { popUpTo("home") { saveState = true }; launchSingleTop = true; restoreState = true } }, icon = { Icon(icon, label) }, label = { Text(label, maxLines = 1) })
                }
            } }) { inset ->
            NavHost(nav, startDestination = "home", modifier = Modifier.padding(inset)) {
                composable("home") { HomeScreen(vm, nav) }
                composable("console/{console}") { SourcesScreen(it.arguments!!.getString("console")!!, vm, nav) }
                composable("catalogue/{console}/{provider}") { CatalogueScreen(it.arguments!!.getString("console")!!, it.arguments!!.getString("provider")!!, vm, nav) }
                composable("game/{id}") { DetailsScreen(it.arguments!!.getString("id")!!, vm) }
                composable("search") { SearchScreen(vm, nav) }
                composable("favorites") {
                    val games by vm.favorites.collectAsStateWithLifecycle()
                    GameCollection("Favorites", games, "Save games with the heart on their details page.") { openGame(it, vm, nav) }
                }
                composable("downloads") { DownloadsScreen(vm) }
                composable("settings") { SettingsScreen(vm) }
            }
        }
    }
}
private fun openGame(game: Game, vm: ShelfViewModel, nav: NavHostController) { vm.view(game); nav.navigate("game/${Uri.encode(game.id)}") }
@Composable private fun Intro(title: String, text: String) {
    Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surfaceVariant)), RoundedCornerShape(24.dp)).padding(24.dp)) {
        Text("YOUR NEXT HOMEBREW DISCOVERY", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp)); Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp)); Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
@Composable private fun HomeScreen(vm: ShelfViewModel, nav: NavHostController) {
    val recent by vm.recent.collectAsStateWithLifecycle()
    LazyVerticalGrid(columns = GridCells.Adaptive(148.dp), contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item(span = { GridItemSpan(maxLineSpan) }) { Intro("Small games.\nNew worlds.", "Choose a console, discover a community source, and build your shelf.") }
        Consoles.all.groupBy { it.family }.forEach { (family, consoles) ->
            item(span = { GridItemSpan(maxLineSpan) }) { Text(family, Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleLarge) }
            items(consoles, key = { it.id }) { console ->
                ElevatedCard(onClick = { nav.navigate("console/${console.id}") }, modifier = Modifier.fillMaxWidth().heightIn(min = 130.dp)) {
                    Column(Modifier.padding(18.dp)) {
                        Icon(Icons.Default.SportsEsports, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.height(12.dp)); Text(console.name, fontWeight = FontWeight.Bold)
                        val count = vm.graph.registry.forConsole(console.id).size
                        Text(if(count == 0) "Sources coming soon" else "$count supported ${if(count == 1) "source" else "sources"}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if(recent.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { Text("Recently viewed", style = MaterialTheme.typography.titleLarge) }
            items(recent.take(8), key = { "recent-${it.id}" }) { GameCard(it, false) { openGame(it, vm, nav) } }
        }
    }
}
@Composable private fun SourcesScreen(console: String, vm: ShelfViewModel, nav: NavHostController) {
    val providers = vm.graph.registry.forConsole(console)
    val disabled by vm.disabled.collectAsStateWithLifecycle()
    val statuses by vm.status.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Intro(Consoles.all.first { it.id == console }.name, "Choose a source website") }
        if(providers.isEmpty()) item { EmptyState("No supported source yet", "This console is registered. A verified, authorized provider can be added through the adapter interface.") }
        providers.groupBy { it.section }.forEach { (section, sources) ->
            item { Text(section, style = MaterialTheme.typography.titleLarge) }
            items(sources) { p ->
                ElevatedCard(onClick = { if(p.id !in disabled) nav.navigate("catalogue/$console/${p.id}") }) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.Language, null, tint = MaterialTheme.colorScheme.primary)
                        Text(p.name, style = MaterialTheme.typography.titleLarge); Text(p.description)
                        Text("Platforms: ${p.supportedConsoles.joinToString()}", style = MaterialTheme.typography.labelLarge)
                        Text(if(p.id in disabled) "Disabled in Settings" else statuses[p.id] ?: "Live access verified · tap to connect", color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun CatalogueScreen(console: String, provider: String, vm: ShelfViewModel, nav: NavHostController) {
    LaunchedEffect(provider) { vm.load(provider) }
    val state by vm.catalogue.collectAsStateWithLifecycle()
    var search by rememberSaveable(console, provider) { mutableStateOf("") }
    var genre by rememberSaveable(console, provider) { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(Sort.TITLE) }
    var list by rememberSaveable { mutableStateOf(false) }
    var page by rememberSaveable(console, provider) { mutableIntStateOf(0) }
    LaunchedEffect(search, genre, sort) { page = 0 }
    val query = GameQuery(console, search, genre, sort, pageSize = ((page + 1) * 24).coerceAtMost(100))
    // Page the complete normalized feed locally, without repeating upstream requests.
    val allMatches = remember(state.games, search, genre, sort, console) { cataloguePage(state.games, query.copy(pageSize = 100)).let { _ ->
        val result = mutableListOf<Game>(); var i = 0
        do { val chunk = cataloguePage(state.games, query.copy(page = i++, pageSize = 100)); result.addAll(chunk.games) } while(chunk.hasMore)
        result.toList()
    } }
    val visible = allMatches.take((page + 1) * 24)
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(vm.graph.registry.get(provider).name, style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(search, { search = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Search this catalogue") }, leadingIcon = { Icon(Icons.Default.Search, null) })
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { Choice("Sort: ${sort.name.lowercase()}", Sort.entries.map { it.name }) { sort = Sort.valueOf(it) } }
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) { Choice(if(genre.isBlank()) "All genres" else genre, listOf("All genres") + state.games.filter { console in it.consoles }.flatMap { it.genres }.distinct().sorted()) { genre = if(it == "All genres") "" else it } }
                IconButton(onClick = { list = !list }) { Icon(if(list) Icons.Default.GridView else Icons.Default.ViewList, if(list) "Show grid" else "Show list") }
            }
            if(state.offline) Text("Offline · showing saved catalogue. Pull to retry.", color = MaterialTheme.colorScheme.tertiary)
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error); Button(onClick = { vm.load(provider, true) }) { Text("Retry") } }
        }
        PullToRefreshBox(isRefreshing = state.loading, onRefresh = { page = 0; vm.load(provider, true) }, modifier = Modifier.weight(1f)) {
            LazyVerticalGrid(columns = if(list) GridCells.Fixed(1) else GridCells.Adaptive(148.dp), contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if(state.loading) item(span = { GridItemSpan(maxLineSpan) }) { EmptyState("Connecting to catalogue", "The first GBA repository sync can take a minute. Subsequent visits use the local cache.") }
                if(!state.loading && state.error == null && visible.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { EmptyState("No games found", "Try another search, filter, or source.") }
                items(visible, key = { it.id }) { game -> GameCard(game, list) { openGame(game, vm, nav) } }
                if(visible.size < allMatches.size) item(span = { GridItemSpan(maxLineSpan) }) { Button(onClick = { page++ }, modifier = Modifier.fillMaxWidth()) { Text("Load more · ${visible.size} of ${allMatches.size}") } }
            }
        }
    }
}
@Composable private fun Choice(label: String, options: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box { TextButton(onClick = { expanded = true }) { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        DropdownMenu(expanded, { expanded = false }) { options.forEach { value -> DropdownMenuItem(text = { Text(value) }, onClick = { onSelect(value); expanded = false }) } }
    }
}
@Composable private fun Artwork(url: String?, title: String, modifier: Modifier) {
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        Icon(Icons.Default.SportsEsports, "Artwork unavailable", tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), modifier = Modifier.size(48.dp))
        AsyncImage(model = url, contentDescription = "$title artwork", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
    }
}
@Composable private fun GameCard(game: Game, list: Boolean, onClick: () -> Unit) {
    ElevatedCard(onClick = onClick) {
        if(list) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Artwork(game.artwork, game.title, Modifier.size(104.dp)); Column(Modifier.weight(1f).padding(12.dp)) { GameText(game) }
        } else Column { Artwork(game.artwork, game.title, Modifier.fillMaxWidth().height(150.dp)); Column(Modifier.padding(12.dp)) { GameText(game) } }
    }
}
@Composable private fun GameText(game: Game) {
    Text(game.title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
    game.developer?.let { Text(it, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    if(game.description.isNotBlank()) Text(game.description, maxLines = 2, style = MaterialTheme.typography.bodySmall, overflow = TextOverflow.Ellipsis)
    Text(game.genres.firstOrNull() ?: game.consoles.joinToString(), color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelSmall)
}
@Composable private fun EmptyState(title: String, description: String) { Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { Text(title, style = MaterialTheme.typography.titleLarge); Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
@Composable private fun GameCollection(title: String, games: List<Game>, empty: String, onClick: (Game) -> Unit) {
    LazyVerticalGrid(GridCells.Adaptive(148.dp), contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item(span = { GridItemSpan(maxLineSpan) }) { Text(title, style = MaterialTheme.typography.headlineMedium) }
        if(games.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { EmptyState("Your shelf is empty", empty) }
        items(games, key = { it.id }) { g -> GameCard(g, false) { onClick(g) } }
    }
}
@Composable private fun DetailsScreen(id: String, vm: ShelfViewModel) {
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    var detailsLoading by remember(id) { mutableStateOf(false) }
    var detailRefresh by remember(id) { mutableIntStateOf(0) }
    val game by produceState<Game?>(null, id, detailRefresh) {
        value = vm.graph.db.shelf().game(id)?.let(vm.graph.repository::decode) ?: vm.catalogue.value.games.firstOrNull { it.id == id } ?: vm.favorites.value.firstOrNull { it.id == id } ?: vm.recent.value.firstOrNull { it.id == id }
        value?.let { cached ->
            vm.view(cached)
            detailsLoading = vm.graph.registry.get(cached.provider).refreshDetails
            try { value = vm.graph.repository.details(cached, refresh = detailRefresh > 0); value?.let(vm::view) }
            catch(e: kotlinx.coroutines.CancellationException) { throw e }
            catch(_: Exception) { vm.messages.emit("Game details unavailable. Showing saved information; open the original website or try again later.") }
            finally { detailsLoading = false }
        }
    }
    val context = LocalContext.current
    val g = game
    if(g == null) { EmptyState("Loading game", "If this entry was removed from the cache, return to its catalogue."); return }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if(vm.graph.registry.get(g.provider).refreshDetails) item {
            TextButton(onClick = { detailRefresh++ }, enabled = !detailsLoading) { Text("Refresh game details") }
        }
        if(detailsLoading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        item { Artwork(g.artwork, g.title, Modifier.fillMaxWidth().height(240.dp)) }
        item { Row(verticalAlignment = Alignment.CenterVertically) { Text(g.title, Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); IconButton(onClick = { vm.favorite(g) }) { Icon(if(favorites.any { it.id == g.id }) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Toggle favorite") } } }
        item { Text(listOfNotNull(g.developer?.let { "Developer: $it" }, g.publisher?.let { "Publisher: $it" }, g.release?.let { "Release: $it" }, "Platforms: ${g.consoles.joinToString()}", g.license?.let { "Source license: $it" }).joinToString("\n"), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { Text(g.description.ifBlank { "No description provided by this source." }) }
        if(g.screenshots.isNotEmpty()) item { LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) { items(g.screenshots) { shot -> Artwork(shot, g.title, Modifier.width(280.dp).height(190.dp)) } } }
        item { Text("Available files", style = MaterialTheme.typography.titleLarge) }
        if(g.downloads.isEmpty()) item { Text("No direct files supplied. Open the original website.") }
        items(g.downloads) { file ->
            OutlinedCard { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(file.name, fontWeight = FontWeight.Bold)
                Text("${file.format} · ${file.size?.let { "${it / 1024} KiB" } ?: "Size not provided"}")
                Text(file.url, style = MaterialTheme.typography.bodySmall)
                if(file.authorization != null) {
                    Text("Distribution: ${file.authorization}", style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { vm.download(g, file) }) { Icon(Icons.Default.Download, null); Text("Download") }
                } else { Text("Distribution authorization not reviewed. Check the publisher's terms on the original website.", style = MaterialTheme.typography.bodySmall) }
            } }
        }
        item { OutlinedButton(onClick = { openWebsite(context, g.sourceUrl) }, Modifier.fillMaxWidth()) { Text("Open original website ↗") } }
        item { Text("${vm.graph.registry.get(g.provider).attribution}. RetroShelf does not install or run downloaded files.", style = MaterialTheme.typography.bodySmall) }
        vm.graph.registry.get(g.provider).metadataLicenseUrl?.let { license ->
            item { TextButton(onClick = { openWebsite(context, license) }) { Text("Metadata license: CC BY-SA 4.0 ↗") } }
        }
    }
}
@Composable private fun SearchScreen(vm: ShelfViewModel, nav: NavHostController) {
    val disabled by vm.disabled.collectAsStateWithLifecycle()
    val choices = vm.graph.registry.providers.filter { it.id !in disabled }.flatMap { p -> p.supportedConsoles.map { c -> Triple("${p.name} · $c", c, p.id) } }
    var selection by rememberSaveable { mutableStateOf("") }
    val picked = choices.firstOrNull { it.first == selection } ?: choices.firstOrNull()
    Column { if(picked != null) { Choice(picked.first, choices.map { it.first }) { selection = it }; CatalogueScreen(picked.second, picked.third, vm, nav) } else EmptyState("No enabled providers", "Enable a provider in Settings to search its catalogue.") }
}
@Composable private fun DownloadsScreen(vm: ShelfViewModel) {
    val rows by vm.downloads.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var export by remember { mutableStateOf<DownloadRecord?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri -> uri?.let { target -> export?.let { vm.export(it, target) } }; export = null }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Downloads", style = MaterialTheme.typography.headlineMedium) }
        if(rows.isEmpty()) item { EmptyState("No downloads yet", "Authorized files will appear here with their original source and progress.") }
        items(rows, key = { it.id }) { row ->
            ElevatedCard { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(row.title, style = MaterialTheme.typography.titleMedium); Text(row.filename)
                LinearProgressIndicator(progress = { row.progress / 100f }, modifier = Modifier.fillMaxWidth())
                Text("${row.state} · ${row.progress}%"); Text(row.destination, style = MaterialTheme.typography.bodySmall)
                Text(row.url, style = MaterialTheme.typography.bodySmall)
                Row {
                    if(row.state == "Complete") TextButton(onClick = { export = row; launcher.launch(row.filename) }) { Text("Save a copy…") }
                    else if(row.state in listOf("Queued", "Downloading", "Paused")) TextButton(onClick = { vm.cancel(row) }) { Text("Cancel") }
                    else TextButton(onClick = { vm.retry(row) }) { Text("Retry") }
                    TextButton(onClick = { openWebsite(context, row.url) }) { Text("Source") }
                }
            } }
        }
    }
}
@Composable private fun SettingsScreen(vm: ShelfViewModel) {
    val dark by vm.dark.collectAsStateWithLifecycle()
    val public by vm.publicDownloads.collectAsStateWithLifecycle()
    val disabled by vm.disabled.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("Settings", style = MaterialTheme.typography.headlineMedium) }
        item { ToggleRow("Dark theme", dark, vm::setDark) }
        item { ToggleRow("Save to public Downloads/RetroShelf", public, vm::setPublic); Text("When off, files use app storage and are removed on uninstall. Use Save a copy in Downloads to choose any document destination.", style = MaterialTheme.typography.bodySmall) }
        item { OutlinedButton(onClick = { scope.launch { withContext(Dispatchers.IO) { context.imageLoader.diskCache?.clear() }; context.imageLoader.memoryCache?.clear(); vm.messages.emit("Image cache cleared") } }) { Text("Clear image cache") } }
        item { Text("Catalogues and opened game details stay saved until you refresh or clear them. Pull down in a catalogue to check for updates.", style = MaterialTheme.typography.bodySmall) }
        item { OutlinedButton(onClick = vm::clearCatalogue) { Text("Clear catalogue cache") } }
        item { Text("Provider management", style = MaterialTheme.typography.titleLarge) }
        items(vm.graph.registry.providers) { p -> ToggleRow(p.name, p.id !in disabled) { vm.enable(p.id, it) } }
        item { Text("About RetroShelf 0.2.3", style = MaterialTheme.typography.titleLarge); Text("A native browser for community homebrew. Game data and images belong to their respective authors. No games are bundled. Only individually reviewed distributions have direct download buttons.") }
        item { Text("Source attribution", style = MaterialTheme.typography.titleLarge) }
        items(vm.graph.registry.providers) { p -> Column { Text(p.attribution, style = MaterialTheme.typography.bodySmall); TextButton(onClick = { openWebsite(context, p.website) }) { Text("${p.name} ↗") } } }
        item { Text("Homebrew Hub API access could not be verified (HTTP 403). GBA uses its public upstream repository. GB/GBC integrations remain unavailable. Universal search is deferred; search each enabled catalogue using the Search tab.", style = MaterialTheme.typography.bodySmall) }
    }
}
@Composable private fun ToggleRow(title: String, value: Boolean, onChange: (Boolean) -> Unit) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(title, Modifier.weight(1f)); Switch(value, onChange) } }
private fun openWebsite(context: android.content.Context, url: String) {
    if(!UrlSafety.valid(url)) return
    try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch(_: Exception) { android.widget.Toast.makeText(context, "No browser available", android.widget.Toast.LENGTH_SHORT).show() }
}
