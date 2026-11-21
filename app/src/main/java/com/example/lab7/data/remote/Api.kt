package com.example.lab7.data.remote

import com.example.lab7.domain.RemoteCommand
import com.example.lab7.domain.RemoteCommandSource
import com.google.gson.JsonObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/** JSON, який отримує хмара. Окремий від Room-сутності, щоб схема БД не залежала від API. */
data class ReadingDto(
    val sampleId: String,
    val deviceId: String,
    val timestamp: Long,
    val type: String,
    val value: Float?,
    val rawValue: Float?,
    val state: String?,
    val confidence: Float?,
    val event: String?,
)

data class ReadingAck(val id: Long?, val sampleId: String?)

interface CloudApi {
    @POST("readings")
    suspend fun upload(@Body reading: ReadingDto): Response<ReadingAck>

    @GET("command")
    suspend fun command(): Response<JsonObject>
}

object ApiFactory {
    fun create(baseUrl: String, logger: (String) -> Unit): CloudApi {
        val logging = HttpLoggingInterceptor { logger(it) }.apply { level = HttpLoggingInterceptor.Level.BASIC }
        val client = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .callTimeout(8, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()
        return Retrofit.Builder().baseUrl(baseUrl).client(client)
            .addConverterFactory(GsonConverterFactory.create()).build()
            .create(CloudApi::class.java)
    }
}

/** Команда для лампи з хаба (ЛР4): {"id": 2, "completed": true}. */
class HubCommandSource(private val api: CloudApi) : RemoteCommandSource {
    override suspend fun fetch(): RemoteCommand {
        val response = api.command()
        val body = response.body()
        if (!response.isSuccessful || body == null) throw IOException("HTTP ${response.code()}")
        return parseCommand(body)
    }
}

fun parseCommand(body: JsonObject): RemoteCommand {
    val id = body.get("id")
    val completed = body.get("completed")
    require(id != null && id.isJsonPrimitive && id.asJsonPrimitive.isNumber) { "Некоректний id" }
    val number = id.asBigDecimal
    require(number.signum() > 0 && number.stripTrailingZeros().scale() <= 0) { "id має бути додатним цілим" }
    require(completed != null && completed.isJsonPrimitive && completed.asJsonPrimitive.isBoolean) { "completed має бути boolean" }
    return RemoteCommand(number.longValueExact(), completed.asBoolean)
}
