package com.reverfyx.spasaemmamuptitsu

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import okhttp3.Dns
import okhttp3.OkHttpClient
import java.net.InetAddress
import java.util.concurrent.TimeUnit

class App : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader {
        val pinnedDns = object : Dns {
            override fun lookup(hostname: String): List<InetAddress> {
                return if (hostname.equals("mellstroy.work.gd", ignoreCase = true)) {
                    listOf(InetAddress.getByName("2.26.85.86"))
                } else {
                    Dns.SYSTEM.lookup(hostname)
                }
            }
        }

        val http = OkHttpClient.Builder()
            .dns(pinnedDns)
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        return ImageLoader.Builder(this)
            .okHttpClient(http)
            .crossfade(true)
            .build()
    }
}
