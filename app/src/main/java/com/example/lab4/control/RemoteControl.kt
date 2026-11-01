package com.example.lab4.control

import com.google.gson.JsonObject
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Url
import java.io.IOException
import java.util.concurrent.TimeUnit

data class RemoteCommand(val id: Long, val on: Boolean)
interface ControlApi {
    @GET suspend fun read(@Url url: String): retrofit2.Response<JsonObject>
}

class RemoteRepository(private val api: ControlApi = createApi()) {
    suspend fun fetch(url: String): Pair<RemoteCommand, String> {
        val response = api.read(url)
        if (!response.isSuccessful) throw IOException("HTTP ${response.code()}")
        val body = response.body() ?: throw IOException("Порожня відповідь")
        return parse(body) to body.toString()
    }
    companion object {
        const val PUBLIC_URL = "https://jsonplaceholder.typicode.com/todos/1"
        const val DEMO_URL = "http://127.0.0.1:8787/command"
        fun parse(body: JsonObject): RemoteCommand {
            val id = body.get("id")
            val completed = body.get("completed")
            require(id != null && id.isJsonPrimitive && id.asJsonPrimitive.isNumber) { "Некоректний id" }
            val number = id.asBigDecimal
            require(number.signum() > 0 && number.stripTrailingZeros().scale() <= 0) { "id має бути додатним цілим" }
            require(completed != null && completed.isJsonPrimitive && completed.asJsonPrimitive.isBoolean) { "completed має бути boolean" }
            return RemoteCommand(number.longValueExact(), completed.asBoolean)
        }
        fun createApi(): ControlApi = Retrofit.Builder()
            .baseUrl("https://jsonplaceholder.typicode.com/")
            .client(OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS).callTimeout(6, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true).build()) // GET is idempotent; reconnect a stale socket.
            .addConverterFactory(GsonConverterFactory.create()).build().create(ControlApi::class.java)
    }
}
