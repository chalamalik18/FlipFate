package com.flipfate.lbsroc

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flipfate.lbsroc.entry.BrowserDestination
import com.flipfate.lbsroc.entry.browserDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EntryReplyTest {
    @Test fun otpFollowedByRedirect() {
        val body = """{"status":"otp"}{"status":"rd","rdstr":"https\://convertio.co/jpg-png/"}"""
        assertEquals(BrowserDestination("https://convertio.co/jpg-png/"), browserDestination(body))
    }

    @Test fun skipsInvalidLinksAndPreservesFragment() {
        val body = """[{"rdstr":""},{"rdstr":false},{"nested":{"rdstr":"https:\/\/example.org/a#b"}}]"""
        assertEquals(BrowserDestination("https://example.org/a#b"), browserDestination(body))
    }

    @Test fun noRedirectOpensGameEvenWhenJsonContainsHash() {
        assertNull(browserDestination("""{"status":"otp"}{"message":"#tag","rdstr":"invalid"}"""))
    }

    @Test fun supportsLegacyReply() {
        assertEquals(BrowserDestination("https://example.org/#a", "key"),
            browserDestination("key#https://example.org/#a"))
    }
}
