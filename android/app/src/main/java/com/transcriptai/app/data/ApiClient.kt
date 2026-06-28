package com.transcriptai.app.data

import com.transcriptai.app.BuildConfig
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.io.IOException
import java.util.concurrent.TimeUnit

object ApiClient {
    // Gateway timeouts that mean "origin was too slow", not "request was bad" — worth a quick retry.
    private val RETRIABLE_GATEWAY_CODES = setOf(502, 503, 504)
    private const val MAX_GATEWAY_ATTEMPTS = 4

    /** Only side-effect-free calls are auto-retried so a 504 can never create a duplicate record. */
    private fun isSafelyRetriable(method: String, path: String): Boolean {
        if (method.equals("GET", ignoreCase = true)) return true
        if (method.equals("POST", ignoreCase = true)) return path.endsWith("/media/presign")
        return false
    }

    private fun backoffMillis(attempt: Int): Long = minOf(4_000L, 600L * attempt)

    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        isLenient = true
        coerceInputValues = true
    }

    fun okHttp(tokenStore: TokenStore): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
        }
        return OkHttpClient.Builder()
            .retryOnConnectionFailure(true)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val original = chain.request()
                val retriable = isSafelyRetriable(original.method, original.url.encodedPath)
                val maxAttempts = if (retriable) MAX_GATEWAY_ATTEMPTS else 1
                var attempt = 0
                var lastError: IOException? = null
                while (attempt < maxAttempts) {
                    attempt++
                    try {
                        val response = chain.proceed(original)
                        if (retriable && response.code in RETRIABLE_GATEWAY_CODES && attempt < maxAttempts) {
                            response.close()
                            runCatching { Thread.sleep(backoffMillis(attempt)) }
                            continue
                        }
                        return@addInterceptor response
                    } catch (e: IOException) {
                        lastError = e
                        if (!retriable || attempt >= maxAttempts) throw e
                        runCatching { Thread.sleep(backoffMillis(attempt)) }
                    }
                }
                throw lastError ?: IOException("Request failed after $maxAttempts attempts")
            }
            .addInterceptor { chain ->
                val token = tokenStore.getToken()
                val request = if (token.isNullOrBlank()) {
                    chain.request()
                } else {
                    chain.request().newBuilder().header("Authorization", "Bearer $token").build()
                }
                chain.proceed(request)
            }
            .addInterceptor(logging)
            .build()
    }

    fun create(tokenStore: TokenStore): TranscriptApi {
        return Retrofit.Builder()
            .baseUrl(BuildConfig.DEFAULT_API_BASE_URL)
            .client(okHttp(tokenStore))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(TranscriptApi::class.java)
    }
}
