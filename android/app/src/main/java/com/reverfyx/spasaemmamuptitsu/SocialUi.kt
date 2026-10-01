package com.reverfyx.spasaemmamuptitsu

import android.media.MediaPlayer
import android.net.Uri
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage

@Composable
fun InlineVideo(url: String, modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    AndroidView(
        modifier = modifier.background(Color.Black),
        factory = { context ->
            VideoView(context).apply {
                tag = url
                setVideoURI(Uri.parse(url))
                setOnPreparedListener { mp ->
                    mp.isLooping = autoPlay
                    mp.setVolume(if (autoPlay) 0f else 1f, if (autoPlay) 0f else 1f)
                    if (autoPlay) start()
                }
                if (!autoPlay) setOnClickListener {
                    if (isPlaying) pause() else start()
                }
            }
        },
        update = { view ->
            if (view.tag != url) {
                view.tag = url
                view.setVideoURI(Uri.parse(url))
            }
        }
    )
}

@Composable
private fun ProfileAvatar(user: UserDto, size: Int = 92) {
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(Color(0xFF7157E6), Color(0xFFE748A7)))),
        contentAlignment = Alignment.Center
    ) {
        if (!user.avatarUrl.isNullOrBlank()) {
            AsyncImage(
                model = user.avatarUrl,
                contentDescription = "Аватар",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(user.username.take(1).uppercase(), fontSize = (size / 2.5).sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun NameBadges(user: UserDto) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(user.displayName ?: user.username, fontSize = 24.sp, fontWeight = FontWeight.Black)
        if (user.verified) {
            Spacer(Modifier.width(6.dp))
            Box(Modifier.size(20.dp).background(Color(0xFF2E8CFF), CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }
        if (user.sponsorBadge) {
            Spacer(Modifier.width(6.dp))
            Box(Modifier.size(20.dp).background(Color(0xFF27C97B), CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Star, null, tint = Color.White, modifier = Modifier.size(13.dp))
            }
        }
    }
}

@Composable
private fun Stat(value: Int, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value.toString(), fontSize = 18.sp, fontWeight = FontWeight.Black)
        Text(label, color = TextMuted, fontSize = 10.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TikTokProfileScreen(
    vm: MainViewModel,
    onPost: (Int) -> Unit,
    own: Boolean
) {
    val profile = vm.profile
    var editOpen by remember { mutableStateOf(false) }
    var supportOpen by remember { mutableStateOf(false) }

    if (editOpen && profile != null) {
        EditProfileSheet(vm, profile.user) { editOpen = false }
    }
    if (supportOpen) {
        SupportSheet(vm) { supportOpen = false }
    }

    if (vm.profileLoading && profile == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Accent)
        }
        return
    }
    if (profile == null) {
        ConnectionEmpty("Профиль не загрузился") { vm.retryConnection() }
        return
    }

    val user = profile.user
    val themeGradient = when (user.theme) {
        "red" -> listOf(Color(0xFF45111B), Color(0xFF0A0A0C))
        "violet" -> listOf(Color(0xFF24164C), Color(0xFF0A0A0C))
        "black" -> listOf(Color.Black, Color(0xFF0A0A0C))
        else -> listOf(Color(0xFF161820), Color(0xFF0A0A0C))
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Box(Modifier.fillMaxWidth().height(220.dp).background(Color(0xFF111216))) {
                when {
                    user.coverUrl.isNullOrBlank() -> Box(
                        Modifier.fillMaxSize().background(Brush.linearGradient(themeGradient))
                    )
                    user.coverType == "video" -> InlineVideo(user.coverUrl, Modifier.fillMaxSize(), autoPlay = true)
                    else -> AsyncImage(
                        model = user.coverUrl,
                        contentDescription = "Фон профиля",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(Color.Transparent, Bg.copy(alpha = 0.94f)))
                    )
                )
            }
        }

        item {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Bg)
                    .padding(horizontal = 16.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    ProfileAvatar(user, 92)
                    Spacer(Modifier.weight(1f))
                    if (own) {
                        OutlinedButton(onClick = { editOpen = true }) {
                            Icon(Icons.Default.Edit, null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("Редактировать")
                        }
                    } else {
                        Button(
                            onClick = { vm.toggleFollow(user.username) },
                            colors = ButtonDefaults.buttonColors(containerColor = Accent)
                        ) {
                            Text(if (user.followed) "Отписаться" else "Подписаться")
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                NameBadges(user)
                Text("@${user.username}", color = TextMuted, fontSize = 12.sp)

                Row(
                    Modifier.fillMaxWidth().padding(vertical = 18.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Stat(user.following, "Подписки")
                    Stat(user.followers, "Подписчики")
                    Stat(user.likes, "Лайки")
                }

                if (!user.bio.isNullOrBlank()) {
                    Text(user.bio, fontSize = 13.sp, lineHeight = 18.sp)
                    Spacer(Modifier.height(10.dp))
                }

                if (!user.musicUrl.isNullOrBlank()) {
                    AudioRow(user.musicTitle ?: "Музыка профиля", user.musicUrl)
                    Spacer(Modifier.height(10.dp))
                }

                if (own) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { supportOpen = true }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Favorite, null, tint = Color(0xFF27C97B), modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("Поддержать")
                        }
                        if (!user.verified) {
                            OutlinedButton(
                                onClick = { vm.requestVerification("") },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Verified, null, modifier = Modifier.size(17.dp))
                                Spacer(Modifier.width(5.dp))
                                Text("Верификация")
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { vm.logout() }) { Text("Выйти", color = TextMuted) }
                }

                Spacer(Modifier.height(12.dp))
                Text("Публикации", fontSize = 17.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(8.dp))
            }
        }

        val rows = profile.posts.chunked(3)
        items(rows.size) { rowIndex ->
            val row = rows[rowIndex]
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                row.forEach { post ->
                    ProfilePostTile(post, Modifier.weight(1f)) { onPost(post.id) }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }

        item { Spacer(Modifier.height(90.dp)) }
    }
}

@Composable
private fun ProfilePostTile(post: PostDto, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .aspectRatio(0.82f)
            .background(Surface2)
            .clickable(onClick = onClick)
    ) {
        when {
            !post.imageUrl.isNullOrBlank() -> AsyncImage(
                model = post.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            !post.videoUrl.isNullOrBlank() -> {
                Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(38.dp))
                }
            }
            else -> Text(
                post.body,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
                fontSize = 11.sp,
                modifier = Modifier.padding(9.dp).align(Alignment.Center)
            )
        }
        Text(
            "♥ ${post.likes}",
            fontSize = 9.sp,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(6.dp)
                .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(6.dp))
                .padding(horizontal = 5.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun AudioRow(title: String, url: String) {
    var playing by remember { mutableStateOf(false) }
    var prepared by remember { mutableStateOf(false) }
    val player = remember(url) { MediaPlayer() }
    DisposableEffect(url) {
        onDispose { try { player.release() } catch (_: Exception) {} }
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = Surface2),
        shape = RoundedCornerShape(13.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable {
                    try {
                        if (playing) {
                            player.pause()
                            playing = false
                        } else if (prepared) {
                            player.start()
                            playing = true
                        } else {
                            player.reset()
                            player.setDataSource(url)
                            player.setOnPreparedListener {
                                prepared = true
                                it.start()
                                playing = true
                            }
                            player.setOnCompletionListener { playing = false }
                            player.prepareAsync()
                        }
                    } catch (_: Exception) {}
                }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.MusicNote, null, tint = AccentSoft)
            Spacer(Modifier.width(8.dp))
            Text(title, modifier = Modifier.weight(1f), fontSize = 12.sp)
            Text(if (playing) "Пауза" else "Слушать", color = AccentSoft, fontSize = 11.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditProfileSheet(vm: MainViewModel, user: UserDto, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(user.displayName ?: user.username) }
    var bio by remember { mutableStateOf(user.bio.orEmpty()) }
    var theme by remember { mutableStateOf(user.theme) }
    var musicTitle by remember { mutableStateOf(user.musicTitle.orEmpty()) }
    var avatar by remember { mutableStateOf<Uri?>(null) }
    var cover by remember { mutableStateOf<Uri?>(null) }
    var music by remember { mutableStateOf<Uri?>(null) }

    val avatarPick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { avatar = it }
    val coverPick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { cover = it }
    val musicPick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { music = it }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Surface) {
        LazyColumn(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            item { Text("Редактирование профиля", fontSize = 23.sp, fontWeight = FontWeight.Black) }
            item { OutlinedTextField(name, { name = it.take(40) }, label = { Text("Имя") }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(bio, { bio = it.take(180) }, label = { Text("О себе") }, minLines = 3, modifier = Modifier.fillMaxWidth()) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    OutlinedButton({ avatarPick.launch("image/*") }, Modifier.weight(1f)) { Text("Аватар") }
                    OutlinedButton({ coverPick.launch("*/*") }, Modifier.weight(1f)) { Text("Фон") }
                    OutlinedButton({ musicPick.launch("audio/*") }, Modifier.weight(1f)) { Text("Музыка") }
                }
            }
            item { OutlinedTextField(musicTitle, { musicTitle = it.take(80) }, label = { Text("Название музыки") }, modifier = Modifier.fillMaxWidth()) }
            item {
                Text("Тема", color = TextMuted, fontSize = 11.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf("dark" to "Тёмная", "black" to "Чёрная", "violet" to "Фиолетовая", "red" to "Красная").forEach { item ->
                        OutlinedButton(onClick = { theme = item.first }) {
                            Text(if (theme == item.first) "✓ ${item.second}" else item.second)
                        }
                    }
                }
            }
            item {
                Button(
                    enabled = !vm.profileSaving,
                    onClick = {
                        vm.saveProfile(name, bio, theme, musicTitle, avatar, cover, music) { ok ->
                            if (ok) onDismiss()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (vm.profileSaving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                    else Text("Сохранить")
                }
            }
            item { Spacer(Modifier.height(30.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SupportSheet(vm: MainViewModel, onDismiss: () -> Unit) {
    var amountText by remember { mutableStateOf("50") }
    val uriHandler = LocalUriHandler.current
    val clipboard = LocalClipboardManager.current
    val cfg = vm.supportConfig

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Surface) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Поддержать проект", fontSize = 23.sp, fontWeight = FontWeight.Black)
            Text("Любая сумма от 5 ₽ помогает оплачивать сервер и развитие.", color = TextMuted, fontSize = 12.sp)
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it.filter { ch -> ch.isDigit() || ch == '.' }.take(8) },
                label = { Text("Сумма, ₽") },
                modifier = Modifier.fillMaxWidth()
            )
            val amount = amountText.toDoubleOrNull() ?: 0.0
            Button(
                enabled = amount >= 5 && cfg?.lolzEnabled == true,
                onClick = { vm.supportLolz(amount) { uriHandler.openUri(it) } },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF27C97B)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Star, null)
                Spacer(Modifier.width(6.dp))
                Text("Поддержать через LOLZ")
            }
            Text("Подтверждённая поддержка через LOLZ даёт зелёный значок в профиле.", color = TextMuted, fontSize = 10.sp)

            OutlinedButton(
                enabled = amount >= 5 && cfg?.tonEnabled == true,
                onClick = {
                    vm.supportTon(amount) { wallet ->
                        clipboard.setText(AnnotatedString(wallet))
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Скопировать TON-кошелёк") }

            if (cfg?.lolzEnabled != true || cfg.tonEnabled != true) {
                Text("Недоступные способы появятся после настройки на сервере.", color = TextMuted, fontSize = 10.sp)
            }
            Spacer(Modifier.height(25.dp))
        }
    }
}

@Composable
fun ConnectionEmpty(text: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onRetry) { Text("Повторить") }
        }
    }
}
