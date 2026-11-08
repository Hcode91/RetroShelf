package io.retroshelf

import org.junit.Assert.*
import org.junit.Test

class WebsitePolicyTests {
    @Test fun `embedded browsing is limited to selected original HTTPS sites`() {
        assertTrue(WebsitePolicy.embedded("steamrip","https://steamrip.com/games-list-page/"))
        assertTrue(WebsitePolicy.embedded("steamrip","https://www.steamrip.com/"))
        assertTrue(WebsitePolicy.embedded("nopaystation-ps3","https://nopaystation.com/search?platform=ps3"))
        assertFalse(WebsitePolicy.embedded("steamrip","https://steamrip.com.example.org/"))
        assertFalse(WebsitePolicy.embedded("steamrip","http://steamrip.com/"))
        assertFalse(WebsitePolicy.embedded("steamrip","https://user:pass@steamrip.com/"))
        assertFalse(WebsitePolicy.embedded("steamrip","file:///etc/passwd"))
        assertFalse(WebsitePolicy.embedded("steamrip","javascript:alert(1)"))
        assertFalse(WebsitePolicy.embedded("steamrip","https://127.0.0.1/"))
        assertFalse(WebsitePolicy.embedded("nopaystation-ps3","https://steamrip.com/"))
        assertFalse(WebsitePolicy.embedded("unknown","https://steamrip.com/"))
    }
}
