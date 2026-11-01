package com.example.lab2

import com.example.lab2.cloud.CloudApi
import com.example.lab2.cloud.CloudClient
import com.example.lab2.cloud.SensorData
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class CloudApiTest {
    @Test fun retrofitSendsPostJsonAndParsesEchoedCoordinates() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val sample = SensorData("lab-test", 1730000000000L, 420f,
                x = 2f, y = -3f, z = 9.1f, sampleId = "sample-1")
            val echo = JsonParser.parseString(CloudClient.gson.toJson(sample)).asJsonObject
            echo.addProperty("id", 101)
            server.enqueue(MockResponse().setResponseCode(201)
                .setHeader("Content-Type", "application/json").setBody(echo.toString()))
            val api = Retrofit.Builder().baseUrl(server.url("/"))
                .addConverterFactory(GsonConverterFactory.create(CloudClient.gson)).build().create(CloudApi::class.java)
            val response = api.postSensor(sample)
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/posts", request.path)
            assertTrue(request.getHeader("Content-Type")!!.startsWith("application/json"))
            val json = JsonParser.parseString(request.body.readUtf8()).asJsonObject
            assertEquals("lab-test", json.get("deviceId").asString)
            assertEquals(1730000000000L, json.get("timestamp").asLong)
            assertEquals(-3f, json.get("y").asFloat)
            assertEquals("light", json.get("type").asString)
            assertEquals(201, response.code())
            assertEquals("sample-1", response.body()!!.get("sampleId").asString)
            assertEquals(9.1f, response.body()!!.get("z").asFloat)
        } finally { server.shutdown() }
    }
}
