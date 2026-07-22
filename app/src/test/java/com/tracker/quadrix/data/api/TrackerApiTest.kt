package com.tracker.quadrix.data.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Covers [TrackerApi.parseLocationResponse], which must tolerate both the documented envelope
 * and the bare `{"version":"…"}` the backend currently returns for `POST /api/tablet/location/`.
 */
class TrackerApiTest {

    private val api = TrackerApi(accessTokenProvider = { null })

    @Test
    fun `bare version body is parsed (current backend shape)`() {
        // The exact 19-byte body seen in logcat.
        val result = api.parseLocationResponse("""{"version":"1.0.0"}""")
        assertEquals("1.0.0", result.appVersion)
        assertNull(result.deviceId)
    }

    @Test
    fun `documented envelope with app_version is parsed`() {
        val body = """
            {"status":"success","data":{"device_id":"abc-123","app_version":"2.3.4"},
             "message":"Tablet location updated.","errors":null}
        """.trimIndent()
        val result = api.parseLocationResponse(body)
        assertEquals("2.3.4", result.appVersion)
        assertEquals("abc-123", result.deviceId)
    }

    @Test
    fun `envelope without app_version still yields device id and no crash`() {
        val body = """{"status":"success","data":{"device_id":"abc-123"}}"""
        val result = api.parseLocationResponse(body)
        assertNull(result.appVersion)
        assertEquals("abc-123", result.deviceId)
    }

    @Test
    fun `unexpected body does not throw`() {
        assertNull(api.parseLocationResponse("not json").appVersion)
        assertNull(api.parseLocationResponse("").appVersion)
    }
}
