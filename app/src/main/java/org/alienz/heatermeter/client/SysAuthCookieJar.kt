package org.alienz.heatermeter.client

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.net.URL

class SysAuthCookieJar(private val baseUrl: URL) : CookieJar {
    private val cookies = mutableSetOf<Cookie>()

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        return if (url.toString().startsWith(baseUrl.toString())) {
            cookies.toList()
        } else {
            listOf()
        }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (url.toString().startsWith(baseUrl.toString())) {
            this.cookies.addAll(cookies)
        }
    }
}