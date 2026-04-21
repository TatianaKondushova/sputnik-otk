package ru.sputnik.otk.data

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class WebhookClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: WebhookClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = WebhookClient(OkHttpClient(), Json { ignoreUnknownKeys = true })
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `send returns Ok on 200 with ok=true`() = runTest {
        server.enqueue(MockResponse().setBody("""{"ok":true}"""))

        val result = client.send(
            url = server.url("/").toString(),
            password = "secret",
            panel = Panel(id = "04:AB", fault = ""),
            master = "Руслан",
            date = LocalDate.of(2026, 4, 20),
        )

        assertEquals(WebhookClient.Result.Ok, result)
    }

    @Test
    fun `send puts all fields into request body`() = runTest {
        server.enqueue(MockResponse().setBody("""{"ok":true}"""))

        client.send(
            url = server.url("/").toString(),
            password = "secret",
            panel = Panel(id = "04:AB", fault = "скол"),
            master = "Камиль",
            date = LocalDate.of(2026, 4, 20),
        )

        val recorded = server.takeRequest()
        val body = recorded.body.readUtf8()
        assertTrue("body must contain password", body.contains("\"password\":\"secret\""))
        assertTrue("body must contain panel", body.contains("\"panel\":\"04:AB\""))
        assertTrue("body must contain master", body.contains("\"master\":\"Камиль\""))
        assertTrue("body must contain date", body.contains("\"date\":\"2026-04-20\""))
        assertTrue("body must contain fault", body.contains("\"fault\":\"скол\""))
        assertEquals("application/json; charset=utf-8", recorded.getHeader("Content-Type"))
    }

    @Test
    fun `send returns WrongPassword on 200 with ok=false and wrong password`() = runTest {
        server.enqueue(MockResponse().setBody("""{"ok":false,"error":"wrong password"}"""))

        val result = client.send(
            url = server.url("/").toString(),
            password = "x",
            panel = Panel(id = "p"),
            master = "m",
            date = LocalDate.of(2026, 4, 20),
        )

        assertEquals(WebhookClient.Result.WrongPassword, result)
    }

    @Test
    fun `send returns ServerError on 200 with ok=false and other error`() = runTest {
        server.enqueue(MockResponse().setBody("""{"ok":false,"error":"quota exceeded"}"""))

        val result = client.send(
            url = server.url("/").toString(),
            password = "x",
            panel = Panel(id = "p"),
            master = "m",
            date = LocalDate.of(2026, 4, 20),
        )

        assertEquals(WebhookClient.Result.ServerError("quota exceeded"), result)
    }

    @Test
    fun `send returns ServerError on non-200 status`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))

        val result = client.send(
            url = server.url("/").toString(),
            password = "x",
            panel = Panel(id = "p"),
            master = "m",
            date = LocalDate.of(2026, 4, 20),
        )

        val r = result as WebhookClient.Result.ServerError
        assertTrue(r.reason.contains("HTTP 500"))
    }

    @Test
    fun `send returns NetworkError when server is not reachable`() = runTest {
        val unreachableUrl = server.url("/").toString()
        server.shutdown()

        val result = client.send(
            url = unreachableUrl,
            password = "x",
            panel = Panel(id = "p"),
            master = "m",
            date = LocalDate.of(2026, 4, 20),
        )

        assertTrue("expected NetworkError, got $result", result is WebhookClient.Result.NetworkError)
    }
}
