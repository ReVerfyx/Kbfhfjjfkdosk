package com.reverfyx.spasaemmamuptitsu

import com.google.gson.annotations.SerializedName

data class CaptchaResponse(val id:String,val image:String,@SerializedName("expires_in") val expiresIn:Int)
data class AuthRequest(val username:String,val password:String,@SerializedName("captcha_id") val captchaId:String,val captcha:String)

data class UserDto(
    val id:Int,
    val username:String,
    @SerializedName("display_name") val displayName:String?=null,
    val bio:String?=null,
    @SerializedName("avatar_url") val avatarUrl:String?=null,
    @SerializedName("cover_url") val coverUrl:String?=null,
    @SerializedName("cover_type") val coverType:String="image",
    val verified:Boolean=false,
    @SerializedName("sponsor_badge") val sponsorBadge:Boolean=false,
    val theme:String="dark",
    @SerializedName("music_title") val musicTitle:String?=null,
    @SerializedName("music_url") val musicUrl:String?=null,
    val followers:Int=0,
    val following:Int=0,
    val likes:Int=0,
    @SerializedName("posts_count") val postsCount:Int=0,
    val followed:Boolean=false,
    @SerializedName("is_admin") val isAdmin:Boolean=false,
    @SerializedName("created_at") val createdAt:String?=null
)

data class AuthResponse(val token:String,@SerializedName("expires_at") val expiresAt:Long,val user:UserDto)
data class MeResponse(val user:UserDto)

data class PostDto(
    val id:Int,
    val username:String,
    @SerializedName("display_name") val displayName:String?=null,
    val body:String,
    @SerializedName("image_url") val imageUrl:String?=null,
    @SerializedName("video_url") val videoUrl:String?=null,
    @SerializedName("avatar_url") val avatarUrl:String?=null,
    val verified:Boolean=false,
    @SerializedName("sponsor_badge") val sponsorBadge:Boolean=false,
    @SerializedName("parent_id") val parentId:Int?=null,
    val views:Int=0,
    val likes:Int=0,
    val replies:Int=0,
    val reposts:Int=0,
    val liked:Boolean=false,
    val reposted:Boolean=false,
    @SerializedName("created_at") val createdAt:String=""
)

data class FeedResponse(val posts:List<PostDto>)
data class PostDetailResponse(val post:PostDto,val replies:List<PostDto>)
data class StatusDto(
    val code:String,
    val label:String,
    val detail:String,
    val location:String,
    @SerializedName("source_url") val sourceUrl:String?=null,
    @SerializedName("updated_at") val updatedAt:String
)
data class MonitorEventDto(
    val id:Int,
    val source:String,
    val title:String,
    val url:String,
    val summary:String?=null,
    @SerializedName("published_at") val publishedAt:String?=null,
    val official:Int=0,
    val urgent:Int=0,
    @SerializedName("created_at") val createdAt:String=""
)
data class StatusResponse(val status:StatusDto,val events:List<MonitorEventDto>)
data class LikeResponse(val liked:Boolean,val likes:Int)
data class RepostResponse(val reposted:Boolean,val reposts:Int)
data class CreatePostResponse(val ok:Boolean,@SerializedName("post_id") val postId:Int)
data class ProfileResponse(val user:UserDto,val posts:List<PostDto>)
data class ProfileUpdateResponse(val ok:Boolean,val user:UserDto)
data class FollowResponse(val followed:Boolean,val profile:UserDto)
data class VerificationResponse(val ok:Boolean,val status:String)
data class SupportConfig(@SerializedName("lolz_enabled") val lolzEnabled:Boolean=false,@SerializedName("ton_enabled") val tonEnabled:Boolean=false,@SerializedName("ton_wallet") val tonWallet:String="",@SerializedName("min_rub") val minRub:Int=5)
data class SupportRequest(val amount:Double)
data class SupportPaymentResponse(val ok:Boolean=false,@SerializedName("payment_id") val paymentId:Int?=null,@SerializedName("payment_url") val paymentUrl:String?=null,val wallet:String?=null,@SerializedName("amount_rub") val amountRub:Double?=null)
data class VerificationRequest(val message:String="")
data class MellaiHistoryDto(val role:String,val content:String)
data class MellaiRequest(val message:String,val history:List<MellaiHistoryDto> = emptyList())
data class MellaiResponse(val reply:String)
data class ApiMessage(val error:String?=null,val message:String?=null)
data class ChatMessage(val text:String,val fromUser:Boolean,val time:Long=System.currentTimeMillis())
