package com.reverfyx.spasaemmamuptitsu

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

interface ApiService {
    @GET("api/v1/captcha")
    suspend fun captcha(): CaptchaResponse

    @POST("api/v1/auth/login")
    suspend fun login(@Body body: AuthRequest): Response<AuthResponse>

    @POST("api/v1/auth/register")
    suspend fun register(@Body body: AuthRequest): Response<AuthResponse>

    @POST("api/v1/auth/logout")
    suspend fun logout(): Response<Map<String, Any>>

    @GET("api/v1/me")
    suspend fun me(): Response<MeResponse>

    @GET("api/v1/status")
    suspend fun status(): StatusResponse

    @GET("api/v1/feed")
    suspend fun feed(): FeedResponse

    @GET("api/v1/posts/{id}")
    suspend fun post(@Path("id") id: Int): Response<PostDetailResponse>

    @POST("api/v1/posts/{id}/like")
    suspend fun like(@Path("id") id: Int): Response<LikeResponse>

    @Multipart
    @POST("api/v1/posts")
    suspend fun createPost(@Part parts: List<MultipartBody.Part>): Response<CreatePostResponse>

    @GET("api/v1/profile/{username}")
    suspend fun profile(@Path("username") username: String): Response<ProfileResponse>

    @POST("api/v1/mellai")
    suspend fun mellai(@Body body: MellaiRequest): Response<MellaiResponse>
}

class ApiClient(context: Context, private val session: SessionStore) {
    companion object {
        const val BASE_URL = "https://mellstroy.work.gd/"
    }

    private val gson = Gson()

    private val authInterceptor = Interceptor { chain ->
        val request = chain.request().newBuilder().apply {
            session.token?.let { addHeader("Authorization", "Bearer $it") }
            addHeader("Accept", "application/json")
            addHeader("User-Agent", "SpasaemMamuPtitsuNative/3.0")
        }.build()
        chain.proceed(request)
    }

    private val http = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
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
            "Ошибка ${response.code()}"
        }
    }

    fun makePostParts(
        context: Context,
        body: String,
        imageUri: Uri?,
        parentId: Int?
    ): List<MultipartBody.Part> {
        val parts = mutableListOf<MultipartBody.Part>()
        parts += MultipartBody.Part.createFormData("body", body)
        parentId?.let {
            parts += MultipartBody.Part.createFormData("parent_id", it.toString())
        }

        if (imageUri != null) {
            val resolver = context.contentResolver
            val mime = resolver.getType(imageUri) ?: "image/jpeg"
            val bytes = resolver.openInputStream(imageUri)?.use { it.readBytes() }
                ?: throw IllegalArgumentException("Не удалось прочитать изображение")
            val requestBody = bytes.toRequestBody(mime.toMediaTypeOrNull())
            parts += MultipartBody.Part.createFormData("image", "upload.jpg", requestBody)
        }
        return parts
    }
}
