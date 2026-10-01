package com.reverfyx.spasaemmamuptitsu

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val session = SessionStore(app)
    private val client = ApiClient(app, session)
    private val api = client.service

    var authenticated by mutableStateOf(session.token != null)
        private set
    var guestMode by mutableStateOf(false)
        private set
    var authVisible by mutableStateOf(session.token == null)
        private set

    var connectionOk by mutableStateOf(false)
        private set
    var connectionBusy by mutableStateOf(false)
        private set

    var captcha by mutableStateOf<CaptchaResponse?>(null)
        private set
    var authBusy by mutableStateOf(false)
        private set

    var feed by mutableStateOf<List<PostDto>>(emptyList())
        private set
    var feedLoading by mutableStateOf(false)
        private set

    var status by mutableStateOf<StatusDto?>(null)
        private set
    var events by mutableStateOf<List<MonitorEventDto>>(emptyList())
        private set
    var statusLoading by mutableStateOf(false)
        private set

    var me by mutableStateOf<UserDto?>(null)
        private set

    var postDetail by mutableStateOf<PostDetailResponse?>(null)
        private set
    var postLoading by mutableStateOf(false)
        private set

    var profile by mutableStateOf<ProfileResponse?>(null)
        private set
    var profileLoading by mutableStateOf(false)
        private set

    var supportConfig by mutableStateOf<SupportConfig?>(null)
        private set

    var chat by mutableStateOf(
        listOf(
            ChatMessage(
                "Привет. Я @mellai. Можешь спросить про Mellstroy, статус, стримы или последние публичные события.",
                false
            )
        )
    )
        private set
    var chatBusy by mutableStateOf(false)
        private set

    var creatingPost by mutableStateOf(false)
        private set
    var profileSaving by mutableStateOf(false)
        private set

    var message by mutableStateOf<String?>(null)
        private set

    init {
        refreshAll()
        refreshCaptcha()
        loadSupport()
        if (authenticated) loadMe()
    }

    fun clearMessage() { message = null }

    fun continueAsGuest() {
        guestMode = true
        authVisible = false
    }

    fun requireAuth(): Boolean {
        if (authenticated) return true
        authVisible = true
        refreshCaptcha()
        return false
    }

    fun closeAuth() {
        if (authenticated || guestMode) authVisible = false
    }

    fun retryConnection() {
        refreshAll()
        refreshCaptcha()
        loadSupport()
    }

    fun refreshCaptcha() {
        viewModelScope.launch {
            try {
                captcha = api.captcha()
                connectionOk = true
            } catch (_: Exception) {
                captcha = null
                connectionOk = false
            }
        }
    }

    fun authenticate(username: String, password: String, captchaText: String, register: Boolean) {
        val c = captcha ?: run {
            message = "Сначала обнови проверку."
            refreshCaptcha()
            return
        }
        authBusy = true
        viewModelScope.launch {
            try {
                val body = AuthRequest(username.trim(), password, c.id, captchaText.trim())
                val response = if (register) api.register(body) else api.login(body)
                if (response.isSuccessful && response.body() != null) {
                    val result = response.body()!!
                    session.token = result.token
                    session.username = result.user.username
                    authenticated = true
                    guestMode = false
                    authVisible = false
                    me = result.user
                    connectionOk = true
                    message = if (register) "Аккаунт создан" else "Вход выполнен"
                    refreshAll()
                    loadMe()
                } else {
                    message = client.errorMessage(response)
                    refreshCaptcha()
                }
            } catch (_: Exception) {
                connectionOk = false
                message = "Нет соединения с сервером. Попробуй ещё раз."
                refreshCaptcha()
            } finally {
                authBusy = false
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            try { if (authenticated) api.logout() } catch (_: Exception) {}
            session.clear()
            authenticated = false
            guestMode = false
            me = null
            profile = null
            authVisible = true
            refreshCaptcha()
        }
    }

    fun refreshAll() {
        connectionBusy = true
        loadFeed()
        loadStatus()
    }

    fun loadFeed() {
        feedLoading = true
        viewModelScope.launch {
            try {
                feed = api.feed().posts
                connectionOk = true
            } catch (_: Exception) {
                connectionOk = false
            } finally {
                feedLoading = false
                connectionBusy = statusLoading
            }
        }
    }

    fun loadStatus() {
        statusLoading = true
        viewModelScope.launch {
            try {
                val result = api.status()
                status = result.status
                events = result.events
                connectionOk = true
            } catch (_: Exception) {
                connectionOk = false
            } finally {
                statusLoading = false
                connectionBusy = feedLoading
            }
        }
    }

    fun loadMe() {
        if (!authenticated) return
        viewModelScope.launch {
            try {
                val response = api.me()
                if (response.isSuccessful) {
                    me = response.body()?.user
                    connectionOk = true
                } else if (response.code() == 401) {
                    session.clear()
                    authenticated = false
                    authVisible = true
                }
            } catch (_: Exception) {
                connectionOk = false
            }
        }
    }

    fun loadPost(id: Int) {
        postLoading = true
        postDetail = null
        viewModelScope.launch {
            try {
                val response = api.post(id)
                if (response.isSuccessful) postDetail = response.body()
                else message = client.errorMessage(response)
            } catch (_: Exception) {
                message = "Не удалось открыть публикацию"
            } finally {
                postLoading = false
            }
        }
    }

    fun clearPost() { postDetail = null }

    fun toggleLike(postId: Int) {
        if (!requireAuth()) return
        viewModelScope.launch {
            try {
                val response = api.like(postId)
                if (!response.isSuccessful) {
                    message = client.errorMessage(response)
                    return@launch
                }
                val like = response.body() ?: return@launch
                updatePostEverywhere(postId) { it.copy(liked = like.liked, likes = like.likes) }
            } catch (_: Exception) {
                message = "Не удалось изменить лайк"
            }
        }
    }

    fun toggleRepost(postId: Int) {
        if (!requireAuth()) return
        viewModelScope.launch {
            try {
                val response = api.repost(postId)
                if (!response.isSuccessful) {
                    message = client.errorMessage(response)
                    return@launch
                }
                val rp = response.body() ?: return@launch
                updatePostEverywhere(postId) { it.copy(reposted = rp.reposted, reposts = rp.reposts) }
            } catch (_: Exception) {
                message = "Не удалось сделать репост"
            }
        }
    }

    private fun updatePostEverywhere(id: Int, change: (PostDto) -> PostDto) {
        feed = feed.map { if (it.id == id) change(it) else it }
        postDetail = postDetail?.let { d ->
            d.copy(
                post = if (d.post.id == id) change(d.post) else d.post,
                replies = d.replies.map { if (it.id == id) change(it) else it }
            )
        }
        profile = profile?.copy(posts = profile!!.posts.map { if (it.id == id) change(it) else it })
    }

    fun createPost(body: String, mediaUri: Uri?, parentId: Int?, onDone: (Boolean) -> Unit) {
        if (!requireAuth()) {
            onDone(false)
            return
        }
        creatingPost = true
        viewModelScope.launch {
            try {
                val response = api.createPost(client.makePostParts(body.trim(), mediaUri, parentId))
                if (response.isSuccessful) {
                    message = if (parentId == null) "Опубликовано" else "Ответ отправлен"
                    loadFeed()
                    if (parentId != null) loadPost(parentId)
                    onDone(true)
                } else {
                    message = client.errorMessage(response)
                    onDone(false)
                }
            } catch (_: Exception) {
                message = "Не удалось отправить публикацию"
                onDone(false)
            } finally {
                creatingPost = false
            }
        }
    }

    fun loadProfile(username: String) {
        profileLoading = true
        profile = null
        viewModelScope.launch {
            try {
                val response = api.profile(username)
                if (response.isSuccessful) profile = response.body()
                else message = client.errorMessage(response)
            } catch (_: Exception) {
                message = "Не удалось загрузить профиль"
            } finally {
                profileLoading = false
            }
        }
    }

    fun clearProfile() { profile = null }

    fun toggleFollow(username: String) {
        if (!requireAuth()) return
        viewModelScope.launch {
            try {
                val response = api.follow(username)
                if (response.isSuccessful && response.body() != null) {
                    val updated = response.body()!!.profile
                    profile = profile?.copy(user = updated)
                } else message = client.errorMessage(response)
            } catch (_: Exception) {
                message = "Не удалось изменить подписку"
            }
        }
    }

    fun saveProfile(
        displayName: String,
        bio: String,
        theme: String,
        musicTitle: String,
        avatar: Uri?,
        cover: Uri?,
        music: Uri?,
        onDone: (Boolean) -> Unit
    ) {
        if (!requireAuth()) {
            onDone(false)
            return
        }
        profileSaving = true
        viewModelScope.launch {
            try {
                val parts = client.makeProfileParts(displayName, bio, theme, musicTitle, avatar, cover, music)
                val response = api.editProfile(parts)
                if (response.isSuccessful && response.body() != null) {
                    me = response.body()!!.user
                    profile = profile?.copy(user = response.body()!!.user)
                    message = "Профиль сохранён"
                    onDone(true)
                } else {
                    message = client.errorMessage(response)
                    onDone(false)
                }
            } catch (_: Exception) {
                message = "Не удалось сохранить профиль"
                onDone(false)
            } finally {
                profileSaving = false
            }
        }
    }

    fun requestVerification(messageText: String) {
        if (!requireAuth()) return
        viewModelScope.launch {
            try {
                val response = api.requestVerification(VerificationRequest(messageText))
                message = if (response.isSuccessful) "Заявка отправлена" else client.errorMessage(response)
            } catch (_: Exception) {
                message = "Не удалось отправить заявку"
            }
        }
    }

    fun loadSupport() {
        viewModelScope.launch {
            try {
                val response = api.supportConfig()
                if (response.isSuccessful) supportConfig = response.body()
            } catch (_: Exception) {}
        }
    }

    fun supportLolz(amount: Double, onUrl: (String) -> Unit) {
        if (!requireAuth()) return
        viewModelScope.launch {
            try {
                val response = api.supportLolz(SupportRequest(amount))
                val url = response.body()?.paymentUrl
                if (response.isSuccessful && !url.isNullOrBlank()) onUrl(url)
                else message = client.errorMessage(response)
            } catch (_: Exception) {
                message = "Платёжный сервис временно недоступен"
            }
        }
    }

    fun supportTon(amount: Double, onWallet: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val response = api.supportTon(SupportRequest(amount))
                val wallet = response.body()?.wallet
                if (response.isSuccessful && !wallet.isNullOrBlank()) onWallet(wallet)
                else message = client.errorMessage(response)
            } catch (_: Exception) {
                message = "TON-поддержка временно недоступна"
            }
        }
    }

    fun sendMellai(text: String) {
        if (!requireAuth()) return
        val clean = text.trim()
        if (clean.isEmpty() || chatBusy) return
        chat = chat + ChatMessage(clean, true)
        chatBusy = true
        viewModelScope.launch {
            try {
                val response = api.mellai(MellaiRequest(clean))
                chat = chat + ChatMessage(
                    if (response.isSuccessful && response.body() != null) response.body()!!.reply
                    else client.errorMessage(response),
                    false
                )
            } catch (_: Exception) {
                chat = chat + ChatMessage("Не удалось связаться с @mellai. Попробуй позже.", false)
            } finally {
                chatBusy = false
            }
        }
    }
}
