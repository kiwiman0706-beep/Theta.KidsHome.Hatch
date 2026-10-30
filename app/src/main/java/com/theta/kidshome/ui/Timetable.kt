package com.theta.kidshome.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theta.kidshome.data.Profile
import com.theta.kidshome.data.Store
import java.util.Calendar

val SchoolDays = listOf("げつ", "か", "すい", "もく", "きん", "ど")

/** かもく → えもじ と 色 */
private val subjects = listOf(
    listOf("こくご", "国語") to ("📖" to Color(0xFFFFB3B3)),
    listOf("さんすう", "算数") to ("🔢" to Color(0xFFB3D4FF)),
    listOf("せいかつ", "生活") to ("🌱" to Color(0xFFC9F0A8)),
    listOf("おんがく", "音楽") to ("🎵" to Color(0xFFFFD6F0)),
    listOf("ずこう", "図工", "図画工作") to ("🎨" to Color(0xFFFFE29A)),
    listOf("たいいく", "体育") to ("⚽" to Color(0xFFB8F0E0)),
    listOf("どうとく", "道徳") to ("💗" to Color(0xFFFFC4D8)),
    listOf("がっかつ", "学活", "学級活動") to ("🗣" to Color(0xFFE2D4FF)),
    listOf("えいご", "英語", "外国語") to ("🔤" to Color(0xFFD4F1FF)),
    listOf("りか", "理科") to ("🔬" to Color(0xFFDDF5D8)),
    listOf("しゃかい", "社会") to ("🗾" to Color(0xFFFFE6C7)),
    listOf("そうごう", "総合") to ("🌏" to Color(0xFFE5FFE9)),
    listOf("しょしゃ", "書写") to ("🖌" to Color(0xFFF0E6DA)),
    listOf("きゅうしょく", "給食") to ("🍚" to Color(0xFFFFF0C9)),
)

fun subjectStyle(name: String): Pair<String, Color> =
    subjects.firstOrNull { (keys, _) -> keys.any { name.contains(it) } }?.second ?: ("✏️" to Color(0xFFF5F0F2))

/** きょう（15 時を過ぎたら あした）の ようび。日よう・休みは つぎの げつ */
fun timetableDay(now: Long): Pair<Int, Boolean> {
    val c = Calendar.getInstance().apply { timeInMillis = now }
    var tomorrow = c.get(Calendar.HOUR_OF_DAY) >= 15
    if (tomorrow) c.add(Calendar.DAY_OF_MONTH, 1)
    var dow = c.get(Calendar.DAY_OF_WEEK) // 1 = にち
    if (dow == Calendar.SUNDAY) { dow = Calendar.MONDAY; tomorrow = true }
    return (dow - 2) to tomorrow
}

/** ホームの ちいさい じかんわり（きょう か あした）と もちもの */
@Composable
fun TimetableWindow(theme: KidTheme, profile: Profile, now: Long, modifier: Modifier = Modifier) {
    val (day, tomorrow) = timetableDay(now)
    val list = profile.timetable.getOrNull(day).orEmpty().filter { it.isNotBlank() }
    val bring = profile.belongings.getOrNull(day).orEmpty()
    RetroWindow(theme, modifier.fillMaxWidth(), title = "${if (tomorrow) "あした" else "きょう"}（${SchoolDays[day]}）の じかんわり", titleColor = theme.titleBars[2]) {
        Column(Modifier.padding(10.dp)) {
            if (list.isEmpty()) DotText("おやすみ", theme, 16)
            list.forEachIndexed { i, s ->
                val (emoji, color) = subjectStyle(s)
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(8.dp)).background(color).padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DotText("${i + 1}", theme, 14, modifier = Modifier.width(18.dp))
                    Text(emoji, fontSize = 16.sp)
                    DotText(s, theme, 15, modifier = Modifier.padding(start = 6.dp))
                }
            }
            if (bring.isNotBlank()) DotText("🎒 $bring", theme, 14, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

/** じかんわり の タブ：1 しゅうかん の表 */
@Composable
fun TimetablePage(theme: KidTheme, profile: Profile, now: Long) {
    val (today, _) = timetableDay(now)
    val days = if (profile.timetable[5].any { it.isNotBlank() }) 6 else 5
    val periods = profile.timetable.maxOf { it.size }.coerceAtLeast(1)
    RetroWindow(theme, Modifier.fillMaxSize().padding(end = 6.dp, bottom = 6.dp), title = "じかんわり", titleColor = theme.titleBars[2]) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Box(Modifier.width(36.dp))
                (0 until days).forEach { d ->
                    Box(
                        Modifier.weight(1f).padding(2.dp).clip(RoundedCornerShape(10.dp))
                            .background(if (d == today) theme.accent else theme.bubbles[0]).padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) { DotText(SchoolDays[d], theme, 20, if (d == today) Color.White else theme.outline) }
                }
            }
            (0 until periods).forEach { p ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(36.dp), contentAlignment = Alignment.Center) { DotText("${p + 1}", theme, 20) }
                    (0 until days).forEach { d ->
                        val s = profile.timetable[d].getOrNull(p).orEmpty()
                        val (emoji, color) = subjectStyle(s)
                        Column(
                            Modifier.weight(1f).heightIn(min = 64.dp).padding(2.dp).clip(RoundedCornerShape(10.dp))
                                .background(if (s.isBlank()) Color.Transparent else color)
                                .border(if (d == today) 3.dp else 1.dp, if (d == today) theme.accent else theme.outline.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                                .padding(4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            if (s.isNotBlank()) {
                                Text(emoji, fontSize = 20.sp)
                                Text(s, fontFamily = DotFont, fontSize = 15.sp, color = theme.outline, textAlign = TextAlign.Center, maxLines = 2)
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                Box(Modifier.width(36.dp), contentAlignment = Alignment.Center) { Text("🎒", fontSize = 20.sp) }
                (0 until days).forEach { d ->
                    Box(Modifier.weight(1f).padding(2.dp)) {
                        DotText(profile.belongings[d], theme, 13)
                    }
                }
            }
        }
    }
}

/** おうちのひと：じかんわり の入力（ようび を選んで、1〜6 じかんめ と もちもの） */
@Composable
fun TimetableEditor(profile: Profile) {
    var day by remember(profile.id) { mutableIntStateOf(0) }
    Text("じかんわり（入れると、ホームに「きょう／あした の じかんわり」と じかんわり のタブが出ます）", fontSize = 13.sp, color = Color.Gray, modifier = Modifier.padding(top = 12.dp))
    Row(Modifier.horizontalScroll(rememberScrollState())) {
        SchoolDays.forEachIndexed { i, d ->
            TextButton(onClick = { day = i }) { Text(if (i == day) "【$d】" else d, fontSize = 16.sp) }
        }
    }
    val list = profile.timetable[day]
    fun setPeriod(p: Int, v: String) = Store.updateProfile(profile.id) { pr ->
        val cur = pr.timetable[day].toMutableList()
        while (cur.size <= p) cur += ""
        cur[p] = v
        pr.copy(timetable = pr.timetable.mapIndexed { i, l -> if (i == day) cur.dropLastWhile { it.isBlank() } else l })
    }
    (0 until 6).forEach { p ->
        OutlinedTextField(
            value = list.getOrNull(p).orEmpty(), onValueChange = { setPeriod(p, it) }, singleLine = true,
            label = { Text("${p + 1} じかんめ（例：こくご）") }, modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        )
    }
    OutlinedTextField(
        value = profile.belongings[day],
        onValueChange = { v -> Store.updateProfile(profile.id) { pr -> pr.copy(belongings = pr.belongings.mapIndexed { i, s -> if (i == day) v else s }) } },
        singleLine = true, label = { Text("もちもの（例：たいそうふく、けんばんハーモニカ）") },
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
    )
    androidx.compose.foundation.layout.Spacer(Modifier.height(4.dp))
}
