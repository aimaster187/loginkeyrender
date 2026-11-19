package com.example.data

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url
import java.util.concurrent.TimeUnit

interface RenderAdminApi {

    @GET("api/gate/config")
    suspend fun getGateConfig(
        @Query("pkg") packageName: String,
        @Query("vc") versionCode: Long
    ): Response<GateConfig>

    @GET
    suspend fun getRawConfigFromUrl(@Url fullUrl: String): Response<GateConfig>

    @POST("api/gate/verify")
    suspend fun verifyLicense(@Body request: LicenseVerifyRequest): Response<LicenseVerifyResponse>

    @GET("api/admin/dashboard")
    suspend fun getAdminDashboard(
        @Header("x-admin-secret") adminSecret: String
    ): Response<AdminDashboardResponse>

    @PUT("api/admin/config")
    suspend fun updateAdminConfig(
        @Header("x-admin-secret") adminSecret: String,
        @Body config: GateConfig
    ): Response<ApiActionResponse>

    @POST("api/admin/keys")
    suspend fun createLicenseKey(
        @Header("x-admin-secret") adminSecret: String,
        @Body request: CreateKeyRequest
    ): Response<ApiActionResponse>

    @POST("api/admin/keys/{key}/reset-device")
    suspend fun resetKeyDevice(
        @Header("x-admin-secret") adminSecret: String,
        @Path("key") key: String
    ): Response<ApiActionResponse>

    @POST("api/admin/keys/{key}/toggle-revoke")
    suspend fun toggleRevokeKey(
        @Header("x-admin-secret") adminSecret: String,
        @Path("key") key: String
    ): Response<ApiActionResponse>

    @DELETE("api/admin/keys/{key}")
    suspend fun deleteKey(
        @Header("x-admin-secret") adminSecret: String,
        @Path("key") key: String
    ): Response<ApiActionResponse>
}

object RenderApiFactory {
    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .build()

    fun create(baseUrl: String): RenderAdminApi? {
        val clean = baseUrl.trim().trimEnd('/')
        if (!clean.startsWith("https://") && !clean.startsWith("http://")) {
            return null
        }
        return Retrofit.Builder()
            .baseUrl("$clean/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(RenderAdminApi::class.java)
    }
}
