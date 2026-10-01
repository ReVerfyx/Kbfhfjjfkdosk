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

    var chat by mutableStateOf(
        listOf(
            ChatMessage(
                "Привет. Я @mellai. Можешь спросить про Mellstroy, последние публичные сигналы, статус или стримы.",
                false
            )
        )
    )
        private set
    var chatBusy by mutableStateOf(false)
        private set

    var creatingPost by mutableStateOf(false)
        private set

    var message by mutableStateOf<String?>(null)
        private set

    init {
        refreshCaptcha()
        refreshAll()
        if (authenticated) loadMe()
    }

    fun clearMessage() {
        message = null
    }

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

    fun refreshCaptcha() {
        viewModelScope.launch {
            try {
                captcha = api.captcha()
            } catch (e: Exception) {
                message = "Не удалось загрузить CAPTCHA: ${e.message ?: "ошибка сети"}"
            }
        }
    }

    fun authenticate(
        username: String,
        password: String,
        captchaText: String,
        register: Boolean
    ) {
        val c = captcha ?: return
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
                    message = if (register) "Аккаунт создан" else "Вход выполнен"
                    refreshAll()
                } else {
                    message = client.errorMessage(response)
                    refreshCaptcha()
                }
            } catch (e: Exception) {
                message = "Ошибка сети: ${e.message ?: "неизвестно"}"
                refreshCaptcha()
            } finally {
                authBusy = false
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            try {
                if (authenticated) api.logout()
            } catch (_: Exception) {
            }
            session.clear()
            authenticated = false
            guestMode = false
            me = null
            authVisible = true
            refreshCaptcha()
        }
    }

    fun refreshAll() {
        loadFeed()
        loadStatus()
    }

    fun loadFeed() {
        feedLoading = true
        viewModelScope.launch {
            try {
                feed = api.feed().posts
            } catch (e: Exception) {
                message = "Не удалось обновить ленту"
            } finally {
                feedLoading = false
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
            } catch (e: Exception) {
                message = "Не удалось обновить статус"
            } finally {
                statusLoading = false
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
                } else if (response.code() == 401) {
                    session.clear()
                    authenticated = false
                    authVisible = true
                }
            } catch (_: Exception) {
            }
        }
    }

    fun loadPost(id: Int) {
        postLoading = true
        postDetail = null
        viewModelScope.launch {
            try {
                val response = api.post(id)
                if (response.isSuccessful) {
                    postDetail = response.body()
                } else {
                    message = client.errorMessage(response)
                }
            } catch (e: Exception) {
                message = "Не удалось открыть публикацию"
            } finally {
                postLoading = false
            }
        }
    }

    fun clearPost() {
        postDetail = null
    }

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
                feed = feed.map {
                    if (it.id == postId) it.copy(liked = like.liked, likes = like.likes) else it
                }
                postDetail = postDetail?.let { detail ->
                    detail.copy(
                        post = if (detail.post.id == postId) {
                            detail.post.copy(liked = like.liked, likes = like.likes)
                        } else detail.post,
                        replies = detail.replies.map {
                            if (it.id == postId) it.copy(liked = like.liked, likes = like.likes) else it
                        }
                    )
                }
                profile = profile?.copy(
                    posts = profile!!.posts.map {
                        if (it.id == postId) it.copy(liked = like.liked, likes = like.likes) else it
                    }
                )
            } catch (_: Exception) {
                message = "Не удалось изменить лайк"
            }
        }
    }

    fun createPost(
        body: String,
        imageUri: Uri?,
        parentId: Int?,
        onDone: (Boolean) -> Unit
    ) {
        if (!requireAuth()) {
            onDone(false)
            return
        }
        creatingPost = true
        viewModelScope.launch {
            try {
                val parts = client.makePostParts(getApplication<Application>(), body.trim(), imageUri, parentId)
                val response = api.createPost(parts)
                if (response.isSuccessful) {
                    message = if (parentId == null) "Опубликовано" else "Ответ отправлен"
                    loadFeed()
                    if (parentId != null) loadPost(parentId)
                    onDone(true)
                } else {
                    message = client.errorMessage(response)
                    onDone(false)
                }
            } catch (e: Exception) {
                message = "Не удалось отправить: ${e.message ?: "ошибка"}"
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
                if (response.isSuccessful) {
                    profile = response.body()
                } else {
                    message = client.errorMessage(response)
                }
            } catch (_: Exception) {
                message = "Не удалось загрузить профиль"
            } finally {
                profileLoading = false
            }
        }
    }

    fun clearProfile() {
        profile = null
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
                if (response.isSuccessful && response.body() != null) {
                    chat = chat + ChatMessage(response.body()!!.reply, false)
                } else {
                    chat = chat + ChatMessage(client.errorMessage(response), false)
                }
            } catch (_: Exception) {
                chat = chat + ChatMessage("Не удалось связаться с @mellai. Проверь соединение с сервером.", false)
            } finally {
                chatBusy = false
            }
        }
    }
}
