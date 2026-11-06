package th.co.lotto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GloResultClientTest {
    private val validResponse = """
        {
          "statusCode": 200,
          "status": true,
          "response": {
            "result": {
              "date": "2026-07-16",
              "data": {
                "first": {
                  "price": "6000000",
                  "number": [
                    { "round": 1, "value": "639214" }
                  ]
                },
                "last2": {
                  "number": [
                    { "round": 1, "value": "01" }
                  ]
                }
              }
            }
          }
        }
    """.trimIndent()

    @Test
    fun extractsFirstPrizeTailAndOfficialLastTwo() {
        val result = GloResultClient.parseResponse(validResponse, "2026-07-16", fetchedAtEpochMillis = 123L)
        assertEquals("2026-07-16", result.drawDateIso)
        assertEquals("639214", result.firstPrizeNumber)
        assertEquals("214", result.threeTopNumber)
        assertEquals("01", result.twoBottomNumber)
        assertEquals(123L, result.fetchedAtEpochMillis)
    }

    @Test
    fun preservesLeadingZeroesInBothDerivedAndOfficialNumbers() {
        val result = GloResultClient.parseResponse(validResponse.replace("639214", "123004"), "2026-07-16")
        assertEquals("004", result.threeTopNumber)
        assertEquals("01", result.twoBottomNumber)
    }

    @Test
    fun rejectsWrongDateInvalidFirstPrizeAndMissingLastTwo() {
        assertThrows(IllegalArgumentException::class.java) {
            GloResultClient.parseResponse(validResponse, "2026-07-01")
        }
        assertThrows(IllegalArgumentException::class.java) {
            GloResultClient.parseResponse(validResponse.replace("639214", "12345"), "2026-07-16")
        }
        assertThrows(IllegalStateException::class.java) {
            GloResultClient.parseResponse(validResponse.replace("\"last2\":", "\"missingLast2\":"), "2026-07-16")
        }
    }

    @Test
    fun rejectsUnsuccessfulAndMissingGloResponses() {
        assertThrows(IllegalArgumentException::class.java) {
            GloResultClient.parseResponse(validResponse.replace("\"status\": true", "\"status\": false"), "2026-07-16")
        }
        assertThrows(IllegalStateException::class.java) {
            GloResultClient.parseResponse("""{"statusCode":200,"status":true,"response":{"result":null}}""", "2026-07-16")
        }
    }
}
