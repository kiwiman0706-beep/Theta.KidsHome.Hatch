package com.theta.kidshome.ui

import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.Key
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.focusable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.theta.kidshome.data.AppConfig
import com.theta.kidshome.data.Launcher
import com.theta.kidshome.data.Store
import com.theta.kidshome.data.Tile
import com.theta.kidshome.data.TileType
import com.theta.kidshome.update.SelfUpdate
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** タップしたところで、はじける えもじ */
private data class Burst(val id: Long, val at: Offset, val emojis: List<String>)

@Composable
fun HomeScreen(
    onOpenDrawing: () -> Unit,
    onOpenParent: () -> Unit,
    onOpenInstrument: () -> Unit = {},
    onOpenEdupad: () -> Unit = {},
    onOpenWordpro: () -> Unit = {},
    onOpenPuzzle: () -> Unit = {},
    onOpenTyping: () -> Unit = {},
    onOpenMoji: () -> Unit = {},
    onOpenKuku: () -> Unit = {},
    onPlayVideo: (List<com.theta.kidshome.data.MediaEntry>, Int) -> Unit = { _, _ -> },
) {
    val config = Store.config
    val profile = config.currentProfile
    val theme = themeById(profile.themeId)
    val context = LocalContext.current
    val playful = !Store.safeMode

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val locked = config.isVideoLocked(now)
    var showGate by remember { mutableStateOf(false) }
    var showLockedDialog by remember { mutableStateOf(false) }
    val bursts = remember { mutableStateListOf<Burst>() }
    var stars by remember { mutableIntStateOf(0) }
    fun burst(at: Offset, emojis: List<String> = theme.decorations) {
        if (playful) bursts += Burst(System.nanoTime(), at, emojis)
    }

    val installed = remember(config.appCategory, config.categories) { Launcher.listApps(context) }
    val media = buildList {
        if (profile.hasTimetable) add(KidTab("timetable", TabKind.TIMETABLE, "📒", "じかんわり"))
        if (config.showVideos && visibleVideos().isNotEmpty()) add(KidTab("local:video", TabKind.LOCAL_VIDEO, "📼", "ビデオ"))
        if (config.showMusic && visibleMusic().isNotEmpty()) add(KidTab("local:music", TabKind.MUSIC, "🎵", "おんがく"))
        if (config.showBooks && visibleBooks().isNotEmpty()) add(KidTab("local:book", TabKind.BOOKS, "🎧", "おはなし"))
    }
    val tabs = remember(profile, config.widgetIds, config.appCategory, config.categories, installed, media) {
        kidTabs(profile.tiles, config.widgetIds.isNotEmpty(), media + categoryTabs(config, profile.id, installed))
    }
    LaunchedEffect(Unit) { com.theta.kidshome.data.MediaLibrary.refresh(context) }
    // ホームを開いたままでも、ときどき シート と てんき を取り直す（どちらも間隔は中で決める）
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            com.theta.kidshome.data.SheetSync.sync(context)
            com.theta.kidshome.data.Weather.refresh(context)
        }
    }
    var tabKey by remember { mutableStateOf("home") }
    val tab = tabs.firstOrNull { it.key == tabKey } ?: tabs.first()

    val onTile: (Tile) -> Unit = { tile ->
        when {
            tile.isVideo && locked -> showLockedDialog = true
            tile.type == TileType.DRAWING -> onOpenDrawing()
            tile.type == TileType.INSTRUMENT -> onOpenInstrument()
            tile.type == TileType.EDUPAD -> onOpenEdupad()
            tile.type == TileType.WORDPRO -> onOpenWordpro()
            tile.type == TileType.PUZZLE -> onOpenPuzzle()
            tile.type == TileType.TYPING -> onOpenTyping()
            tile.type == TileType.MOJI -> onOpenMoji()
            tile.type == TileType.KUKU -> onOpenKuku()
            else -> Launcher.launch(context, tile)
        }
    }

    BoxWithConstraints(
        Modifier.fillMaxSize().pointerInput(theme.id) {
            // 背景のどこをさわっても、キラキラがはじける
            detectTapGestures { burst(it) }
        }
    ) {
        val wide = maxWidth > 760.dp && maxWidth > maxHeight
        RetroBackground(theme)
        if (playful) FloatingDecorations(theme)

        Column(
            Modifier.fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(start = 20.dp, top = 12.dp, bottom = 12.dp)
        ) {
            Header(config, theme, now, updateBadge = SelfUpdate.state.newer, onGate = { showGate = true })
            VSpace(14)
            Row(Modifier.weight(1f)) {
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    Crossfade(tab.key, label = "tab") { key ->
                        val t = tabs.firstOrNull { it.key == key } ?: tabs.first()
                        when (t.kind) {
                            TabKind.HOME -> HomePage(config, theme, locked, now, wide, stars, playful, ::burst, onTile)
                            TabKind.CLOCK -> ClockPage(theme, now, wide)
                            TabKind.VIDEO, TabKind.CATEGORY -> VideoPage(t, theme, locked, onTile) { showLockedDialog = true }
                            TabKind.WIDGETS -> WidgetPage(theme)
                            TabKind.TIMETABLE -> TimetablePage(theme, profile, now)
                            TabKind.LOCAL_VIDEO -> VideoLibraryPage(theme, locked, onPlayVideo) { showLockedDialog = true }
                            TabKind.MUSIC -> AudioLibraryPage(theme, com.theta.kidshome.data.MediaKind.MUSIC)
                            TabKind.BOOKS -> AudioLibraryPage(theme, com.theta.kidshome.data.MediaKind.BOOK)
                        }
                    }
                }
                TabRail(tabs, tab.key, theme, locked) { tabKey = it }
            }
        }

        // ふせん（おえかき・わーぷろ から はったもの）
        StickerLayer(theme, profile.stickers, maxWidth, maxHeight)

        if (playful) {
            Walker(theme, maxWidth, maxHeight) { at ->
                stars++
                burst(at, listOf("⭐", "🌟", "✨"))
            }
            bursts.forEach { b -> key(b.id) { BurstView(b) { bursts.remove(b) } } }
        }

        if (showGate) ParentGate(onSuccess = { showGate = false; onOpenParent() }, onDismiss = { showGate = false })
        if (showLockedDialog) LockedDialog(config, theme, now) { showLockedDialog = false }
    }
}

@Composable
private fun HomePage(
    config: AppConfig, theme: KidTheme, locked: Boolean, now: Long, wide: Boolean, stars: Int, playful: Boolean,
    burst: (Offset, List<String>) -> Unit, onTile: (Tile) -> Unit,
) {
    val tiles = config.currentProfile.tiles.filter { it.type != TileType.VIDEO }
    if (wide) {
        Row(Modifier.fillMaxSize()) {
            Column(
                Modifier.width(290.dp).fillMaxHeight().verticalScroll(rememberScrollState())
                    .padding(end = 6.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                MascotWindow(config, theme, stars, playful, burst)
                if (locked) LockWindow(config, theme, now)
                if (config.currentProfile.hasTimetable) TimetableWindow(theme, config.currentProfile, now)
                CalendarWindow(theme, now, compact = true, initialWeek = true)
                MusicWindow(theme)
            }
            HSpace(22)
            TileGrid(tiles, theme, locked, Modifier.weight(1f), onTile)
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(end = 6.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                MascotWindow(config, theme, stars, playful, burst, Modifier.weight(1f).fillMaxHeight())
                MusicWindow(theme, Modifier.weight(1f).fillMaxHeight())
            }
            if (locked) {
                VSpace(16)
                Box(Modifier.padding(end = 6.dp)) { LockWindow(config, theme, now) }
            }
            VSpace(16)
            Box(Modifier.padding(end = 6.dp)) { CalendarWindow(theme, now, compact = true, initialWeek = true) }
            VSpace(18)
            TileGrid(tiles, theme, locked, Modifier.weight(1f), onTile)
        }
    }
}

/** 動画サービスごとのタブ：そのサービスにピン留めした作品と、アプリを開くボタン */
@Composable
private fun VideoPage(tab: KidTab, theme: KidTheme, locked: Boolean, onTile: (Tile) -> Unit, onLocked: () -> Unit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        val pkg = tab.packageName?.takeIf { Launcher.isInstalled(context, it) }
        RetroWindow(theme, Modifier.fillMaxWidth().padding(end = 6.dp), title = tab.label, titleColor = theme.titleBars[0]) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                val icon = remember(pkg) { pkg?.let { Launcher.icon(context, it) } }
                if (icon != null) Image(icon, null, Modifier.size(44.dp)) else Text(tab.emoji, fontSize = 36.sp)
                DotText(if (tab.kind == TabKind.CATEGORY) tab.label else "${tab.label} の おきにいり", theme, 20, modifier = Modifier.padding(start = 12.dp).weight(1f))
                if (pkg != null) {
                    RetroButton(if (locked) "🔒 アプリ" else "アプリを ひらく", theme, theme.titleBars[1], fontSize = 16) {
                        if (locked) onLocked() else Launcher.launchPackage(context, pkg)
                    }
                }
            }
        }
        VSpace(18)
        TileGrid(tab.tiles, theme, locked, Modifier.weight(1f), onTile)
    }
}

// ---------------------------------------------------------------- タブ

private enum class TabKind { HOME, CLOCK, TIMETABLE, LOCAL_VIDEO, MUSIC, BOOKS, CATEGORY, VIDEO, WIDGETS }

private data class KidTab(
    val key: String,
    val kind: TabKind,
    val emoji: String,
    val label: String,
    val packageName: String? = null,
    val tiles: List<Tile> = emptyList(),
)

/** ホーム・とけい・動画サービスごと（ピン留めした作品があるものだけ）・ウィジェット */
/** 仕分けたカテゴリーのうち、この子に見せるもの（アプリが入っているものだけ） */
private fun categoryTabs(config: AppConfig, profileId: String, installed: List<com.theta.kidshome.data.InstalledApp>): List<KidTab> =
    config.categories.filter { profileId in it.visibleTo }.mapNotNull { cat ->
        val tiles = installed.filter { config.appCategory[it.packageName] == cat.id }.map { app ->
            Tile(
                id = "cat:${app.packageName}", type = TileType.APP, label = app.label, packageName = app.packageName,
                isVideo = cat.isVideo || app.packageName in Launcher.videoPackages,
            )
        }
        if (tiles.isEmpty()) null else KidTab("cat:${cat.id}", TabKind.CATEGORY, cat.emoji, cat.name, tiles = tiles)
    }

private fun kidTabs(tiles: List<Tile>, hasWidgets: Boolean, categories: List<KidTab> = emptyList()): List<KidTab> {
    val videos = LinkedHashMap<String, MutableList<Tile>>()
    val services = HashMap<String, com.theta.kidshome.data.VideoService?>()
    tiles.filter { it.type == TileType.VIDEO }.forEach { t ->
        val s = t.url?.let { Launcher.detectService(it) }
        val name = s?.name ?: "どうが"
        services[name] = s
        videos.getOrPut(name) { mutableListOf() } += t
    }
    return buildList {
        add(KidTab("home", TabKind.HOME, "🏠", "ホーム"))
        add(KidTab("clock", TabKind.CLOCK, "🕐", "とけい"))
        addAll(categories)
        videos.forEach { (name, list) ->
            val s = services[name]
            add(KidTab("video:$name", TabKind.VIDEO, s?.emoji ?: "🎬", name, s?.packageName, list))
        }
        if (hasWidgets) add(KidTab("widgets", TabKind.WIDGETS, "🧩", "ウィジェット"))
    }
}

/** 画面の右はしの、フォルダのみみ のようなタブ */
@Composable
private fun TabRail(tabs: List<KidTab>, selected: String, theme: KidTheme, locked: Boolean, onSelect: (String) -> Unit) {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxHeight().verticalScroll(rememberScrollState()).padding(start = 10.dp, top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        tabs.forEachIndexed { i, t ->
            val isSel = t.key == selected
            val shape = RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp)
            val tabInteraction = remember { MutableInteractionSource() }
            val tabFocused by tabInteraction.collectIsFocusedAsState()
            Box(
                Modifier
                    .offset(x = if (isSel) 0.dp else 10.dp)
                    .stickerShadow(theme.outline, if (isSel) 4.dp else 2.dp, 18.dp)
                    .width(84.dp)
                    .clip(shape)
                    .background(if (isSel) Color.White else theme.titleBars[i % theme.titleBars.size])
                    .border(if (tabFocused) 5.dp else 3.dp, if (tabFocused) theme.accent else theme.outline, shape)
                    .clickable(interactionSource = tabInteraction, indication = null) { onSelect(t.key) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val icon = remember(t.packageName) { t.packageName?.let { Launcher.icon(context, it) } }
                    Box(contentAlignment = Alignment.Center) {
                        if (icon != null) Image(icon, null, Modifier.size(36.dp)) else Text(t.emoji, fontSize = 28.sp)
                        if (locked && (t.kind == TabKind.VIDEO || t.kind == TabKind.LOCAL_VIDEO || t.kind == TabKind.CATEGORY && t.tiles.all { it.isVideo })) Text("🔒", fontSize = 18.sp, modifier = Modifier.offset(x = 14.dp, y = 12.dp))
                    }
                    Text(
                        t.label, fontFamily = DotFont, fontSize = 11.sp, color = theme.outline,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun key(id: Long, content: @Composable () -> Unit) = androidx.compose.runtime.key(id) { content() }

// ---------------------------------------------------------------- 上のバー

@Composable
private fun Header(config: AppConfig, theme: KidTheme, now: Long, updateBadge: Boolean, onGate: () -> Unit) {
    val cal = Calendar.getInstance().apply { timeInMillis = now }
    val (battery, charging) = rememberBattery()
    Row(Modifier.fillMaxWidth().padding(end = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        DotText(
            "${config.currentProfile.name}の ホーム", theme, 28, theme.text,
            modifier = Modifier.weight(1f, fill = false),
        )
        listOf(Color(0xFFFFE29A), Color(0xFFB8F0A8), Color(0xFFFFB3CF)).forEach { c ->
            Box(
                Modifier.padding(start = 8.dp).size(16.dp).clip(CircleShape).background(c)
                    .border(2.5.dp, theme.outline, CircleShape)
            )
        }
        Spacer(Modifier.weight(1f))
        Chip(theme, "%d:%02d".format(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE)))
        if (battery >= 0) Chip(theme, "${if (charging) "⚡" else "🔋"}$battery%")
        HSpace(8)
        if (config.profiles.size > 1) {
            config.profiles.forEach { p ->
                ProfileAvatar(
                    name = p.name,
                    theme = themeById(p.themeId),
                    selected = p.id == config.currentProfileId,
                    frame = theme,
                ) { Store.update { it.copy(currentProfileId = p.id) } }
            }
        }
        HSpace(8)
        HoldButton(theme, updateBadge, onGate)
    }
}

@Composable
private fun Chip(theme: KidTheme, text: String) {
    val shape = RoundedCornerShape(50)
    Box(
        Modifier.padding(horizontal = 4.dp).clip(shape).background(Color.White.copy(alpha = 0.85f))
            .border(2.5.dp, theme.outline, shape).padding(horizontal = 12.dp, vertical = 4.dp)
    ) { DotText(text, theme, 18) }
}

@Composable
private fun ProfileAvatar(name: String, theme: KidTheme, selected: Boolean, frame: KidTheme, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier.padding(horizontal = 6.dp).clickable(
            interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick,
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.offset(y = if (selected) (-4).dp else 0.dp)
                .stickerShadow(frame.outline, if (selected) 4.dp else 2.dp, 16.dp)
                .size(if (selected) 60.dp else 50.dp).clip(shape)
                .background(if (selected) theme.titleBars.first() else Color.White)
                .border(3.dp, frame.outline, shape),
            contentAlignment = Alignment.Center,
        ) { Text(theme.mascot, fontSize = if (selected) 32.sp else 26.sp) }
        DotText(name, frame, 12, frame.text, modifier = Modifier.padding(top = 4.dp))
    }
}

/** 2秒ながおしで開く、おうちのひとボタン（子どもがうっかり押さないように） */
@Composable
private fun HoldButton(theme: KidTheme, badge: Boolean, onTriggered: () -> Unit) {
    var progress by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    val shape = RoundedCornerShape(14.dp)
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    var held by remember { mutableIntStateOf(0) }
    Box(
        Modifier.stickerShadow(theme.outline, 3.dp, 14.dp).size(52.dp).clip(shape)
            .background(Color.White.copy(alpha = 0.85f))
            .border(if (focused) 5.dp else 3.dp, if (focused) theme.accent else theme.outline, shape)
            // テレビ：リモコンの決定ボタンを 2 びょう ながおし
            .onPreviewKeyEvent { e ->
                val center = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter
                if (!center) return@onPreviewKeyEvent false
                if (e.type == KeyEventType.KeyDown) {
                    held++
                    progress = (held / 20f).coerceAtMost(1f)
                    if (held == 20) { progress = 0f; onTriggered() }
                } else if (e.type == KeyEventType.KeyUp) {
                    held = 0
                    progress = 0f
                }
                true
            }
            .focusable(interactionSource = interaction)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown().consume()
                    val job = scope.launch {
                        val start = System.currentTimeMillis()
                        while (true) {
                            progress = ((System.currentTimeMillis() - start) / 2000f).coerceAtMost(1f)
                            if (progress >= 1f) {
                                progress = 0f
                                onTriggered()
                                break
                            }
                            delay(16)
                        }
                    }
                    waitForUpOrCancellation()
                    job.cancel()
                    progress = 0f
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (progress > 0f) {
            CircularProgressIndicator(
                progress = { progress }, color = theme.accent, strokeWidth = 4.dp,
                modifier = Modifier.size(44.dp),
            )
        }
        Text("⚙️", fontSize = 24.sp, modifier = Modifier.alpha(0.7f))
        if (badge) {
            Box(Modifier.align(Alignment.TopEnd).padding(5.dp).size(10.dp).clip(CircleShape).background(Color(0xFFFF4D6D)))
        }
    }
}

// ---------------------------------------------------------------- ウィジェット

@Composable
private fun MascotWindow(
    config: AppConfig, theme: KidTheme, stars: Int, playful: Boolean,
    onBurst: (Offset, List<String>) -> Unit, modifier: Modifier = Modifier,
) {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greeting = when (hour) {
        in 4..9 -> "おはよう！"
        in 10..16 -> "こんにちは！"
        else -> "こんばんは！"
    }
    var line by remember(theme.id) { mutableStateOf(greeting) }
    val jump = remember { Animatable(0f) }
    val spin = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var center by remember { mutableStateOf(Offset.Zero) }
    val idle = rememberInfiniteTransition(label = "idle")
    val bob by idle.animateFloat(0f, -6f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "bob")

    RetroWindow(theme, modifier, title = theme.name, titleColor = theme.titleBars[0]) {
        Column(Modifier.fillMaxWidth().padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            // ふきだし
            Box(
                Modifier.clip(RoundedCornerShape(14.dp)).background(theme.bubbles[0])
                    .border(2.5.dp, theme.outline, RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) { DotText(line, theme, 16) }
            Text(
                theme.mascot,
                fontSize = 68.sp,
                modifier = Modifier
                    .offset(y = (jump.value + if (playful) bob else 0f).dp)
                    .rotate(spin.value)
                    .onGloballyPositioned { center = it.boundsInRoot().center }
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        line = (theme.lines + "${config.currentProfile.name}、だいすき！").random()
                        onBurst(center, listOf("💗", "💖", "✨"))
                        if (!playful) return@clickable
                        scope.launch {
                            if (Random.nextBoolean()) {
                                jump.animateTo(-36f, tween(160))
                                jump.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                            } else {
                                spin.animateTo(360f, tween(500))
                                spin.snapTo(0f)
                            }
                        }
                    },
            )
            DotText("ほし ⭐ × $stars", theme, 14, theme.outline.copy(alpha = 0.75f))
        }
    }
}

private fun remainingText(until: Long, now: Long): String {
    val min = ((until - now + 59_999) / 60_000).toInt()
    return if (min >= 60) "${min / 60}じかん${if (min % 60 > 0) " ${min % 60}ふん" else ""}" else "${min}ふん"
}

@Composable
private fun LockWindow(config: AppConfig, theme: KidTheme, now: Long) {
    val total = (config.videoLockUntil - config.videoLockFrom).coerceAtLeast(1L)
    val done = ((now - config.videoLockFrom).toFloat() / total).coerceIn(0f, 1f)
    RetroWindow(theme, Modifier.fillMaxWidth(), title = "おしらせ", titleColor = Color(0xFFFFB3CF)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(config.lockReason.emoji, fontSize = 30.sp)
                Column(Modifier.padding(start = 10.dp)) {
                    DotText("${config.lockReason.title}！", theme, 20)
                    DotText("どうがは あと ${remainingText(config.videoLockUntil, now)}", theme, 15)
                }
            }
            VSpace(10)
            RetroProgress(done, theme, theme.accent, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun LockedDialog(config: AppConfig, theme: KidTheme, now: Long, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        RetroWindow(theme, Modifier.width(380.dp), title = "おしらせ", titleColor = Color(0xFFFFB3CF)) {
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${theme.mascot}${config.lockReason.emoji}", fontSize = 56.sp)
                DotText("いまは ${config.lockReason.title} だよ！", theme, 22)
                VSpace(6)
                DotText("どうがは あと ${remainingText(config.videoLockUntil, now)} まってね", theme, 16)
                VSpace(18)
                RetroButton("OK", theme, theme.titleBars[2], onClick = onDismiss)
            }
        }
    }
}

// ---------------------------------------------------------------- ボタン

@Composable
private fun TileGrid(tiles: List<Tile>, theme: KidTheme, locked: Boolean, modifier: Modifier, onTile: (Tile) -> Unit) {
    if (tiles.isEmpty()) {
        Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            RetroWindow(theme, title = "おしらせ", titleColor = theme.titleBars[3]) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(theme.mascot, fontSize = 64.sp)
                    DotText("おうちのひとに あそぶものを\nついか してもらってね", theme, 18)
                }
            }
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(190.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
        contentPadding = PaddingValues(end = 8.dp, bottom = 28.dp, top = 2.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        itemsIndexed(tiles, key = { _, t -> t.id }) { i, tile ->
            TileWindow(tile, theme, i, locked && tile.isVideo) { onTile(tile) }
        }
    }
}

@Composable
private fun TileWindow(tile: Tile, theme: KidTheme, index: Int, locked: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val icon = remember(tile.packageName, tile.type) {
        if (tile.type == TileType.APP && tile.packageName != null) Launcher.icon(context, tile.packageName) else null
    }
    val title = when {
        locked -> "ロックちゅう"
        tile.type == TileType.VIDEO -> tile.url?.let { Launcher.detectService(it)?.name } ?: "どうが"
        tile.type == TileType.DRAWING -> "おえかき"
        tile.type == TileType.CALL -> "でんわ"
        tile.type == TileType.INSTRUMENT -> "がっき"
        tile.type == TileType.EDUPAD -> "べんきょう"
        tile.type == TileType.WORDPRO -> "わーぷろ"
        tile.type == TileType.PUZZLE -> "パズル"
        tile.type == TileType.TYPING -> "タイピング"
        tile.type == TileType.MOJI -> "もじ"
        tile.type == TileType.KUKU -> "くく"
        tile.isVideo -> "どうが"
        else -> "アプリ"
    }
    RetroWindow(
        theme,
        Modifier.aspectRatio(0.86f),
        title = title,
        titleColor = if (locked) Color(0xFFDDDDDD) else theme.titleBars[index % theme.titleBars.size],
        onClick = onClick,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                Modifier.padding(10.dp).alpha(if (locked) 0.3f else 1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(84.dp).clip(CircleShape).background(theme.bubbles[index % theme.bubbles.size])
                        .border(2.5.dp, theme.outline.copy(alpha = 0.35f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (icon != null) Image(icon, contentDescription = null, modifier = Modifier.size(62.dp))
                    else Text(tile.emoji, fontSize = 44.sp)
                }
                VSpace(10)
                Text(
                    tile.label, fontFamily = DotFont, fontSize = 18.sp, color = theme.outline,
                    textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 22.sp,
                )
            }
            if (locked) Text("🔒", fontSize = 52.sp)
        }
    }
}

// ---------------------------------------------------------------- あそび

/** ときどき画面の下を歩いてくる いきもの。タップでつかまえると ほし がもらえる */
@Composable
private fun Walker(theme: KidTheme, width: Dp, height: Dp, onCatch: (Offset) -> Unit) {
    val density = LocalDensity.current
    val x = remember { Animatable(-1f) }
    var critter by remember { mutableStateOf("🐣") }
    var walking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val step = rememberInfiniteTransition(label = "step")
    val hop by step.animateFloat(0f, -10f, infiniteRepeatable(tween(260), RepeatMode.Reverse), label = "hop")

    LaunchedEffect(theme.id) {
        while (true) {
            delay(Random.nextLong(12_000, 25_000))
            critter = listOf("🐣", "🐞", "🐢", "🦔", "🐌", "🐿️", theme.decorations.first()).random()
            walking = true
            x.snapTo(-0.1f)
            x.animateTo(1.1f, tween(14_000, easing = LinearEasing))
            walking = false
        }
    }
    if (!walking) return
    val px = with(density) { (width * x.value).toPx() }
    val py = with(density) { (height - 70.dp).toPx() }
    Text(
        critter,
        fontSize = 44.sp,
        modifier = Modifier
            .offset { IntOffset(px.roundToInt(), (py + hop * density.density).roundToInt()) }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                onCatch(Offset(px + 50f, py + 50f))
                walking = false
                scope.launch { x.stop() }
            },
    )
}

@Composable
private fun BurstView(burst: Burst, onDone: () -> Unit) {
    val density = LocalDensity.current
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        t.animateTo(1f, tween(900))
        onDone()
    }
    val n = 8
    repeat(n) { i ->
        val angle = 2 * PI * i / n
        val dist = with(density) { (90.dp * t.value).toPx() }
        val size = with(density) { 14.dp.toPx() }
        Text(
            burst.emojis[i % burst.emojis.size],
            fontSize = 26.sp,
            modifier = Modifier
                .offset {
                    IntOffset(
                        (burst.at.x + cos(angle) * dist - size).roundToInt(),
                        (burst.at.y + sin(angle) * dist - size - 40 * t.value).roundToInt(),
                    )
                }
                .alpha(1f - t.value),
        )
    }
}

@Composable
private fun FloatingDecorations(theme: KidTheme) {
    val t = rememberInfiniteTransition(label = "deco")
    val phase by t.animateFloat(0f, 1f, infiniteRepeatable(tween(8000, easing = LinearEasing)), label = "phase")
    val items = remember(theme.id) {
        val r = Random(theme.id.hashCode())
        List(12) {
            Deco(theme.decorations[it % theme.decorations.size], r.nextFloat(), r.nextFloat(), 20 + r.nextInt(22), r.nextFloat())
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        items.forEach { d ->
            val wave = sin((phase + d.phase) * 2 * PI).toFloat()
            Text(
                d.emoji,
                fontSize = d.size.sp,
                modifier = Modifier
                    .offset(x = maxWidth * d.x - 20.dp, y = maxHeight * d.y + (wave * 14).dp)
                    .alpha(0.3f),
            )
        }
    }
}

private data class Deco(val emoji: String, val x: Float, val y: Float, val size: Int, val phase: Float)
