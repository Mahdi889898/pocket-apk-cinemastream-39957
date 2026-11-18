package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.rounded.AddLink
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.CatalogMovie
import com.example.data.model.DetectedWebStream
import com.example.data.model.LibraryItem
import com.example.data.model.MediaSource
import com.example.data.model.MediaType
import com.example.data.model.PlayerRequest
import com.example.data.model.SourceType
import com.example.data.remote.CuratedCatalog
import com.example.ui.theme.CinemaGold
import com.example.ui.theme.CinemaGoldContainer
import com.example.ui.theme.LiveCrimson
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianCardBorder
import com.example.ui.theme.ObsidianElevated
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.StreamCyan
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(
    searchQuery: String,
    selectedCategory: String,
    archiveResults: List<CatalogMovie>,
    isSearching: Boolean,
    searchError: String?,
    isExtractingUrl: Boolean,
    extractedQuickStreams: List<DetectedWebStream>,
    savedSources: List<MediaSource>,
    libraryItems: List<LibraryItem>,
    onSearchQueryChange: (String) -> Unit,
    onSearchSubmit: () -> Unit,
    onSelectCategory: (String) -> Unit,
    onPlayMovie: (CatalogMovie) -> Unit,
    onQuickPlayUrl: (String) -> Unit,
    onPlayDetectedStream: (DetectedWebStream) -> Unit,
    onClearExtractedStreams: () -> Unit,
    onToggleFavorite: (CatalogMovie, Boolean) -> Unit,
    onOpenWebsiteInBrowser: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showQuickPlayDialog by remember { mutableStateOf(false) }

    val favoriteUrls = remember(libraryItems) {
        libraryItems.filter { it.favorite }.map { it.streamUrl }.toSet()
    }

    val categories = listOf("همه", "سینمایی", "انیمیشن", "علمی-تخیلی", "کانال زنده")

    val userChannelMovies = remember(savedSources) {
        savedSources
            .filter { it.resolvedType() == SourceType.CHANNEL || it.resolvedType() == SourceType.DIRECT_MOVIE }
            .map { src ->
                CatalogMovie(
                    id = "src_${src.id}",
                    title = src.title,
                    subtitle = src.url,
                    streamUrl = src.url,
                    posterUrl = src.logoUrl,
                    category = src.category,
                    sourceName = "منبع شخصی شما",
                    mediaType = if (src.resolvedType() == SourceType.CHANNEL) MediaType.LIVE_CHANNEL else MediaType.MOVIE,
                    isLive = src.resolvedType() == SourceType.CHANNEL,
                )
            }
    }

    val displayedFeatured = remember(CuratedCatalog.featuredMovies, userChannelMovies, selectedCategory, searchQuery) {
        val combined = userChannelMovies + CuratedCatalog.featuredMovies
        combined.filter { movie ->
            val matchesCategory = selectedCategory == "همه" || movie.category == selectedCategory
            val matchesQuery = searchQuery.isBlank() ||
                movie.title.contains(searchQuery, ignoreCase = true) ||
                movie.subtitle.contains(searchQuery, ignoreCase = true) ||
                movie.category.contains(searchQuery, ignoreCase = true)
            matchesCategory && matchesQuery
        }
    }

    val spotlight = CuratedCatalog.featuredMovies.first()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = "سینما استریم",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = "پخش از کانال‌ها، لیست‌های M3U و سایت‌ها",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
                Button(
                    onClick = { showQuickPlayDialog = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CinemaGold,
                        contentColor = ObsidianBg,
                    ),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("quick_play_button"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AddLink,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "پخش لینک",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }

        // Hero Spotlight Banner
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .border(1.dp, ObsidianCardBorder, RoundedCornerShape(22.dp))
                    .clickable { onPlayMovie(spotlight) }
                    .testTag("hero_spotlight_card"),
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_cinema_hero),
                    contentDescription = "بنر فیلم برگزیده",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Transparent,
                                    ObsidianBg.copy(alpha = 0.72f),
                                    ObsidianBg.copy(alpha = 0.96f),
                                )
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(18.dp),
                ) {
                    Surface(
                        color = CinemaGold.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(
                            text = "پیشنهاد ویژه • ${spotlight.year} • ${spotlight.durationLabel}",
                            style = MaterialTheme.typography.labelMedium,
                            color = CinemaGold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = spotlight.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = spotlight.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(14.dp)
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(CinemaGold),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "پخش فوری",
                        tint = ObsidianBg,
                        modifier = Modifier.size(30.dp),
                    )
                }
            }
        }

        // Multi-source Search Bar
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = {
                        Text(
                            text = "جستجوی فیلم در کانال‌ها و آرشیو آنلاین...",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            tint = CinemaGold,
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchQueryChange("") }) {
                                Icon(Icons.Rounded.Close, contentDescription = "پاک کردن")
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearchSubmit() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = ObsidianSurface,
                        unfocusedContainerColor = ObsidianSurface,
                        focusedBorderColor = CinemaGold,
                        unfocusedBorderColor = ObsidianCardBorder,
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("movie_search_input"),
                )
                Button(
                    onClick = onSearchSubmit,
                    enabled = !isSearching,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ObsidianElevated,
                        contentColor = CinemaGold,
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .height(54.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("search_movies_button"),
                ) {
                    if (isSearching) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = CinemaGold,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text("بگرد")
                    }
                }
            }
        }

        // Category chips
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(categories) { category ->
                    val selected = category == selectedCategory
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectCategory(category) },
                        label = {
                            Text(
                                text = category,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CinemaGoldContainer,
                            selectedLabelColor = CinemaGold,
                            containerColor = ObsidianSurface,
                            labelColor = TextSecondary,
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selected,
                            borderColor = ObsidianCardBorder,
                            selectedBorderColor = CinemaGold,
                        ),
                    )
                }
            }
        }

        // Online search results from Internet Archive
        if (archiveResults.isNotEmpty() || !searchError.isNullOrBlank()) {
            item {
                Text(
                    text = "نتایج جستجو از آرشیو فیلم‌ها",
                    style = MaterialTheme.typography.titleMedium,
                    color = StreamCyan,
                )
            }
            if (!searchError.isNullOrBlank()) {
                item {
                    Surface(
                        color = ObsidianSurface,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, ObsidianCardBorder, RoundedCornerShape(14.dp)),
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = searchError,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { onOpenWebsiteInBrowser("https://archive.org/details/movies") },
                            ) {
                                Icon(Icons.Rounded.Language, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("باز کردن سایت در مرورگر")
                            }
                        }
                    }
                }
            } else {
                items(archiveResults, key = { it.id }) { movie ->
                    MovieCardRow(
                        movie = movie,
                        isFavorite = favoriteUrls.contains(movie.streamUrl),
                        onPlay = { onPlayMovie(movie) },
                        onToggleFavorite = { onToggleFavorite(movie, favoriteUrls.contains(movie.streamUrl)) },
                    )
                }
            }
        }

        // Featured & Personal Channels Movie List
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "فیلم‌ها و استریم‌های آماده پخش",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "${displayedFeatured.size} مورد",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextMuted,
                )
            }
        }

        items(displayedFeatured, key = { it.id }) { movie ->
            MovieCardRow(
                movie = movie,
                isFavorite = favoriteUrls.contains(movie.streamUrl),
                onPlay = { onPlayMovie(movie) },
                onToggleFavorite = { onToggleFavorite(movie, favoriteUrls.contains(movie.streamUrl)) },
            )
        }

        // Quick jump to websites
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "سایت‌های فیلم پیشنهادی (ورود به مرورگر هوشمند)",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        items(CuratedCatalog.starterWebsites, key = { it.id }) { site ->
            Surface(
                color = ObsidianSurface,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, ObsidianCardBorder, RoundedCornerShape(16.dp))
                    .clickable { onOpenWebsiteInBrowser(site.url) },
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(StreamCyan.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Language,
                            contentDescription = null,
                            tint = StreamCyan,
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = site.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = site.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Surface(
                        color = ObsidianElevated,
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(
                            text = site.badge,
                            style = MaterialTheme.typography.labelMedium,
                            color = StreamCyan,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }

    if (showQuickPlayDialog) {
        QuickPlayUrlDialog(
            isLoading = isExtractingUrl,
            onDismiss = { showQuickPlayDialog = false },
            onSubmitUrl = { url ->
                showQuickPlayDialog = false
                onQuickPlayUrl(url)
            },
            onOpenInBrowser = { url ->
                showQuickPlayDialog = false
                onOpenWebsiteInBrowser(url)
            },
        )
    }

    if (extractedQuickStreams.isNotEmpty()) {
        ModalBottomSheet(
            onDismissRequest = onClearExtractedStreams,
            containerColor = ObsidianSurface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "لینک‌های ویدیوی شناسایی‌شده (${extractedQuickStreams.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                extractedQuickStreams.forEach { stream ->
                    Surface(
                        color = ObsidianElevated,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onClearExtractedStreams()
                                onPlayDetectedStream(stream)
                            },
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(
                                color = CinemaGoldContainer,
                                shape = RoundedCornerShape(8.dp),
                            ) {
                                Text(
                                    text = stream.formatBadge,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CinemaGold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stream.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = stream.url,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = "پخش",
                                tint = CinemaGold,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun MovieCardRow(
    movie: CatalogMovie,
    isFavorite: Boolean,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    Surface(
        color = ObsidianSurface,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, ObsidianCardBorder, RoundedCornerShape(18.dp))
            .clickable(onClick = onPlay)
            .testTag("movie_card_${movie.id}"),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 86.dp, height = 108.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(listOf(ObsidianElevated, CinemaGoldContainer))
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (!movie.posterUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = movie.posterUrl,
                        contentDescription = movie.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Movie,
                        contentDescription = null,
                        tint = CinemaGold.copy(alpha = 0.7f),
                        modifier = Modifier.size(34.dp),
                    )
                }
                if (movie.isLive) {
                    Surface(
                        color = LiveCrimson,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp),
                    ) {
                        Text(
                            text = "LIVE",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Surface(
                        color = ObsidianElevated,
                        shape = RoundedCornerShape(6.dp),
                    ) {
                        Text(
                            text = movie.category,
                            style = MaterialTheme.typography.labelMedium,
                            color = CinemaGold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                    if (movie.durationLabel.isNotBlank()) {
                        Text(
                            text = movie.durationLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = movie.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = movie.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = movie.sourceName,
                    style = MaterialTheme.typography.labelMedium,
                    color = StreamCyan,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = if (isFavorite) "حذف از علاقه‌مندی‌ها" else "افزودن به علاقه‌مندی‌ها",
                        tint = if (isFavorite) LiveCrimson else TextSecondary,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(CinemaGold)
                        .clickable(onClick = onPlay),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "پخش",
                        tint = ObsidianBg,
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickPlayUrlDialog(
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onSubmitUrl: (String) -> Unit,
    onOpenInBrowser: (String) -> Unit,
) {
    var urlInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurface,
        title = {
            Text(
                text = "پخش مستقیم از لینک یا سایت",
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "آدرس مستقیم ویدیو (.mp4, .m3u8, .mpd) یا آدرس صفحه وب فیلم را وارد کنید:",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    placeholder = { Text("https://example.com/movie.m3u8") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("quick_play_url_input"),
                )
                AnimatedVisibility(visible = isLoading) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text("در حال بررسی صفحه و استخراج لینک...", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmitUrl(urlInput) },
                enabled = urlInput.isNotBlank() && !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = CinemaGold, contentColor = ObsidianBg),
                modifier = Modifier.testTag("quick_play_confirm_button"),
            ) {
                Text("بررسی و پخش")
            }
        },
        dismissButton = {
            Row {
                TextButton(
                    onClick = { if (urlInput.isNotBlank()) onOpenInBrowser(urlInput) },
                    enabled = urlInput.isNotBlank(),
                ) {
                    Text("باز کردن در مرورگر")
                }
                TextButton(onClick = onDismiss) {
                    Text("لغو")
                }
            }
        },
    )
}
