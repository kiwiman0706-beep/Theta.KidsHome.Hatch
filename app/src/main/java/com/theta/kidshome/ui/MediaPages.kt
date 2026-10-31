package com.theta.kidshome.ui

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theta.kidshome.data.MediaEntry
import com.theta.kidshome.data.MediaKind
import com.theta.kidshome.data.MediaLibrary
import com.theta.kidshome.data.Positions
import com.theta.kidshome.data.Store
import com.theta.kidshome.media.AudioPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** 子どもに見せる動画（かくしたフォルダを除く） */
private fun MediaEntry.shown() = folder !in Store.config.hiddenFolders && shelf !in Store.config.hiddenFolders
fun visibleVideos(): List<MediaEntry> = MediaLibrary.videos.filter { it.shown() }
fun visibleMusic(): List<MediaEntry> = MediaLibrary.music.filter { it.shown() }
fun visibleBooks(): List<MediaEntry> = MediaLibrary.books.filter { it.shown() }

/**
 * ひだり に フォルダ（シリーズ）の 一覧、みぎ に なかみ。フォルダ が 1 つ なら みぎ だけ。
 * えらんだ フォルダ は おぼえておく（つぎ に ひらいた とき も おなじ ところ）。
 */
@Composable
private fun ShelfPanes(
    theme: KidTheme,
    key: String,
    shelves: Map<String, List<MediaEntry>>,
    fallback: String,
    square: Boolean,
    count: (List<MediaEntry>) -> String,
    content: @Composable (name: String, list: List<MediaEntry>) -> Unit,
) {
    var picked by androidx.compose.runtime.saveable.rememberSaveable(key) { mutableStateOf<String?>(null) }
    val cur = picked?.takeIf { it in shelves } ?: shelves.keys.firstOrNull() ?: return
    if (shelves.size <= 1) { content(cur, shelves.getValue(cur)); return }
    Row(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.width(250.dp).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 20.dp),
        ) {
            items(shelves.keys.toList(), key = { it }) { name ->
                val list = shelves.getValue(name)
                val on = name == cur
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .background(if (on) theme.titleBars[0] else Color.White)
                        .border(if (on) 3.dp else 2.dp, theme.outline.copy(alpha = if (on) 1f else 0.25f), RoundedCornerShape(14.dp))
                        .clickable { picked = name }
                        .padding(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MediaThumb(
                        list.first().uri, fallback,
                        Modifier.width(if (square) 56.dp else 80.dp).height(56.dp).clip(RoundedCornerShape(10.dp)), square = square,
                    )
                    Column(Modifier.padding(start = 8.dp).weight(1f)) {
                        Text(name, fontFamily = DotFont, fontSize = 16.sp, color = theme.outline, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(count(list), fontSize = 12.sp, color = theme.outline.copy(alpha = 0.6f))
                    }
                }
            }
        }
        Spacer(Modifier.width(16.dp))
        Box(Modifier.weight(1f).fillMaxHeight()) { content(cur, shelves.getValue(cur)) }
    }
}

/** サムネイル（動画のコマ・ジャケット）。読めるまでは えもじ */
@Composable
fun MediaThumb(uri: Uri, fallback: String, modifier: Modifier = Modifier, square: Boolean = false) {
    val context = LocalContext.current
    val image by produceState<ImageBitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            MediaLibrary.thumbnail(context, uri, if (square) 300 else 384, if (square) 300 else 216)?.asImageBitmap()
        }
    }
    Box(modifier.background(Color(0xFFF2EEF5)), contentAlignment = Alignment.Center) {
        val img = image
        if (img != null) Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Text(fallback, fontSize = 40.sp)
    }
}

private fun minutes(ms: Long): String {
    val m = (ms / 60_000).toInt()
    return if (m >= 60) "${m / 60}じかん${m % 60}ふん" else "${m}ふん"
}

// ---------------------------------------------------------------- ビデオ（端末の動画）

@Composable
fun VideoLibraryPage(theme: KidTheme, locked: Boolean, onPlay: (List<MediaEntry>, Int) -> Unit, onLocked: () -> Unit) {
    val context = LocalContext.current
    ShelfPanes(theme, "video", visibleVideos().groupBy { it.shelf }, "🎬", square = false, count = { "${it.size} こ" }) { shelf, list ->
        // おなじ シリーズ の なか で フォルダ が わかれて いたら（シーズン1・2 など）見出し を 出す
        val parts = list.groupBy { it.folder }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(230.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            contentPadding = PaddingValues(end = 8.dp, bottom = 28.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "title:$shelf") {
                DotText("📼 $shelf", theme, 22, theme.text, modifier = Modifier.padding(top = 6.dp))
            }
            parts.forEach { (folder, sub) ->
                if (parts.size > 1) item(span = { GridItemSpan(maxLineSpan) }, key = "sub:$folder") {
                    DotText("📁 $folder", theme, 17, theme.outline, modifier = Modifier.padding(top = 4.dp))
                }
                itemsIndexed(sub, key = { _, e -> e.id }) { j, e ->
                    val i = list.indexOf(e)
                    val progress = remember(e.id) { Positions.progress(context, e) }
                    RetroWindow(
                        theme, title = minutes(e.durationMs), titleColor = if (locked) Color(0xFFDDDDDD) else theme.titleBars[j % theme.titleBars.size],
                        onClick = { if (locked) onLocked() else onPlay(list, i) },
                    ) {
                        Box {
                            MediaThumb(e.uri, "🎬", Modifier.fillMaxWidth().aspectRatio(16f / 9f).alpha(if (locked) 0.35f else 1f))
                            if (locked) Text("🔒", fontSize = 44.sp, modifier = Modifier.align(Alignment.Center))
                            else Text("▶️", fontSize = 36.sp, modifier = Modifier.align(Alignment.Center).alpha(0.85f))
                        }
                        if (progress > 0f) {
                            Box(Modifier.fillMaxWidth().height(5.dp).background(Color(0xFFEEEEEE))) {
                                Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(Color(0xFFFF4D6D)))
                            }
                        }
                        Text(
                            e.title, fontFamily = DotFont, fontSize = 16.sp, color = theme.outline, maxLines = 2,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(10.dp),
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- おんがく・おはなし

/** アルバム（オーディオブックは 1 冊）ごとの棚。押すと中の曲、1 曲だけならすぐ再生 */
@Composable
fun AudioLibraryPage(theme: KidTheme, kind: MediaKind) {
    val context = LocalContext.current
    val all = if (kind == MediaKind.BOOK) visibleBooks() else visibleMusic()
    var open by remember(kind) { mutableStateOf<String?>(null) }
    val fallback = if (kind == MediaKind.BOOK) "📖" else "💿"

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
          ShelfPanes(
              theme, "audio:$kind", all.groupBy { it.shelf }, fallback, square = true,
              count = { l -> l.map { it.album }.distinct().size.let { n -> if (n > 1) "$n まい" else "${l.size} きょく" } },
          ) { shelf, inShelf ->
            val albums = inShelf.groupBy { it.album }
            val album = open?.let { albums[it] }
            if (album == null) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(170.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                    contentPadding = PaddingValues(end = 8.dp, bottom = 20.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(albums.keys.toList(), key = { _, k -> k }) { i, name ->
                        val tracks = albums.getValue(name)
                        val progress = remember(name) {
                            if (kind == MediaKind.BOOK) tracks.map { Positions.progress(context, it) }.average().toFloat() else 0f
                        }
                        RetroWindow(
                            theme, title = if (tracks.size > 1) "${tracks.size}きょく" else minutes(tracks.first().durationMs),
                            titleColor = theme.titleBars[i % theme.titleBars.size],
                            onClick = { if (tracks.size == 1) AudioPlayer.play(context, tracks, 0) else open = name },
                        ) {
                            MediaThumb(tracks.first().uri, fallback, Modifier.fillMaxWidth().aspectRatio(1f), square = true)
                            if (progress > 0f) {
                                Box(Modifier.fillMaxWidth().height(5.dp).background(Color(0xFFEEEEEE))) {
                                    Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(theme.accent))
                                }
                            }
                            Text(
                                name, fontFamily = DotFont, fontSize = 15.sp, color = theme.outline, maxLines = 2,
                                overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(8.dp),
                            )
                        }
                    }
                }
            } else {
                AlbumTracks(theme, open!!, album, fallback) { open = null }
            }
          }
        }
        if (AudioPlayer.current != null) {
            Spacer(Modifier.height(14.dp))
            NowPlayingBar(theme)
        }
    }
}

@Composable
private fun AlbumTracks(theme: KidTheme, name: String, tracks: List<MediaEntry>, fallback: String, onBack: () -> Unit) {
    val context = LocalContext.current
    RetroWindow(theme, Modifier.fillMaxSize().padding(end = 6.dp, bottom = 6.dp), title = name, titleColor = theme.titleBars[1]) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            RetroIconButton("⬅️", theme, size = 48.dp, onClick = onBack)
            MediaThumb(tracks.first().uri, fallback, Modifier.padding(start = 8.dp).size(64.dp).clip(RoundedCornerShape(10.dp)), square = true)
            DotText(name, theme, 20, modifier = Modifier.padding(start = 12.dp).weight(1f))
            RetroButton("▶ ぜんぶ", theme, theme.titleBars[0], fontSize = 16) { AudioPlayer.play(context, tracks, 0) }
        }
        LazyVerticalGrid(GridCells.Fixed(1), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
            itemsIndexed(tracks, key = { _, t -> t.id }) { i, t ->
                val now = AudioPlayer.current?.id == t.id
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(if (now) theme.bubbles[0] else Color.Transparent)
                        .clickable { AudioPlayer.play(context, tracks, i) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DotText(if (now) "🎵" else "${i + 1}", theme, 16, modifier = Modifier.padding(end = 12.dp))
                    DotText(t.title, theme, 17, modifier = Modifier.weight(1f))
                    DotText(minutes(t.durationMs), theme, 13, theme.outline.copy(alpha = 0.6f))
                }
            }
        }
    }
}

/** いま流れている曲・おはなし */
@Composable
fun NowPlayingBar(theme: KidTheme) {
    val e = AudioPlayer.current ?: return
    var pos by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(e.id) {
        while (true) {
            val d = AudioPlayer.duration()
            pos = if (d > 0) AudioPlayer.position().toFloat() / d else 0f
            delay(500)
        }
    }
    val book = e.kind == MediaKind.BOOK
    RetroWindow(theme, Modifier.fillMaxWidth().padding(end = 6.dp, bottom = 6.dp), title = if (book) "おはなし" else "おんがく", titleColor = theme.titleBars[3]) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            MediaThumb(e.uri, if (book) "📖" else "💿", Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)), square = true)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                DotText(e.title, theme, 17)
                DotText(e.album, theme, 12, theme.outline.copy(alpha = 0.6f))
                Box(
                    Modifier.padding(top = 6.dp).fillMaxWidth().height(14.dp).clip(RoundedCornerShape(50))
                        .background(Color.White).border(2.dp, theme.outline, RoundedCornerShape(50))
                        .pointerSeek()
                ) {
                    Box(Modifier.fillMaxWidth(pos.coerceIn(0f, 1f)).fillMaxHeight().background(theme.accent))
                }
            }
            if (book) RetroIconButton("⏪", theme, size = 44.dp) { AudioPlayer.seekBy(-30_000) }
            else RetroIconButton("⏮", theme, size = 44.dp) { AudioPlayer.previous() }
            RetroIconButton(if (AudioPlayer.playing) "⏸" else "▶️", theme, selected = AudioPlayer.playing, size = 56.dp) { AudioPlayer.toggle() }
            if (book) RetroIconButton("⏩", theme, size = 44.dp) { AudioPlayer.seekBy(30_000) }
            else RetroIconButton("⏭", theme, size = 44.dp) { AudioPlayer.next() }
            if (book) {
                listOf(0.8f, 1f, 1.25f).forEach { s ->
                    RetroIconButton(
                        when (s) { 0.8f -> "🐢"; 1f -> "🙂"; else -> "🐇" },
                        theme, selected = AudioPlayer.speed == s, size = 40.dp,
                    ) { AudioPlayer.changeSpeed(s) }
                }
            }
        }
    }
}

/** 棒をさわったところへ飛ぶ */
private fun Modifier.pointerSeek(): Modifier = pointerInput(Unit) {
    detectTapGestures { o -> AudioPlayer.seekTo(o.x / size.width) }
}
