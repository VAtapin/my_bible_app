package com.bibledesktop.shared.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin

internal actual fun createPlatformHttpClient(): HttpClient = HttpClient(Darwin) {
    configureBibleApiClient()
}
