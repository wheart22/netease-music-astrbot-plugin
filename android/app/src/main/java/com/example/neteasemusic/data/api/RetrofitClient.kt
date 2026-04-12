package com.example.neteasemusic.data.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Creates and caches a [NeteaseApiService] for the given [baseUrl].
 *
 * A new instance is returned whenever [baseUrl] changes so that the user can
 * reconfigure the API address from the Settings screen at runtime.
 */
object RetrofitClient {

    @Volatile
    private var currentBaseUrl: String = ""

    @Volatile
    private var service: NeteaseApiService? = null

    fun getService(baseUrl: String): NeteaseApiService {
        val normalizedUrl = baseUrl.trimEnd('/') + '/'
        if (service != null && normalizedUrl == currentBaseUrl) {
            return service!!
        }
        synchronized(this) {
            if (service != null && normalizedUrl == currentBaseUrl) {
                return service!!
            }
            service = buildService(normalizedUrl)
            currentBaseUrl = normalizedUrl
            return service!!
        }
    }

    private fun buildService(baseUrl: String): NeteaseApiService {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(NeteaseApiService::class.java)
    }
}
