package com.reverfyx.spasaemmamuptitsu

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import okhttp3.Dns
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okio.BufferedSink
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import java.net.InetAddress
import java.util.concurrent.TimeUnit

interface ApiService {
    @GET("api/v1/captcha") suspend fun captcha(): CaptchaResponse
    @POST("api/v1/auth/login") suspend fun login(@Body body: AuthRequest): Response<AuthResponse>
    @POST("api/v1/auth/register") suspend fun register(@Body body: AuthRequest): Response<AuthResponse>
    @POST("api/v1/auth/logout") suspend fun logout(): Response<Map<String, Any>>
    @GET("api/v1/me") suspend fun me(): Response<MeResponse>

    @GET("api/v1/status") suspend fun status(): StatusResponse
    @GET("api/v1/feed") suspend fun feed(): FeedResponse
    @GET("api/v1/posts/{id}") suspend fun post(@Path("id") id: Int): Response<PostDetailResponse>
    @POST("api/v1/posts/{id}/like") suspend fun like(@Path("id") id: Int): Response<LikeResponse>
    @POST("api/v1/posts/{id}/repost") suspend fun repost(@Path("id") id: Int): Response<RepostResponse>
    @Multipart @POST("api/v1/posts") suspend fun createPost(@Part parts: List<MultipartBody.Part>): Response<CreatePostResponse>

    @GET("api/v1/profile/{username}") suspend fun profile(@Path("username") username: String): Response<ProfileResponse>
    @POST("api/v1/profile/{username}/follow") suspend fun follow(@Path("username") username: String): Response<FollowResponse>
    @Multipart @POST("api/v1/profile/edit") suspend fun editProfile(@Part parts: List<MultipartBody.Part>): Response<ProfileUpdateResponse>
    @POST("api/v1/verification/request") suspend fun requestVerification(@Body body: VerificationRequest): Response<VerificationResponse>

    @GET("api/v1/support") suspend fun supportConfig(): Response<SupportConfig>
    @POST("api/v1/support/lolz") suspend fun supportLolz(@Body body: SupportRequest): Response<SupportPaymentResponse>
    @POST("api/v1/support/ton") suspend fun supportTon(@Body body: SupportRequest): Response<SupportPaymentResponse>

    @POST("api/v1/mellai") suspend fun mellai(@Body body: MellaiRequest): Response<MellaiResponse>
}

private class UriRequestBody(
    private val context: Context,
    private val uri: Uri,
    private val mime: String
) : RequestBody() {
    override fun contentType(): MediaType? = MediaType.parse(mime)

    override fun contentLength(): Long {
        return try {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
        } catch (_: Exception) {
            -1L
        }
    }

    override fun writeTo(sink: BufferedSink) {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                sink.write(buffer, 0, read)
            }
        } ?: throw IllegalArgumentException("Не удалось прочитать файл")
    }
}

class ApiClient(private val context: Context, private val session: SessionStore) {
    companion object {
        private const val TLS_HOST = "mellstroy.work.gd"
        private const val SERVER_IP = "2.26.85.86"
        private const val BASE_URL = "https://mellstroy.work.gd/"
    }

    private val gson = Gson()

    private val pinnedDns = Dns { hostname ->
        if (hostname.equals(TLS_HOST, ignoreCase = true)) {
            listOf(InetAddress.getByName(SERVER_IP))
        } else {
            Dns.SYSTEM.lookup(hostname)
        }
    }

    private val authInterceptor = Interceptor { chain ->
        val request = chain.request().newBuilder().apply {
            session.token?.let { addHeader("Authorization", "Bearer $it") }
            addHeader("Accept", "application/json")
            addHeader("User-Agent", "SpasaemNative/4.0")
        }.build()
        chain.proceed(request)
    }

    private val http = OkHttpClient.Builder()
        .dns(pinnedDns)
        .addInterceptor(authInterceptor)
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(70, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    val service: ApiService = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(http)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()
        .create(ApiService::class.java)

    fun errorMessage(response: Response<*>): String {
        val raw = response.errorBody()?.string().orEmpty()
        return try {
            gson.fromJson(raw, ApiMessage::class.java).message ?: "Ошибка ${response.code()}"
        } catch (_: Exception) {
            "Сервер временно недоступен"
        }
    }

    private fun textPart(name: String, value: String): MultipartBody.Part =
        MultipartBody.Part.createFormData(name, value)

    private fun filePart(name: String, uri: Uri): MultipartBody.Part {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        val ext = when {
            mime.startsWith("video/") -> ".mp4"
            mime.startsWith("audio/") -> ".m4a"
            else -> ".jpg"
        }
        val body = UriRequestBody(context, uri, mime)
        return MultipartBody.Part.createFormData(name, "upload$ext", body)
    }

    fun makePostParts(
        body: String,
        mediaUri: Uri?,
        parentId: Int?
    ): List<MultipartBody.Part> {
        val parts = mutableListOf(textPart("body", body))
        parentId?.let { parts += textPart("parent_id", it.toString()) }
        mediaUri?.let {
            val mime = context.contentResolver.getType(it).orEmpty()
            parts += filePart(if (mime.startsWith("video/")) "video" else "image", it)
        }
        return parts
    }

    fun makeProfileParts(
        displayName: String,
        bio: String,
        theme: String,
        musicTitle: String,
        avatar: Uri?,
        cover: Uri?,
        music: Uri?
    ): List<MultipartBody.Part> {
        val parts = mutableListOf(
            textPart("display_name", displayName),
            textPart("bio", bio),
            textPart("theme", theme),
            textPart("music_title", musicTitle)
        )
        avatar?.let { parts += filePart("avatar", it) }
        cover?.let { parts += filePart("cover", it) }
        music?.let { parts += filePart("music", it) }
        return parts
    }
}
