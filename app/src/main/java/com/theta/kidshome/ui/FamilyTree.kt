package com.theta.kidshome.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.theta.kidshome.data.Kin
import com.theta.kidshome.data.KinStore
import com.theta.kidshome.data.Person
import com.theta.kidshome.data.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import kotlin.math.hypot

/** カレンダー など から ひらく かけいず（ぜんがめん） */
@Composable
fun FamilyTreeDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        FamilyTreeScreen(onBack = onDismiss)
    }
}

private class Placed(val p: Person, val x: Float, val y: Float)

/**
 * かけいず：ひと から ひと へ ゆび で ひっぱる と「○○ から 見る と △△ は おばさん」。
 * タップ で たんじょうび・なんさい、✏️（おうちのひと）で ふやす・なおす・かお写真。
 */
@Composable
fun FamilyTreeScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val config = Store.config
    val theme = themeById(config.currentProfile.themeId)
    LaunchedEffect(Unit) { KinStore.load(context) }
    val speak = rememberSpeaker()
    val people = KinStore.people
    val members = people.filter { !it.friend }
    val friends = people.filter { it.friend }
    val me = KinStore.me(config.currentProfileId)
    var edit by remember { mutableStateOf(false) }
    var gate by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Person?>(null) }
    var detail by remember { mutableStateOf<Person?>(null) }
    var answer by remember { mutableStateOf<Pair<Person, Person>?>(null) }

    fun callOf(p: Person) = if (p.id == me?.id) "わたし" else p.call

    Column(Modifier.fillMaxSize().background(theme.bgBottom).windowInsetsPadding(WindowInsets.safeDrawing).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RetroIconButton("🏠", theme, onClick = onBack)
            DotText("かけいず", theme, 28, theme.text, modifier = Modifier.padding(start = 10.dp))
            DotText("  ひと から ひと へ ゆび で ひっぱって みよう", theme, 16, theme.text.copy(alpha = 0.7f), modifier = Modifier.weight(1f))
            if (edit) RetroButton("＋ ひと", theme, theme.titleBars[1], fontSize = 16, modifier = Modifier.padding(end = 6.dp)) {
                editing = Person(KinStore.newId(), "")
            }
            RetroButton(if (edit) "✅ おわり" else "✏️", theme, if (edit) theme.titleBars[0] else Color.White, fontSize = 16) {
                if (edit) edit = false else gate = true
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (members.isEmpty()) {
                DotText(
                    "まだ だれも いません。おうちのひと に ✏️ で かぞく を いれて もらってね（かお写真・たんじょうび・おとうさん／おかあさん を えらぶ だけ）",
                    theme, 18, modifier = Modifier.align(Alignment.Center).padding(40.dp),
                )
            } else {
                Tree(members, me, theme, Modifier.fillMaxSize(),
                    onTap = { p -> if (edit) editing = p else detail = p },
                    onLink = { a, b ->
                        answer = a to b
                        val r = Kin.relation(people, a.id, b.id)
                        if (r != null) speak("${callOf(a)} から みる と、${b.call} は、${r.text}")
                    },
                )
            }
        }
        if (friends.isNotEmpty() || edit) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                DotText("🎈 ともだち ", theme, 16)
                friends.sortedBy { it.birthMonth * 100 + it.birthDay }.forEach { f ->
                    Row(
                        Modifier.padding(end = 8.dp).clip(RoundedCornerShape(30.dp)).background(Color.White).border(2.dp, theme.outline.copy(alpha = 0.3f), RoundedCornerShape(30.dp))
                            .clickable { if (edit) editing = f else detail = f }.padding(end = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Face(f, 40.dp, theme)
                        DotText(" ${f.call}" + if (f.hasBirthday) "  🎂${f.birthMonth}/${f.birthDay}" else "", theme, 15)
                    }
                }
                if (edit) RetroButton("＋ ともだち", theme, theme.titleBars[2], fontSize = 14) { editing = Person(KinStore.newId(), "", friend = true) }
            }
        }
    }

    answer?.let { (a, b) ->
        val ab = Kin.relation(people, a.id, b.id)
        val ba = Kin.relation(people, b.id, a.id)
        Dialog(onDismissRequest = { answer = null }) {
            RetroWindow(theme, Modifier.width(560.dp), title = "だれ かな？", titleColor = theme.titleBars[3], onClick = { answer = null }) {
                Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Face(a, 84.dp, theme); Text("  ➡  ", fontSize = 30.sp, color = theme.outline); Face(b, 84.dp, theme)
                    }
                    if (ab == null) DotText("${callOf(a)} と ${b.call} は つながって いないよ", theme, 20, modifier = Modifier.padding(top = 10.dp))
                    else {
                        DotText("${callOf(a)} から みる と", theme, 20, modifier = Modifier.padding(top = 10.dp))
                        DotText("${b.call} は「${ab.text}」", theme, 32, theme.text)
                        if (ab.term != null && ab.term != "じぶん") DotText("（${ab.chain.replaceFirst(a.call, callOf(a))}）", theme, 15, theme.outline.copy(alpha = 0.7f))
                        ba?.let { DotText("${b.call} から みる と ${callOf(a)} は「${it.text}」", theme, 18, modifier = Modifier.padding(top = 10.dp)) }
                    }
                }
            }
        }
    }
    detail?.let { p -> PersonCard(p, me, theme) { detail = null } }
    editing?.let { p -> PersonEditor(p, theme, onDone = { editing = null }) }
    if (gate) ParentGate(onSuccess = { gate = false; edit = true }, onDismiss = { gate = false })
}

/** かお（写真 が なければ えもじ） */
@Composable
fun Face(p: Person, size: Dp, theme: KidTheme) {
    val context = LocalContext.current
    val img by produceState<ImageBitmap?>(null, p.id, KinStore.photoVersion) {
        value = withContext(Dispatchers.IO) { KinStore.photoBitmap(context, p.id)?.asImageBitmap() }
    }
    Box(Modifier.size(size).clip(CircleShape).background(Color(0xFFFFF4E0)).border(3.dp, if (p.female) Color(0xFFFF8FB1) else if (p.male) Color(0xFF7FB2FF) else theme.outline, CircleShape), contentAlignment = Alignment.Center) {
        val i = img
        if (i != null) Image(i, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else {
            val c = Calendar.getInstance()
            val age = Kin.age(p, c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
            Text(
                when {
                    age != null && age < 13 -> if (p.female) "👧" else if (p.male) "👦" else "🧒"
                    age != null && age >= 60 -> if (p.female) "👵" else if (p.male) "👴" else "🧓"
                    else -> if (p.female) "👩" else if (p.male) "👨" else "🙂"
                },
                fontSize = (size.value * 0.55f).sp,
            )
        }
    }
}

@Composable
private fun Tree(members: List<Person>, me: Person?, theme: KidTheme, modifier: Modifier, onTap: (Person) -> Unit, onLink: (Person, Person) -> Unit) {
    val density = LocalDensity.current
    var drag by remember { mutableStateOf<Pair<Person, Offset>?>(null) }
    BoxWithConstraints(modifier) {
        val w = constraints.maxWidth.toFloat(); val h = constraints.maxHeight.toFloat()
        val placed = remember(members, me, w, h) { layout(members, me, w, h) }
        val step = placed.second
        val cardPx = minOf(step * 0.8f, with(density) { 120.dp.toPx() })
        val nodes = placed.first
        val nodesNow = rememberUpdatedState(nodes)
        val byId = nodes.associateBy { it.p.id }
        Canvas(Modifier.fillMaxSize()) {
            val line = theme.outline.copy(alpha = 0.55f)
            val r = cardPx / 2
            // ふうふ
            nodes.forEach { n ->
                val s = (n.p.spouse ?: nodes.firstOrNull { it.p.spouse == n.p.id }?.p?.id)?.let { byId[it] } ?: return@forEach
                if (s.x > n.x) {
                    drawLine(Color(0xFFFF8FB1), Offset(n.x + r, n.y - 4), Offset(s.x - r, s.y - 4), 4.dp.toPx())
                    drawLine(Color(0xFFFF8FB1), Offset(n.x + r, n.y + 4), Offset(s.x - r, s.y + 4), 4.dp.toPx())
                }
            }
            // おや → こ
            nodes.forEach { c ->
                val ps = listOfNotNull(c.p.father, c.p.mother).mapNotNull { byId[it] }
                if (ps.isEmpty()) return@forEach
                val top = Offset(ps.map { it.x }.average().toFloat(), ps.first().y + if (ps.size == 2) 0f else r)
                val midY = (top.y + c.y - r) / 2 + r * 0.3f
                drawLine(line, top, Offset(top.x, midY), 3.dp.toPx(), StrokeCap.Round)
                drawLine(line, Offset(top.x, midY), Offset(c.x, midY), 3.dp.toPx(), StrokeCap.Round)
                drawLine(line, Offset(c.x, midY), Offset(c.x, c.y - r - 18.dp.toPx()), 3.dp.toPx(), StrokeCap.Round)
            }
            drag?.let { (p, at) ->
                val from = byId[p.id] ?: return@let
                drawLine(theme.accent, Offset(from.x, from.y), at, 7.dp.toPx(), StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(24f, 14f)))
                drawCircle(theme.accent, 14.dp.toPx(), at)
            }
        }
        nodes.forEach { n ->
            val sz = with(density) { cardPx.toDp() }
            Column(
                Modifier.offset(with(density) { (n.x - cardPx / 2).toDp() }, with(density) { (n.y - cardPx / 2).toDp() }).width(sz),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box {
                    Face(n.p, sz, theme)
                    if (n.p.id == me?.id) Text("⭐", fontSize = 22.sp, modifier = Modifier.align(Alignment.TopEnd))
                }
                Text(n.p.call, fontSize = (sz.value * 0.16f).coerceIn(12f, 20f).sp, fontWeight = FontWeight.Bold, color = theme.outline, maxLines = 1)
            }
        }
        // ゆび：タップ か ひっぱる
        Box(Modifier.fillMaxSize().pointerInput(members, me, w, h) {
            awaitEachGesture {
                val down = awaitFirstDown()
                val hit = { o: Offset -> nodesNow.value.minByOrNull { hypot(it.x - o.x, it.y - o.y) }?.takeIf { hypot(it.x - o.x, it.y - o.y) < cardPx * 0.6f } }
                val from = hit(down.position) ?: return@awaitEachGesture
                var at = down.position
                var moved = false
                while (true) {
                    val ev = awaitPointerEvent()
                    val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                    if (!c.pressed) break
                    at = c.position
                    if (hypot(at.x - down.position.x, at.y - down.position.y) > viewConfiguration.touchSlop) moved = true
                    if (moved) drag = from.p to at
                    c.consume()
                }
                drag = null
                if (!moved) onTap(from.p)
                else hit(at)?.takeIf { it.p.id != from.p.id }?.let { onLink(from.p, it.p) }
            }
        })
    }
}

/** 世代 ごと の 行 に ならべる。おや の した に こ、ふうふ は となり */
private fun layout(members: List<Person>, me: Person?, w: Float, h: Float): Pair<List<Placed>, Float> {
    val root = me?.takeIf { m -> members.any { it.id == m.id } } ?: members.first()
    val gen = Kin.generations(members, root.id).toMutableMap()
    // つながって いない 人 は いちばん 下 の 行
    val maxGen = (gen.values.maxOrNull() ?: 0)
    members.filter { it.id !in gen }.forEach { gen[it.id] = maxGen + 1 }
    val rows = gen.entries.groupBy({ it.value }, { it.key }).toSortedMap()
    val byId = members.associateBy { it.id }
    val x = HashMap<String, Float>()
    val maxCount = rows.values.maxOf { it.size }
    val step = w / (maxCount + 0.3f)
    val rowH = h / rows.size
    val out = ArrayList<Placed>()
    rows.entries.forEachIndexed { ri, (_, ids) ->
        val order = ids.mapNotNull { byId[it] }.sortedBy { p ->
            val ps = listOfNotNull(p.father, p.mother).mapNotNull { x[it] }
            if (ps.isNotEmpty()) ps.average().toFloat() else members.indexOf(p) * 0.001f + 1e6f
        }
        // ふうふ を となり に（おや が いる ほう の よこ へ）
        val arranged = ArrayList<Person>()
        order.forEach { p ->
            if (p in arranged) return@forEach
            val sp = order.firstOrNull { it.id == p.spouse || it.spouse == p.id }
            if (sp != null && sp !in arranged) {
                val pHas = p.father != null || p.mother != null
                if (pHas || (sp.father == null && sp.mother == null)) { arranged += p; arranged += sp } else { arranged += sp; arranged += p }
            } else arranged += p
        }
        val y = rowH * (ri + 0.45f)
        arranged.forEachIndexed { i, p ->
            val px = w / 2 + (i - (arranged.size - 1) / 2f) * step
            x[p.id] = px
            out += Placed(p, px, y)
        }
    }
    return out to step
}

/** タップ：たんじょうび・なんさい・えと・わたし から 見る と */
@Composable
private fun PersonCard(p: Person, me: Person?, theme: KidTheme, onDismiss: () -> Unit) {
    val c = Calendar.getInstance()
    val age = Kin.age(p, c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    val days = Kin.daysUntil(p, c)
    val rel = me?.let { Kin.relation(KinStore.people, it.id, p.id) }
    Dialog(onDismissRequest = onDismiss) {
        RetroWindow(theme, Modifier.width(460.dp), title = p.call, titleColor = theme.titleBars[1], onClick = onDismiss) {
            Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Face(p, 140.dp, theme)
                Text(p.name, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = theme.outline)
                if (p.kana.isNotBlank() && p.kana != p.name) Text(p.kana, fontSize = 16.sp, color = theme.outline)
                rel?.takeIf { it.term != "じぶん" }?.let { DotText("わたし から みる と「${it.text}」", theme, 20) }
                if (p.hasBirthday) {
                    DotText("🎂 たんじょうび：${p.birthMonth}がつ ${p.birthDay}にち", theme, 20, modifier = Modifier.padding(top = 8.dp))
                    when (days) {
                        0 -> DotText("🎉 きょう が たんじょうび！ おめでとう！", theme, 22, Color(0xFFD03B5C))
                        null -> {}
                        else -> DotText("あと $days にち" + (age?.let { " で ${it + 1}さい" } ?: ""), theme, 18)
                    }
                }
                age?.let { DotText("いま $it さい", theme, 18) }
                if (p.birthYear > 0) {
                    val e = com.theta.kidshome.data.Almanac.eto(p.birthYear)
                    DotText("${p.birthYear}ねん うまれ ${e.emoji} ${e.kana}（${e.animal}）どし", theme, 16)
                }
            }
        }
    }
}

/** おうちのひと：ひと を ふやす・なおす */
@Composable
private fun PersonEditor(start: Person, theme: KidTheme, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var p by remember { mutableStateOf(start) }
    var ask by remember { mutableStateOf(false) }
    val others = KinStore.people.filter { it.id != p.id && !it.friend }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { withContext(Dispatchers.IO) { runCatching { KinStore.setPhoto(context, p.id, uri) } } }
    }
    fun num(s: String) = s.filter { it.isDigit() }.take(4).toIntOrNull() ?: 0
    Dialog(onDismissRequest = onDone, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        RetroWindow(theme, Modifier.padding(20.dp).width(720.dp), title = if (start.name.isBlank()) "ひと を ふやす" else "なおす：${start.call}", titleColor = theme.titleBars[2]) {
            Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.clickable { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Face(p, 96.dp, theme) }
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        OutlinedTextField(p.name, { p = p.copy(name = it) }, singleLine = true, label = { Text("なまえ（パパ・ゆみおばさん など）") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(p.kana, { p = p.copy(kana = it) }, singleLine = true, label = { Text("よびかた（ひらがな、なくても よい）") }, modifier = Modifier.fillMaxWidth())
                    }
                }
                Text("👆 かお を おす と 写真 を えらべます（まんなか を まるく きりとり）", fontSize = 12.sp, color = Color.Gray)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    listOf("m" to "おとこ", "f" to "おんな").forEach { (k, l) -> FilterChip(p.sex == k, { p = p.copy(sex = k) }, label = { Text(l) }, modifier = Modifier.padding(end = 6.dp)) }
                    Spacer(Modifier.width(12.dp))
                    Text("ともだち", fontSize = 14.sp); Switch(p.friend, { p = p.copy(friend = it) })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🎂", fontSize = 22.sp)
                    OutlinedTextField(if (p.birthYear > 0) "${p.birthYear}" else "", { p = p.copy(birthYear = num(it)) }, singleLine = true, label = { Text("ねん（なくても）") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(150.dp).padding(start = 6.dp))
                    OutlinedTextField(if (p.birthMonth > 0) "${p.birthMonth}" else "", { p = p.copy(birthMonth = num(it).coerceIn(0, 12)) }, singleLine = true, label = { Text("がつ") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(90.dp).padding(start = 6.dp))
                    OutlinedTextField(if (p.birthDay > 0) "${p.birthDay}" else "", { p = p.copy(birthDay = num(it).coerceIn(0, 31)) }, singleLine = true, label = { Text("にち") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(90.dp).padding(start = 6.dp))
                }
                if (!p.friend) {
                    @Composable fun pickOne(label: String, cur: String?, list: List<Person>, set: (String?) -> Unit) {
                        Text(label, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Row(Modifier.horizontalScroll(rememberScrollState())) {
                            FilterChip(cur == null, { set(null) }, label = { Text("なし") }, modifier = Modifier.padding(end = 6.dp))
                            list.forEach { o -> FilterChip(cur == o.id, { set(o.id) }, label = { Text(o.call) }, modifier = Modifier.padding(end = 6.dp)) }
                        }
                    }
                    pickOne("おとうさん は？", p.father, others.filter { !it.female }) { p = p.copy(father = it) }
                    pickOne("おかあさん は？", p.mother, others.filter { !it.male }) { p = p.copy(mother = it) }
                    pickOne("けっこん して いる 人 は？", p.spouse ?: others.firstOrNull { it.spouse == p.id }?.id, others) { p = p.copy(spouse = it) }
                    Text("この 子 本人 なら（★ が つく）", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Row {
                        FilterChip(p.profileId == null, { p = p.copy(profileId = null) }, label = { Text("ちがう") }, modifier = Modifier.padding(end = 6.dp))
                        Store.config.profiles.forEach { pr -> FilterChip(p.profileId == pr.id, { p = p.copy(profileId = pr.id) }, label = { Text(pr.name) }, modifier = Modifier.padding(end = 6.dp)) }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.End) {
                    if (KinStore.people.any { it.id == p.id }) TextButton(onClick = { ask = true }) { Text("けす", color = Color(0xFFD03B5C)) }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDone) { Text("やめる") }
                    RetroButton("ほぞん", theme, theme.titleBars[0], fontSize = 18) {
                        if (p.name.isNotBlank()) KinStore.put(context, p)
                        onDone()
                    }
                }
            }
        }
    }
    if (ask) AlertDialog(
        onDismissRequest = { ask = false },
        title = { Text("${p.call} を けす？") },
        confirmButton = { TextButton(onClick = { ask = false; KinStore.remove(context, p.id); onDone() }) { Text("けす") } },
        dismissButton = { TextButton(onClick = { ask = false }) { Text("やめる") } },
    )
}
