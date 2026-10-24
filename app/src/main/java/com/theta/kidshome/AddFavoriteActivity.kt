package com.theta.kidshome

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theta.kidshome.data.Launcher
import com.theta.kidshome.data.Store
import com.theta.kidshome.ui.ParentTheme
import com.theta.kidshome.ui.ParentGate
import com.theta.kidshome.ui.VideoFields
import com.theta.kidshome.ui.newVideoTile
import com.theta.kidshome.ui.themeById

/** 動画アプリの「共有」から呼ばれて、お気に入り動画として登録する画面 */
class AddFavoriteActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT).orEmpty()
        val sharedUrl = Launcher.extractUrl(text).orEmpty()
        val guessedTitle = subject.ifBlank { text.replace(sharedUrl, "").trim() }.take(20)

        setContent {
            ParentTheme {
                var unlocked by remember { mutableStateOf(false) }
                if (!unlocked) {
                    Box(Modifier.fillMaxSize())
                    ParentGate(onSuccess = { unlocked = true }, onDismiss = { finish() })
                    return@ParentTheme
                }
                val config = Store.config
                var url by remember { mutableStateOf(sharedUrl) }
                var label by remember { mutableStateOf(guessedTitle) }
                var emoji by remember { mutableStateOf("🎬") }
                var targets by remember { mutableStateOf(config.profiles.map { it.id }.toSet()) }

                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(
                        Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
                            .verticalScroll(rememberScrollState())
                            .padding(24.dp)
                            .widthIn(max = 640.dp),
                    ) {
                        Text("🎬 お気に入り動画を登録", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(16.dp))
                        VideoFields(url, { url = it }, label, { label = it }, emoji, { emoji = it })
                        Spacer(Modifier.height(16.dp))
                        Text("だれのホームに追加する？", fontSize = 13.sp, color = Color.Gray)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            config.profiles.forEach { p ->
                                FilterChip(
                                    selected = p.id in targets,
                                    onClick = { targets = if (p.id in targets) targets - p.id else targets + p.id },
                                    label = { Text("${themeById(p.themeId).mascot} ${p.name}") },
                                )
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth().padding(top = 20.dp),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(onClick = { finish() }) { Text("やめる") }
                            Button(
                                enabled = url.isNotBlank() && targets.isNotEmpty(),
                                onClick = {
                                    targets.forEach { id -> Store.addTiles(id, listOf(newVideoTile(url, label, emoji))) }
                                    Toast.makeText(this@AddFavoriteActivity, "登録しました", Toast.LENGTH_SHORT).show()
                                    finish()
                                },
                            ) { Text("登録する") }
                        }
                    }
                }
            }
        }
    }
}
