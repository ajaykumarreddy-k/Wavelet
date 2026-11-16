package com.music.bitchord.spotify.api

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.music.bitchord.data.DebugLog as Log
import com.music.bitchord.spotify.auth.SpotifyAuthManager
import com.music.bitchord.spotify.auth.SpotifyTokenStore
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object SpotifyApiClient {
    private const val TAG = "SpotifyApiClient"
    private const val BASE_URL = "https://api.spotify.com/"

    val gson: Gson = GsonBuilder()
        .setLenient()
        .create()

    private var apiInstance: SpotifyApi? = null

    fun getApi(context: Context): SpotifyApi {
        return apiInstance ?: synchronized(this) {
            apiInstance ?: createApi(context.applicationContext).also { apiInstance = it }
        }
    }

    private fun createApi(context: Context): SpotifyApi {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .addInterceptor(SpotifyAuthInterceptor(context))
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()

        return retrofit.create(SpotifyApi::class.java)
    }

    private class SpotifyAuthInterceptor(private val context: Context) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val originalRequest = chain.request()

            var token = runBlocking {
                SpotifyTokenStore.getValidAccessToken(context)
            }

            val authRequest = if (!token.isNullOrBlank()) {
                originalRequest.newBuilder()
                    .header("Authorization", "Bearer $token")
                    .build()
            } else {
                originalRequest
            }

            var response = chain.proceed(authRequest)

            // If 401 Unauthorized, attempt PKCE refresh only if we have a refresh token
            if (response.code == 401) {
                val hasRefreshToken = !SpotifyTokenStore.getRefreshToken(context).isNullOrBlank()
                if (hasRefreshToken) {
                    Log.w(TAG, "Spotify 401 received, attempting PKCE token refresh...")
                    response.close()

                    val refreshed = runBlocking {
                        SpotifyAuthManager.refreshToken(context)
                    }

                    if (refreshed) {
                        token = runBlocking { SpotifyTokenStore.getValidAccessToken(context) }
                        if (!token.isNullOrBlank()) {
                            val newRequest = originalRequest.newBuilder()
                                .header("Authorization", "Bearer $token")
                                .build()
                            return chain.proceed(newRequest)
                        }
                    }
                    return chain.proceed(originalRequest)
                }
            }

            // If 429 Too Many Requests, do not block the thread for 30+ seconds.
            // Cap sleep to max 500ms so we never freeze UI/networking.
            if (response.code == 429) {
                val retryAfterSec = response.header("Retry-After")?.toLongOrNull() ?: 1L
                Log.w(TAG, "Rate limited (429), Retry-After was ${retryAfterSec}s")
                // Return fast so callers can failover or handle without stalling
                return response
            }

            return response
        }
    }
}

