package com.example.lab4

import com.example.lab4.control.*
import com.google.gson.JsonParser
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class RemoteControlTest {
    @Test fun validatesStrictBooleanAndPositiveIntegerId() {
        assertTrue(RemoteRepository.parse(JsonParser.parseString("{\"id\":1,\"completed\":true}").asJsonObject).on)
        for (json in listOf("{}", "{\"id\":1,\"completed\":\"true\"}", "{\"id\":1.5,\"completed\":true}", "{\"id\":0,\"completed\":true}", "{\"id\":1,\"completed\":null}")) {
            assertTrue(runCatching { RemoteRepository.parse(JsonParser.parseString(json).asJsonObject) }.isFailure)
        }
    }
    @Test fun realGetReadsChangedCommandAndHttpErrorFails() = runTest {
        MockWebServer().use { server ->
            server.start()
            val repo = RemoteRepository()
            server.enqueue(MockResponse().setBody("{\"id\":7,\"completed\":true}"))
            server.enqueue(MockResponse().setBody("{\"id\":8,\"completed\":false}"))
            server.enqueue(MockResponse().setResponseCode(503))
            val url=server.url("/command").toString()
            assertTrue(repo.fetch(url).first.on); assertFalse(repo.fetch(url).first.on)
            assertTrue(runCatching { repo.fetch(url) }.isFailure)
            val request=server.takeRequest();assertEquals("GET",request.method);assertEquals("/command",request.path)
        }
    }
}
