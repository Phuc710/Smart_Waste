package com.example.app_smart_waste.core.network

import android.content.Context
import com.example.app_smart_waste.core.storage.AppConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class RetrofitClient private constructor(context: Context) {

    private val apiService: ApiService
    private val cookieJar: PersistentCookieJar = PersistentCookieJar(context)

    init {
        val logging = HttpLoggingInterceptor().apply {
            level = if (com.example.app_smart_waste.BuildConfig.DEBUG && AppConfig.ENABLE_HTTP_LOGGING) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        val okHttpClient = OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .addInterceptor(AuthInterceptor(context))
            .addInterceptor(logging)
            .connectTimeout(AppConfig.NETWORK_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(AppConfig.NETWORK_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(AppConfig.NETWORK_WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        val baseUrl = AppConfig.getBaseUrl(context)

        val gson = com.google.gson.GsonBuilder()
            .setLenient()
            .registerTypeAdapter(String::class.java, com.google.gson.JsonDeserializer<String> { json, _, _ ->
                if (json.isJsonPrimitive) json.asString else json.toString()
            })
            .create()

        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()

        apiService = retrofit.create(ApiService::class.java)
    }

    fun getApi(): ApiService = apiService
    fun getCookieJar(): PersistentCookieJar = cookieJar

    companion object {
        @Volatile
        private var INSTANCE: RetrofitClient? = null

        fun getInstance(context: Context): RetrofitClient {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: RetrofitClient(context.applicationContext).also { INSTANCE = it }
            }
        }

        fun resetClient() {
            INSTANCE = null
        }
    }
}
