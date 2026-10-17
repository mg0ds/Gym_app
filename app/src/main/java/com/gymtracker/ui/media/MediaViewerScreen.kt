package com.gymtracker.ui.media

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.gymtracker.R
import com.gymtracker.domain.model.ExerciseMedia
import com.gymtracker.domain.model.MediaKind
import com.gymtracker.domain.timer.formatElapsed
import com.gymtracker.ui.components.openExternalLink
import java.io.File

/**
 * Podgląd załącznika: zdjęcie (z powiększaniem), film (Media3) albo link
 * otwierany poza aplikacją.
 *
 * Plik bierzemy **z galerii**, więc może się zdarzyć, że użytkownik go usunął
 * albo dostęp wygasł. Zamiast pustki pokazujemy wtedy własną miniaturę i mówimy
 * wprost, co się stało - użytkownik ma wiedzieć, że załącznik nie zniknął
 * z aplikacji przez błąd.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaViewerScreen(
    onBack: () -> Unit,
    viewModel: MediaViewerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val media = uiState.media
    var showCaptionDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = media?.let { item -> item.kind.label() }
                            ?: stringResource(R.string.media_viewer_title),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                        )
                    }
                },
                actions = {
                    if (media != null) {
                        IconButton(onClick = { showCaptionDialog = true }) {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = stringResource(R.string.media_caption_edit),
                            )
                        }
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.media_delete_action),
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center,
        ) {
            when {
                media == null && uiState.isLoading -> CircularProgressIndicator()

                media == null -> Text(stringResource(R.string.media_viewer_not_found))

                media.isGalleryFile -> MediaContent(
                    media = media,
                    thumbnail = uiState.thumbnail,
                    info = media.infoText(),
                )

                else -> ExternalMedia(
                    media = media,
                    onOpen = { url -> openExternalLink(context, url) },
                )
            }
        }
    }

    if (showCaptionDialog && media != null) {
        MediaCaptionDialog(
            caption = media.caption.orEmpty(),
            onDismiss = { showCaptionDialog = false },
            onConfirm = { caption ->
                showCaptionDialog = false
                viewModel.setCaption(caption)
            },
        )
    }

    if (showDeleteDialog && media != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.media_delete_title)) },
            // Mówimy wprost, że galerii to nie dotyczy - to najczęstsza obawa
            // przy kasowaniu czegokolwiek, co przyszło z galerii.
            text = { Text(stringResource(R.string.media_delete_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.delete(onDeleted = onBack)
                    },
                ) {
                    Text(stringResource(R.string.media_delete_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun MediaContent(
    media: ExerciseMedia,
    thumbnail: File?,
    info: String?,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            when (media.kind) {
                MediaKind.VIDEO -> VideoMedia(
                    uri = media.remoteUri,
                    thumbnail = thumbnail,
                    modifier = Modifier.fillMaxSize(),
                )

                else -> ImageMedia(
                    uri = media.remoteUri,
                    thumbnail = thumbnail,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        MediaCaption(info = info, caption = media.caption)
    }
}

@Composable
private fun ImageMedia(
    uri: String?,
    thumbnail: File?,
    modifier: Modifier = Modifier,
) {
    // Dostęp do pliku z galerii bywa ulotny, więc pytamy o niego przy każdym
    // wejściu: porażka wczytania to nie awaria, tylko informacja.
    var unavailable by remember(uri) { mutableStateOf(false) }
    var scale by remember(uri) { mutableFloatStateOf(MIN_SCALE) }
    var offset by remember(uri) { mutableStateOf(Offset.Zero) }

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(uri) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
                        offset = if (scale > MIN_SCALE) offset + pan else Offset.Zero
                    }
                }
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
            contentAlignment = Alignment.Center,
        ) {
            if (uri != null && !unavailable) {
                AsyncImage(
                    model = uri,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                    onError = { unavailable = true },
                )
            } else {
                ThumbnailFallback(thumbnail = thumbnail)
            }
        }

        if (unavailable) {
            Notice(text = stringResource(R.string.media_notice_image_unavailable))
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun VideoMedia(
    uri: String?,
    thumbnail: File?,
    modifier: Modifier = Modifier,
) {
    var failed by remember(uri) { mutableStateOf(false) }

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            if (uri != null && !failed) {
                VideoPlayer(
                    uri = uri,
                    onError = { failed = true },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                ThumbnailFallback(thumbnail = thumbnail)
            }
        }

        if (failed) {
            Notice(text = stringResource(R.string.media_notice_video_unavailable))
        }
    }
}

/**
 * Odtwarzacz Media3. Trzymamy go przy ekranie (nie w modelu), a przy wyjściu
 * zwalniamy - inaczej dźwięk leciałby dalej po powrocie do szczegółów ćwiczenia.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun VideoPlayer(
    uri: String,
    onError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val player = remember(uri) { ExoPlayer.Builder(context).build() }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                onError()
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    DisposableEffect(lifecycleOwner, player) {
        // Ekran zgaszony albo apka w tle = pauza; wracamy, gdy użytkownik wróci.
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) player.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(player, uri) {
        player.setMediaItem(MediaItem.fromUri(uri))
        player.prepare()
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                useController = true
            }
        },
        modifier = modifier,
        onRelease = { view -> view.player = null },
    )
}

@Composable
private fun ThumbnailFallback(thumbnail: File?) {
    if (thumbnail != null) {
        AsyncImage(
            model = thumbnail,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
    } else {
        Text(
            text = stringResource(R.string.media_kind_photo),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Notice(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun MediaCaption(info: String?, caption: String?) {
    if (info == null && caption == null) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (info != null) {
            Text(
                text = info,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (caption != null) {
            Text(
                text = caption,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Załącznik, który otwieramy poza aplikacją (link do filmu albo zwykły link). */
@Composable
private fun ExternalMedia(
    media: ExerciseMedia,
    onOpen: (String) -> Unit,
) {
    Column(
        modifier = Modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.media_external_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        media.remoteUri?.let { url ->
            Button(onClick = { onOpen(url) }) {
                Text(stringResource(R.string.media_open_external))
            }
        }
    }
}

@Composable
private fun MediaCaptionDialog(
    caption: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(caption) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.media_caption_edit)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { value -> text = value },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.media_caption_field)) },
                minLines = 2,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

/** „1:23 · 12,4 MB” - cokolwiek wiemy o pliku; puste, gdy nie wiemy nic. */
@Composable
private fun ExerciseMedia.infoText(): String? {
    val parts = listOfNotNull(
        durationMs
            ?.takeIf { kind == MediaKind.VIDEO && it > 0 }
            ?.let { milliseconds -> formatElapsed(milliseconds) },
        sizeBytes
            ?.takeIf { it > 0 }
            ?.let { bytes -> formatSize(bytes) },
    )
    return parts.takeIf { it.isNotEmpty() }?.joinToString(separator = " · ")
}

/** Kilobajty dla małych plików - „0,0 MB” przy zdjęciu z telefonu nic nie mówi. */
@Composable
private fun formatSize(bytes: Long): String = if (bytes >= MEGABYTE) {
    stringResource(R.string.media_size_mb, bytes / MEGABYTE.toDouble())
} else {
    stringResource(R.string.media_size_kb, bytes / KILOBYTE.toDouble())
}

private const val MIN_SCALE = 1f
private const val MAX_SCALE = 6f
private const val KILOBYTE = 1024L
private const val MEGABYTE = 1024L * 1024L
