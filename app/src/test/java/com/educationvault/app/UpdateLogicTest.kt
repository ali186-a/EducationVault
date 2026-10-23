package com.educationvault.app

import com.educationvault.app.update.UpdateDecision
import com.educationvault.app.update.UpdateInfo
import com.educationvault.app.update.UpdateLogic
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateLogicTest {
    private fun info(latest: Int, min: Int = 0, url: String = "https://x.example/app.apk") =
        UpdateInfo(latest, "1.0", min, url, "")

    @Test fun noOfferWhenUpToDateOrNewer() {
        assertEquals(UpdateDecision.None, UpdateLogic.decide(12, info(12)))
        assertEquals(UpdateDecision.None, UpdateLogic.decide(13, info(12)))
    }

    @Test fun optionalOfferWhenBehind() {
        assertTrue(UpdateLogic.decide(10, info(12)) is UpdateDecision.Optional)
    }

    @Test fun requiredWhenBelowMinimum() {
        assertTrue(UpdateLogic.decide(9, info(12, min = 10)) is UpdateDecision.Required)
        assertTrue(UpdateLogic.decide(10, info(12, min = 10)) is UpdateDecision.Optional)
    }

    @Test fun neverBlocksWithoutADownloadLink() {
        assertEquals(UpdateDecision.None, UpdateLogic.decide(1, info(12, min = 11, url = "")))
    }

    @Test fun backendNotConfiguredMeansNothingToOffer() {
        val i = UpdateLogic.parse(JSONObject("""{"latestVersionCode":0,"latestVersionName":"","minVersionCode":0,"updateUrl":"","notes":""}"""))
        assertEquals(UpdateDecision.None, UpdateLogic.decide(1, i))
    }

    @Test fun parsesAndLabels() {
        val i = UpdateLogic.parse(JSONObject("""{"latestVersionCode":12,"latestVersionName":"1.2","minVersionCode":5,"updateUrl":"https://a.example/x","notes":"Fixes"}"""))
        assertEquals(12, i.latestCode)
        assertEquals("1.2", i.label)
        assertEquals("12", UpdateInfo(12, "", 0, "https://a.example/x", "").label)
    }
}
