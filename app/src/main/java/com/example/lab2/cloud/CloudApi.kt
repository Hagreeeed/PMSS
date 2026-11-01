package com.example.lab2.cloud

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

interface CloudApi {
    @POST("posts")
    suspend fun postSensor(@Body data: SensorData): Response<JsonObject>
}

object CloudClient {
    const val BASE_URL = "https://jsonplaceholder.typicode.com/"
    val gson = GsonBuilder().setPrettyPrinting().create()

    fun create(logger: (String) -> Unit = {}, debug: Boolean = false): CloudApi {
        val logging = HttpLoggingInterceptor { logger(it) }.apply {
            level = if (debug) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
        }
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false) // Retries belong to the persisted outbox cycle.
            .addInterceptor(logging)
            .build()
        return Retrofit.Builder().baseUrl(BASE_URL).client(client)
            .addConverterFactory(GsonConverterFactory.create(gson)).build().create(CloudApi::class.java)
    }
}
