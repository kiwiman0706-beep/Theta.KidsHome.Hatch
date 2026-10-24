package com.theta.kidshome.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.theta.kidshome.data.Store

enum class PinMode { VERIFY, CHANGE }

private const val PIN_LENGTH = 4

/**
 * おうちのひと用の暗証番号ダイアログ。
 * まだ PIN が決まっていなければ、まず設定してもらう。
 */
@Composable
fun PinGate(mode: PinMode = PinMode.VERIFY, onSuccess: () -> Unit, onDismiss: () -> Unit) {
    val savedPin = Store.config.pin
    val setting = mode == PinMode.CHANGE || savedPin == null
    var input by remember { mutableStateOf("") }
    var firstEntry by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var errorCount by remember { mutableIntStateOf(0) }
    val shake = remember { Animatable(0f) }

    LaunchedEffect(errorCount) {
        if (errorCount == 0) return@LaunchedEffect
        for (x in listOf(24f, -24f, 16f, -16f, 8f, 0f)) shake.animateTo(x, tween(50))
    }

    fun onComplete(pin: String) {
        input = ""
        if (!setting) {
            if (pin == savedPin) onSuccess() else {
                error = "ちがうよ"
                errorCount++
            }
            return
        }
        val first = firstEntry
        if (first == null) {
            firstEntry = pin
            error = null
        } else if (first == pin) {
            Store.update { it.copy(pin = pin) }
            onSuccess()
        } else {
            firstEntry = null
            error = "一致しませんでした。最初からどうぞ"
            errorCount++
        }
    }

    val title = when {
        !setting -> "おうちのひと メニュー"
        firstEntry == null -> if (savedPin == null) "はじめに暗証番号(4けた)を決めてください" else "新しい暗証番号(4けた)"
        else -> "もう一度入力してください"
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(32.dp), color = Color.White) {
            Column(
                Modifier.padding(28.dp).width(300.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("🔑", fontSize = 40.sp)
                Text(
                    title, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center, color = Color(0xFF555555),
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier.offset(x = shake.value.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    repeat(PIN_LENGTH) { i ->
                        Box(
                            Modifier.size(20.dp).clip(CircleShape)
                                .background(if (i < input.length) Color(0xFFE0608A) else Color(0xFFEEEEEE))
                        )
                    }
                }
                Text(
                    error ?: " ", color = Color(0xFFD03B5C), fontSize = 14.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Spacer(Modifier.height(8.dp))
                val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "✕", "0", "⌫")
                keys.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        row.forEach { k ->
                            Box(
                                Modifier.padding(vertical = 6.dp).size(72.dp).clip(CircleShape)
                                    .background(Color(0xFFF5F0F2))
                                    .clickable {
                                        when (k) {
                                            "✕" -> onDismiss()
                                            "⌫" -> input = input.dropLast(1)
                                            else -> if (input.length < PIN_LENGTH) {
                                                input += k
                                                if (input.length == PIN_LENGTH) onComplete(input)
                                            }
                                        }
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(k, fontSize = 26.sp, color = Color(0xFF444444))
                            }
                        }
                    }
                }
            }
        }
    }
}
