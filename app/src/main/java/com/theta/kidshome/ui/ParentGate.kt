package com.theta.kidshome.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.theta.kidshome.data.Store
import kotlin.random.Random

/** おうちのひとゲートの問題（かけ算） */
data class GateQuestion(val a: Int, val b: Int) {
    val answer get() = a * b

    companion object {
        /** hard = 2けた×2けた、そうでなければ 2けた×1けた（九九では解けないように 1 の段・10 の倍数は避ける） */
        fun next(hard: Boolean, random: Random = Random.Default): GateQuestion {
            fun twoDigit(): Int {
                while (true) {
                    val n = random.nextInt(12, 99)
                    if (n % 10 != 0) return n
                }
            }
            return GateQuestion(twoDigit(), if (hard) twoDigit() else random.nextInt(3, 10))
        }
    }
}

/**
 * おうちのひと かくにん。かけ算に答えられたら onSuccess。
 * まちがえたら別の問題に変わる。
 */
@Composable
fun ParentGate(onSuccess: () -> Unit, onDismiss: () -> Unit) {
    val config = Store.config
    val theme = themeById(config.currentProfile.themeId)
    var question by remember { mutableStateOf(GateQuestion.next(config.hardGate)) }
    var input by remember { mutableStateOf("") }
    var wrong by remember { mutableIntStateOf(0) }
    val shake = remember { Animatable(0f) }

    LaunchedEffect(wrong) {
        if (wrong == 0) return@LaunchedEffect
        for (x in listOf(24f, -24f, 16f, -16f, 8f, 0f)) shake.animateTo(x, tween(50))
    }

    Dialog(onDismissRequest = onDismiss) {
        RetroWindow(theme, title = "おうちのひと かくにん", titleColor = theme.titleBars[2], modifier = Modifier.width(340.dp)) {
            Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                DotText("こたえを いれてね", theme, 15, theme.outline.copy(alpha = 0.7f))
                Row(Modifier.offset(x = shake.value.dp).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    DotText("${question.a} × ${question.b} =", theme, 30)
                    Box(
                        Modifier.padding(start = 10.dp).width(96.dp).height(52.dp)
                            .clip(RoundedCornerShape(10.dp)).background(Color.White)
                            .border(3.dp, theme.outline, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center,
                    ) { DotText(input, theme, 30) }
                }
                DotText(if (wrong > 0) "ちがうよ。べつの もんだい だよ" else " ", theme, 13, Color(0xFFD03B5C))
                val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "✕", "0", "⌫")
                keys.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 10.dp)) {
                        row.forEach { k ->
                            Box(
                                Modifier.size(width = 76.dp, height = 56.dp)
                                    .stickerShadow(theme.outline, 3.dp, 12.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (k == "✕" || k == "⌫") theme.titleBars[1] else Color.White)
                                    .border(3.dp, theme.outline, RoundedCornerShape(12.dp))
                                    .clickable {
                                        when (k) {
                                            "✕" -> onDismiss()
                                            "⌫" -> input = input.dropLast(1)
                                            else -> if (input.length < 4) input += k
                                        }
                                    },
                                contentAlignment = Alignment.Center,
                            ) { Text(k, fontFamily = DotFont, fontSize = 26.sp, color = theme.outline) }
                        }
                    }
                }
                RetroButton("OK", theme, theme.titleBars[0], modifier = Modifier.padding(top = 16.dp)) {
                    if (input.toIntOrNull() == question.answer) {
                        onSuccess()
                    } else {
                        wrong++
                        input = ""
                        question = GateQuestion.next(config.hardGate)
                    }
                }
            }
        }
    }
}
