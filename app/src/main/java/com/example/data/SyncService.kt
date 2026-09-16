package com.example.data

import com.squareup.moshi.JsonClass
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class CloudDocument(
    val title: String,
    val content: String,
    val sourcePath: String?,
    val lastModified: Long
)

@JsonClass(generateAdapter = true)
data class SyncPayload(
    val deviceName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val documents: List<CloudDocument>
)

@JsonClass(generateAdapter = true)
data class SyncResponse(
    val success: Boolean,
    val message: String,
    val synchedCount: Int,
    val timestamp: Long
)

interface SyncApi {
    @POST("post") // Using httpbin.org/post to simulate a real, working REST backup and sync API securely
    suspend fun backupDocuments(
        @Body payload: SyncPayload
    ): retrofit2.Response<okhttp3.ResponseBody>
}

object RetrofitClient {
    private const val BASE_URL = "https://httpbin.org/"

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    val apiService: SyncApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(SyncApi::class.java)
    }
}
