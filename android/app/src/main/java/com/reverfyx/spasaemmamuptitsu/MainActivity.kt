package com.reverfyx.spasaemmamuptitsu

import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SpasaemTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Bg) {
                    val vm: MainViewModel = viewModel()
                    SpasaemApp(vm)
                }
            }
        }
    }
}

enum class MainTab(val label: String, val icon: ImageVector) {
    HOME("Главная", Icons.Rounded.Home),
    STATUS("Статус", Icons.Rounded.MonitorHeart),
    FORUM("Форум", Icons.Rounded.Forum),
    MELLAI("@mellai", Icons.Rounded.AutoAwesome),
    PROFILE("Профиль", Icons.Rounded.Person)
}

private val alertCodes = setOf(
    "possibly_detained", "confirmed_detained", "storm"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpasaemApp(vm: MainViewModel) {
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    var openedPost by rememberSaveable { mutableStateOf<Int?>(null) }
    var openedProfile by rememberSaveable { mutableStateOf<String?>(null) }
    var composeParent by remember { mutableStateOf<Int?>(null) }
    var showComposer by remember { mutableStateOf(false) }

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val statusCode = vm.status?.code

    LaunchedEffect(vm.message) {
        vm.message?.let {
            snackbar.showSnackbar(it)
            vm.clearMessage()
        }
    }

    LaunchedEffect(statusCode) {
        if (statusCode in alertCodes) {
            val tone = ToneGenerator(AudioManager.STREAM_ALARM, 65)
            try {
                repeat(3) {
                    tone.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 520)
                    delay(720)
                }
            } finally {
                tone.release()
            }
        }
    }

    if (vm.authVisible) {
        AuthScreen(
            vm = vm,
            allowGuest = !vm.guestMode,
            onClose = { vm.closeAuth() }
        )
        return
    }

    if (showComposer) {
        ComposerSheet(
            vm = vm,
            parentId = composeParent,
            onDismiss = {
                showComposer = false
                composeParent = null
            }
        )
    }

    val infinite = rememberInfiniteTransition(label = "alarm")
    val alarmAlpha by infinite.animateFloat(
        initialValue = 0.02f,
        targetValue = if (statusCode in alertCodes) 0.22f else 0.02f,
        animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
        label = "alarmAlpha"
    )

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Bg,
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                when {
                    openedPost != null -> AppTopBar(
                        title = "Публикация",
                        back = {
                            openedPost = null
                            vm.clearPost()
                        },
                        refresh = { openedPost?.let(vm::loadPost) }
                    )
                    openedProfile != null -> AppTopBar(
                        title = "@${openedProfile}",
                        back = {
                            openedProfile = null
                            vm.clearProfile()
                        },
                        refresh = { openedProfile?.let(vm::loadProfile) }
                    )
                    else -> MainTopBar(
                        vm = vm,
                        title = MainTab.entries[tabIndex].label,
                        onRefresh = {
                            when (MainTab.entries[tabIndex]) {
                                MainTab.STATUS -> vm.loadStatus()
                                MainTab.PROFILE -> vm.me?.username?.let(vm::loadProfile)
                                else -> vm.refreshAll()
                            }
                        }
                    )
                }
            },
            bottomBar = {
                if (openedPost == null && openedProfile == null) {
                    NavigationBar(
                        containerColor = Color(0xFF0E0F12),
                        tonalElevation = 0.dp,
                        modifier = Modifier.navigationBarsPadding()
                    ) {
                        MainTab.entries.forEachIndexed { index, tab ->
                            NavigationBarItem(
                                selected = index == tabIndex,
                                onClick = { tabIndex = index },
                                icon = {
                                    Box(
                                        Modifier
                                            .size(42.dp)
                                            .background(
                                                if (index == tabIndex) Accent.copy(alpha = 0.16f) else Color.Transparent,
                                                CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            tab.icon,
                                            null,
                                            tint = if (index == tabIndex) Accent else TextMuted,
                                            modifier = Modifier.size(23.dp)
                                        )
                                    }
                                },
                                label = { Text(tab.label, fontSize = 9.sp) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Accent,
                                    selectedTextColor = TextPrimary,
                                    indicatorColor = Color.Transparent,
                                    unselectedIconColor = TextMuted,
                                    unselectedTextColor = TextMuted
                                )
                            )
                        }
                    }
                }
            },
            floatingActionButton = {
                if (
                    openedPost == null &&
                    openedProfile == null &&
                    MainTab.entries[tabIndex] == MainTab.FORUM
                ) {
                    ExtendedFloatingActionButton(
                        onClick = {
                            if (vm.requireAuth()) {
                                composeParent = null
                                showComposer = true
                            }
                        },
                        containerColor = Accent,
                        contentColor = Color.White,
                        icon = { Icon(Icons.Default.Add, null) },
                        text = { Text("Пост") }
                    )
                }
            }
        ) { padding ->
            Box(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
            ) {
                when {
                    openedPost != null -> {
                        LaunchedEffect(openedPost) {
                            openedPost?.let(vm::loadPost)
                        }
                        PostDetailScreen(
                            vm = vm,
                            onProfile = { openedProfile = it },
                            onReply = {
                                if (vm.requireAuth()) {
                                    composeParent = openedPost
                                    showComposer = true
                                }
                            }
                        )
                    }
                    openedProfile != null -> {
                        LaunchedEffect(openedProfile) {
                            openedProfile?.let(vm::loadProfile)
                        }
                        ProfileContent(
                            vm = vm,
                            onPost = { openedPost = it }
                        )
                    }
                    else -> {
                        Crossfade(
                            targetState = MainTab.entries[tabIndex],
                            label = "tabs"
                        ) { tab ->
                            when (tab) {
                                MainTab.HOME -> HomeScreen(
                                    vm = vm,
                                    onStatus = { tabIndex = MainTab.STATUS.ordinal },
                                    onForum = { tabIndex = MainTab.FORUM.ordinal },
                                    onPost = { openedPost = it },
                                    onProfile = { openedProfile = it }
                                )
                                MainTab.STATUS -> StatusScreen(vm)
                                MainTab.FORUM -> ForumScreen(
                                    vm = vm,
                                    onPost = { openedPost = it },
                                    onProfile = { openedProfile = it }
                                )
                                MainTab.MELLAI -> MellaiScreen(vm)
                                MainTab.PROFILE -> ProfileTab(
                                    vm = vm,
                                    onPost = { openedPost = it },
                                    onLogin = { vm.requireAuth() }
                                )
                            }
                        }
                    }
                }
            }
        }

        if (statusCode in alertCodes) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Red.copy(alpha = alarmAlpha))
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainTopBar(vm: MainViewModel, title: String, onRefresh: () -> Unit) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color(0xFF0E0F12),
            titleContentColor = TextPrimary
        ),
        title = {
            Column {
                Text(
                    "СПАСАЕМ МАМУ-ПТИЦУ",
                    fontWeight = FontWeight.Black,
                    fontSize = 13.sp,
                    letterSpacing = 0.6.sp
                )
                Text(title, color = TextMuted, fontSize = 11.sp)
            }
        },
        actions = {
            if (vm.status?.code in alertCodes) {
                Text(
                    "ТРЕВОГА",
                    color = Accent,
                    fontWeight = FontWeight.Black,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .background(Accent.copy(alpha = 0.12f), RoundedCornerShape(99.dp))
                        .padding(horizontal = 9.dp, vertical = 5.dp)
                )
                Spacer(Modifier.width(4.dp))
            }
            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, "Обновить", tint = TextMuted)
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppTopBar(title: String, back: () -> Unit, refresh: () -> Unit) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0E0F12)),
        title = { Text(title, fontWeight = FontWeight.Bold) },
        navigationIcon = {
            IconButton(onClick = back) {
                Icon(Icons.Default.ArrowBack, "Назад")
            }
        },
        actions = {
            IconButton(onClick = refresh) {
                Icon(Icons.Default.Refresh, "Обновить")
            }
        }
    )
}

@Composable
private fun AuthScreen(vm: MainViewModel, allowGuest: Boolean, onClose: () -> Unit) {
    var register by rememberSaveable { mutableStateOf(false) }
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var captchaText by rememberSaveable { mutableStateOf("") }

    val bitmap = remember(vm.captcha?.image) {
        vm.captcha?.image?.let(::decodeDataImage)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF11121A), Bg, Color(0xFF13090D))
                )
            )
            .statusBarsPadding()
            .imePadding()
            .padding(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(48.dp)
                        .background(Accent, RoundedCornerShape(15.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("М", fontWeight = FontWeight.Black, fontSize = 24.sp)
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        "Спасаем маму-птицу",
                        fontWeight = FontWeight.Black,
                        fontSize = 24.sp
                    )
                    Text(
                        "Подключение к серверу",
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                }
                Spacer(Modifier.weight(1f))
                if (!allowGuest) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, "Закрыть")
                    }
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = Surface),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AuthTab(
                            text = "Вход",
                            selected = !register,
                            modifier = Modifier.weight(1f),
                            onClick = { register = false }
                        )
                        AuthTab(
                            text = "Регистрация",
                            selected = register,
                            modifier = Modifier.weight(1f),
                            onClick = { register = true }
                        )
                    }

                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it.take(24) },
                        label = { Text("Юзернейм") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Пароль") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("Самодельная CAPTCHA", color = TextMuted, fontSize = 12.sp)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(86.dp)
                            .clickable { vm.refreshCaptcha() },
                        colors = CardDefaults.cardColors(containerColor = Surface2),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap,
                                    contentDescription = "CAPTCHA",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp)
                                )
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Проверка не загрузилась", color = TextMuted, fontSize = 11.sp)
                                    TextButton(onClick = { vm.refreshCaptcha() }) {
                                        Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(5.dp))
                                        Text("Повторить")
                                    }
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = captchaText,
                        onValueChange = { captchaText = it.uppercase().take(5) },
                        label = { Text("Символы с картинки") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                vm.authenticate(username, password, captchaText, register)
                            }
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        enabled = !vm.authBusy &&
                            username.length >= 3 &&
                            password.length >= 8 &&
                            captchaText.length == 5,
                        onClick = {
                            vm.authenticate(username, password, captchaText, register)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Accent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        if (vm.authBusy) {
                            CircularProgressIndicator(
                                Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                        } else {
                            Text(
                                if (register) "Создать аккаунт" else "Войти",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            if (allowGuest) {
                OutlinedButton(
                    onClick = { vm.continueAsGuest() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Продолжить без входа")
                }
            }

            Text(
                "Без входа можно читать ленту и статус. Для публикаций, подписок и @mellai нужен аккаунт.",
                color = TextMuted,
                fontSize = 11.sp,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
private fun AuthTab(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val color by animateColorAsState(
        if (selected) Accent else Surface2,
        label = "authTab"
    )
    Box(
        modifier
            .background(color, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (selected) Color.White else TextMuted,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun decodeDataImage(data: String) = try {
    val raw = data.substringAfter(",")
    val bytes = Base64.decode(raw, Base64.DEFAULT)
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
} catch (_: Exception) {
    null
}

@Composable
private fun HomeScreen(
    vm: MainViewModel,
    onStatus: () -> Unit,
    onForum: () -> Unit,
    onPost: (Int) -> Unit,
    onProfile: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        if (!vm.connectionOk && vm.status == null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Surface),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Text("Подключение к серверу", fontWeight = FontWeight.Black, fontSize = 18.sp)
                        Text("Сервер пока не ответил. Проверь соединение и попробуй ещё раз.", color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp))
                        OutlinedButton(onClick = { vm.retryConnection() }, modifier = Modifier.padding(top = 10.dp)) {
                            Icon(Icons.Default.Refresh, null)
                            Spacer(Modifier.width(5.dp))
                            Text("Повторить")
                        }
                    }
                }
            }
        }
        item {
            StatusHero(
                status = vm.status,
                onClick = onStatus,
                loading = vm.statusLoading
            )
        }

        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickAction(
                    "Статус",
                    "Публичный монитор",
                    Icons.Default.MonitorHeart,
                    Modifier.weight(1f),
                    onStatus
                )
                QuickAction(
                    "Форум",
                    "Обсуждения",
                    Icons.Default.Forum,
                    Modifier.weight(1f),
                    onForum
                )
            }
        }

        item {
            SectionTitle(
                title = "Свежие публикации",
                subtitle = "Последнее из сообщества",
                action = "Все",
                onAction = onForum
            )
        }

        items(vm.feed.take(5), key = { it.id }) { post ->
            PostCard(
                post = post,
                onOpen = { onPost(post.id) },
                onLike = { vm.toggleLike(post.id) },
                onProfile = { onProfile(post.username) },
                onRepost = { vm.toggleRepost(post.id) }
            )
        }

        item {
            SectionTitle(
                title = "Сигналы монитора",
                subtitle = "Публичные источники",
                action = "Статус",
                onAction = onStatus
            )
        }

        items(vm.events.take(5), key = { it.id }) { event ->
            EventRow(event)
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun StatusHero(status: StatusDto?, onClick: () -> Unit, loading: Boolean) {
    val danger = status?.code in alertCodes
    val accent = when {
        danger -> Accent
        status?.code == "ok" -> Good
        else -> Warn
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(24.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(270.dp)
        ) {
            AsyncImage(
                model = statusPhoto(status?.code),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                alpha = 0.62f
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0x22000000),
                                Color(0xAA090A0E),
                                Color(0xFA0A0A0C)
                            )
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .background(accent, CircleShape)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (danger) "ТРЕВОЖНЫЙ РЕЖИМ" else "LIVE STATUS",
                        color = accent,
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.weight(1f))
                    if (loading) {
                        CircularProgressIndicator(
                            Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
                Text(
                    status?.label ?: "Получаем статус…",
                    fontSize = 28.sp,
                    lineHeight = 31.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    status?.detail ?: "Подключение к серверу",
                    color = Color(0xFFD0D1D7),
                    lineHeight = 20.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                status?.let {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.LocationOn,
                            null,
                            tint = Color(0xFFB8BAC4),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(it.location, color = Color(0xFFB8BAC4), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickAction(
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(38.dp)
                    .background(AccentSoft.copy(alpha = 0.16f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = AccentSoft)
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(title, fontWeight = FontWeight.Bold)
                Text(subtitle, color = TextMuted, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun SectionTitle(
    title: String,
    subtitle: String,
    action: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Column {
            Text(title, fontWeight = FontWeight.Black, fontSize = 19.sp)
            Text(subtitle, color = TextMuted, fontSize = 11.sp)
        }
        Spacer(Modifier.weight(1f))
        if (action != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(action, color = AccentSoft)
            }
        }
    }
}

@Composable
private fun ForumScreen(
    vm: MainViewModel,
    onPost: (Int) -> Unit,
    onProfile: (String) -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Форум",
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        "Публикации сообщества · фото · ответы · просмотры",
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                }
            }
            items(vm.feed, key = { it.id }) { post ->
                PostCard(
                    post = post,
                    onOpen = { onPost(post.id) },
                    onLike = { vm.toggleLike(post.id) },
                    onProfile = { onProfile(post.username) }
                )
            }
            item { Spacer(Modifier.height(90.dp)) }
        }

        if (vm.feedLoading) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = Accent
            )
        }
    }
}

@Composable
private fun PostCard(
    post: PostDto,
    onOpen: () -> Unit,
    onLike: () -> Unit,
    onProfile: () -> Unit,
    onRepost: () -> Unit = {}
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Surface)
            .clickable(onClick = onOpen)
            .padding(horizontal = 14.dp, vertical = 13.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(AccentSoft.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center
            ) {
                if (!post.avatarUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = post.avatarUrl,
                        contentDescription = "Аватар",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(post.username.take(1).uppercase(), fontWeight = FontWeight.Black)
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        post.displayName ?: "@${post.username}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier.clickable(onClick = onProfile)
                    )
                    if (post.verified) {
                        Spacer(Modifier.width(4.dp))
                        Text("✓", color = Color(0xFF2E8CFF), fontWeight = FontWeight.Black)
                    }
                    if (post.sponsorBadge) {
                        Spacer(Modifier.width(4.dp))
                        Text("★", color = Color(0xFF27C97B), fontWeight = FontWeight.Black)
                    }
                }
                Text(
                    "@${post.username} · ${shortDate(post.createdAt)}",
                    color = TextMuted,
                    fontSize = 10.sp
                )
            }
        }

        if (post.body.isNotBlank()) {
            Text(
                post.body,
                fontSize = 15.sp,
                lineHeight = 21.sp,
                modifier = Modifier.padding(top = 11.dp)
            )
        }

        if (!post.videoUrl.isNullOrBlank()) {
            InlineVideo(
                url = post.videoUrl,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 11.dp)
                    .aspectRatio(9f / 12f)
                    .clip(RoundedCornerShape(16.dp))
            )
        } else {
            post.imageUrl?.let {
                AsyncImage(
                    model = it,
                    contentDescription = "Фото",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 11.dp)
                        .aspectRatio(1.15f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Surface2)
                )
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatAction(
                icon = if (post.liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                text = post.likes.toString(),
                tint = if (post.liked) Accent else TextMuted,
                onClick = onLike
            )
            Spacer(Modifier.width(18.dp))
            StatAction(
                icon = Icons.Default.ChatBubbleOutline,
                text = post.replies.toString(),
                onClick = onOpen
            )
            Spacer(Modifier.width(18.dp))
            StatAction(
                icon = Icons.Default.Repeat,
                text = post.reposts.toString(),
                tint = if (post.reposted) Good else TextMuted,
                onClick = onRepost
            )
            Spacer(Modifier.width(18.dp))
            StatAction(
                icon = Icons.Default.Visibility,
                text = post.views.toString(),
                onClick = onOpen
            )
        }
    }
    Divider(color = Line, thickness = 1.dp)
}

@Composable
private fun StatAction(
    icon: ImageVector,
    text: String,
    tint: Color = TextMuted,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(5.dp))
        Text(text, color = tint, fontSize = 11.sp)
    }
}

@Composable
private fun Avatar(username: String, size: Int) {
    val colors = listOf(
        Color(0xFF7057E5),
        Color(0xFFE34D79),
        Color(0xFF278B7A),
        Color(0xFFB36A34),
        Color(0xFF3976CF)
    )
    val color = colors[(username.hashCode() and Int.MAX_VALUE) % colors.size]
    Box(
        Modifier
            .size(size.dp)
            .background(color, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            username.take(1).uppercase(),
            color = Color.White,
            fontWeight = FontWeight.Black
        )
    }
}

@Composable
private fun StatusScreen(vm: MainViewModel) {
    val status = vm.status
    val uriHandler = LocalUriHandler.current
    val danger = status?.code in alertCodes

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            StatusHero(status, onClick = {}, loading = vm.statusLoading)
        }

        if (danger) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 5.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Accent.copy(alpha = 0.14f)
                    ),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Row(
                        Modifier.padding(15.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(Icons.Default.Shield, null, tint = Accent)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                "Тревожный режим",
                                color = Accent,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                "Есть тревожный публичный сигнал. Это не заменяет официального подтверждения; источник показывается отдельно.",
                                color = TextMuted,
                                fontSize = 12.sp,
                                lineHeight = 17.sp
                            )
                        }
                    }
                }
            }
        }

        status?.sourceUrl?.let { source ->
            item {
                OutlinedButton(
                    onClick = { uriHandler.openUri(source) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    Icon(Icons.Default.OpenInNew, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Открыть основной источник")
                }
            }
        }

        item {
            SectionTitle(
                "Публичная хронология",
                "Последние сообщения мониторинга"
            )
        }

        items(vm.events, key = { it.id }) { event ->
            EventRow(event)
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun EventRow(event: MonitorEventDto) {
    val uriHandler = LocalUriHandler.current
    val urgent = event.urgent != 0

    Row(
        Modifier
            .fillMaxWidth()
            .clickable { uriHandler.openUri(event.url) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            Modifier
                .padding(top = 5.dp)
                .size(9.dp)
                .background(if (urgent) Accent else Color(0xFF5B6170), CircleShape)
        )
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(
                event.title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                buildString {
                    append(event.source)
                    if (event.official != 0) append(" · официальный источник")
                },
                color = if (event.official != 0) Good else TextMuted,
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
            event.summary?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    color = TextMuted,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 5.dp)
                )
            }
        }
        Icon(
            Icons.Default.OpenInNew,
            null,
            tint = TextMuted,
            modifier = Modifier.size(16.dp)
        )
    }
    Divider(color = Line)
}

@Composable
private fun PostDetailScreen(
    vm: MainViewModel,
    onProfile: (String) -> Unit,
    onReply: () -> Unit
) {
    val detail = vm.postDetail

    if (vm.postLoading && detail == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Accent)
        }
        return
    }

    if (detail == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Публикация не загружена", color = TextMuted)
        }
        return
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            PostCard(
                post = detail.post,
                onOpen = {},
                onLike = { vm.toggleLike(detail.post.id) },
                onProfile = { onProfile(detail.post.username) },
                onRepost = { vm.toggleRepost(detail.post.id) }
            )
        }

        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Ответы",
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp
                )
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = onReply,
                    colors = ButtonDefaults.buttonColors(containerColor = Accent)
                ) {
                    Icon(Icons.Default.ChatBubbleOutline, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Ответить")
                }
            }
        }

        items(detail.replies, key = { it.id }) { reply ->
            PostCard(
                post = reply,
                onOpen = {},
                onLike = { vm.toggleLike(reply.id) },
                onProfile = { onProfile(reply.username) },
                onRepost = { vm.toggleRepost(reply.id) }
            )
        }

        if (detail.replies.isEmpty()) {
            item {
                Text(
                    "Ответов пока нет.",
                    color = TextMuted,
                    modifier = Modifier.padding(18.dp)
                )
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComposerSheet(
    vm: MainViewModel,
    parentId: Int?,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf("") }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    val context = LocalContext.current

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        imageUri = uri
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        tonalElevation = 0.dp
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                if (parentId == null) "Новая публикация" else "Ответить",
                fontSize = 24.sp,
                fontWeight = FontWeight.Black
            )

            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(500) },
                placeholder = {
                    Text(
                        if (parentId == null) "Что нового?" else "Напиши ответ…"
                    )
                },
                minLines = 4,
                maxLines = 8,
                modifier = Modifier.fillMaxWidth()
            )

            imageUri?.let { uri ->
                val mime = context.contentResolver.getType(uri).orEmpty()
                if (mime.startsWith("video/")) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Surface2),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Видео выбрано · будет сжато на сервере", modifier = Modifier.padding(16.dp), color = TextMuted)
                    }
                } else {
                    AsyncImage(
                        model = uri,
                        contentDescription = "Выбранное изображение",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(16.dp))
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { picker.launch("*/*") }) {
                    Icon(Icons.Default.Image, null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (imageUri == null) "Фото / видео" else "Сменить медиа")
                }
                Spacer(Modifier.weight(1f))
                Text("${text.length}/500", color = TextMuted, fontSize = 11.sp)
                Spacer(Modifier.width(10.dp))
                Button(
                    enabled = !vm.creatingPost && (text.isNotBlank() || imageUri != null),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                    onClick = {
                        vm.createPost(text, imageUri, parentId) { ok ->
                            if (ok) onDismiss()
                        }
                    }
                ) {
                    if (vm.creatingPost) {
                        CircularProgressIndicator(
                            Modifier.size(18.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.Send, null)
                        Spacer(Modifier.width(5.dp))
                        Text("Отправить")
                    }
                }
            }
        }
    }
}

@Composable
private fun MellaiScreen(vm: MainViewModel) {
    var input by rememberSaveable { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = AccentSoft.copy(alpha = 0.12f)
            ),
            shape = RoundedCornerShape(20.dp)
        ) {
            Row(
                Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(46.dp)
                        .background(
                            Brush.linearGradient(listOf(AccentSoft, Accent)),
                            RoundedCornerShape(15.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.AutoAwesome, null, tint = Color.White)
                }
                Spacer(Modifier.width(11.dp))
                Column {
                    Text("@mellai", fontWeight = FontWeight.Black, fontSize = 20.sp)
                    Text(
                        "ИИ-помощник о Mellstroy",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(vm.chat) { msg ->
                ChatBubble(msg)
            }
            if (vm.chatBusy) {
                item {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = AccentSoft
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("@mellai думает…", color = TextMuted, fontSize = 11.sp)
                    }
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .background(Color(0xFF0E0F12))
                .padding(10.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it.take(1000) },
                placeholder = { Text("Сообщение @mellai…") },
                maxLines = 5,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                enabled = input.isNotBlank() && !vm.chatBusy,
                onClick = {
                    val msg = input
                    input = ""
                    vm.sendMellai(msg)
                },
                modifier = Modifier
                    .size(52.dp)
                    .background(Accent, CircleShape)
            ) {
                Icon(Icons.Default.Send, null, tint = Color.White)
            }
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        horizontalArrangement = if (message.fromUser) {
            Arrangement.End
        } else {
            Arrangement.Start
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.84f)
                .background(
                    if (message.fromUser) Color(0xFF5D42C4) else Surface2,
                    RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 18.dp,
                        bottomStart = if (message.fromUser) 18.dp else 5.dp,
                        bottomEnd = if (message.fromUser) 5.dp else 18.dp
                    )
                )
                .padding(12.dp)
        ) {
            if (!message.fromUser) {
                Text(
                    "@mellai",
                    color = AccentSoft,
                    fontWeight = FontWeight.Black,
                    fontSize = 11.sp
                )
                Spacer(Modifier.height(3.dp))
            }
            Text(message.text, fontSize = 14.sp, lineHeight = 20.sp)
        }
    }
}

@Composable
private fun ProfileTab(
    vm: MainViewModel,
    onPost: (Int) -> Unit,
    onLogin: () -> Unit
) {
    if (!vm.authenticated) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(30.dp)
            ) {
                Icon(
                    Icons.Default.Person,
                    null,
                    tint = TextMuted,
                    modifier = Modifier.size(60.dp)
                )
                Spacer(Modifier.height(14.dp))
                Text("Профиль доступен после входа", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onLogin,
                    colors = ButtonDefaults.buttonColors(containerColor = Accent)
                ) {
                    Text("Войти")
                }
            }
        }
        return
    }

    val username = vm.me?.username
    LaunchedEffect(username) {
        username?.let(vm::loadProfile)
    }

    ProfileContent(vm, onPost, own = true)
}

@Composable
private fun ProfileContent(
    vm: MainViewModel,
    onPost: (Int) -> Unit,
    own: Boolean = false
) {
    TikTokProfileScreen(vm = vm, onPost = onPost, own = own)
}


private fun statusPhoto(code: String?): String = when (code) {
    "ok" -> "https://mellstroy.work.gd/static/img/archive-2.webp"
    "alert", "storm" -> "https://mellstroy.work.gd/static/img/status-alert.webp"
    "strange" -> "https://mellstroy.work.gd/static/img/status-strange.webp"
    "possibly_detained", "confirmed_detained" -> "https://mellstroy.work.gd/static/img/status-detained.webp"
    else -> "https://mellstroy.work.gd/static/img/status-checking.webp"
}

private fun shortDate(raw: String): String {
    if (raw.isBlank()) return ""
    return raw.replace("T", " ").take(16)
}
