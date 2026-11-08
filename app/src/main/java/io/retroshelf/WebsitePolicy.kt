package io.retroshelf

import java.net.URI

object WebsitePolicy {
    fun embedded(provider: String, url: String): Boolean {
        if(!UrlSafety.valid(url)) return false
        val allowed = when(provider) {
            "steamrip" -> "steamrip.com"
            "nopaystation-ps3" -> "nopaystation.com"
            else -> return false
        }
        return URI(url).host?.lowercase(java.util.Locale.ROOT)?.removePrefix("www.") == allowed
    }
}
