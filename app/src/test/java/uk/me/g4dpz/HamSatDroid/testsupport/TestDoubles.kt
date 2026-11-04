package uk.me.g4dpz.HamSatDroid.testsupport

import uk.me.g4dpz.HamSatDroid.data.DownloadResponse
import uk.me.g4dpz.HamSatDroid.data.KepsDownloader
import uk.me.g4dpz.HamSatDroid.data.KeyValueStore
import java.io.IOException

/** In-memory settings store. */
class MemoryStore : KeyValueStore {
    val map = HashMap<String, Any>()
    override fun getString(key: String, default: String) = map[key] as? String ?: default
    override fun putString(key: String, value: String) { map[key] = value }
    override fun getBoolean(key: String, default: Boolean) = map[key] as? Boolean ?: default
    override fun putBoolean(key: String, value: Boolean) { map[key] = value }
    override fun getInt(key: String, default: Int) = map[key] as? Int ?: default
    override fun putInt(key: String, value: Int) { map[key] = value }
}

/** A downloader whose answers are scripted by the test; it fails with an IOException until told otherwise. */
class FakeDownloader : KepsDownloader {
    val requested = ArrayList<String>()
    var next: () -> DownloadResponse = { throw IOException("no response scripted") }
    override fun download(url: String): DownloadResponse {
        requested.add(url)
        return next()
    }
}
