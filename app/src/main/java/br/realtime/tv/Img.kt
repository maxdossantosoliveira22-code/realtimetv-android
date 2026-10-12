package br.realtime.tv

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.widget.ImageView
import java.io.File
import java.util.concurrent.Executors

/** carregador de imagens leve: memória + disco, reduz o tamanho ao decodificar */
object Img {
    private val pool = Executors.newFixedThreadPool(3)
    private val dir: File by lazy { File(App.ctx.cacheDir, "img").apply { mkdirs() } }
    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 7).toInt()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun clearDisk() {
        cache.evictAll()
        dir.listFiles()?.forEach { it.delete() }
    }

    fun load(v: ImageView, url: String?, w: Int, h: Int, fade: Boolean = true, onLoaded: (() -> Unit)? = null) {
        v.tag = url
        if (url.isNullOrEmpty() || !url.startsWith("http")) {
            v.setImageDrawable(null)
            return
        }
        val key = url + "@" + w
        val hit = cache.get(key)
        if (hit != null) {
            v.setImageBitmap(hit)
            v.alpha = 1f
            onLoaded?.invoke()
            return
        }
        v.setImageDrawable(null)
        pool.execute {
            val bmp = try { fetch(url, w, h) } catch (e: Throwable) { null }
            if (bmp != null) cache.put(key, bmp)
            Bg.ui {
                if (v.tag == url && bmp != null) {
                    v.setImageBitmap(bmp)
                    if (fade) {
                        v.alpha = 0f
                        v.animate().alpha(1f).setDuration(220).start()
                    } else {
                        v.alpha = 1f
                    }
                    onLoaded?.invoke()
                }
            }
        }
    }

    private fun fetch(url: String, w: Int, h: Int): Bitmap? {
        val f = File(dir, Api.md5(url))
        if (!f.exists() || f.length() < 100) {
            val tmp = File(dir, Api.md5(url) + ".tmp")
            try {
                val c = Api.open(url, 15000) ?: return null
                c.inputStream.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
                c.disconnect()
                tmp.renameTo(f)
            } catch (e: Exception) {
                tmp.delete()
                return null
            }
        }
        val o = BitmapFactory.Options()
        o.inJustDecodeBounds = true
        BitmapFactory.decodeFile(f.absolutePath, o)
        if (o.outWidth <= 0) {
            f.delete()
            return null
        }
        var s = 1
        while (o.outWidth / (s * 2) >= w && o.outHeight / (s * 2) >= h) s *= 2
        val o2 = BitmapFactory.Options()
        o2.inSampleSize = s
        return BitmapFactory.decodeFile(f.absolutePath, o2)
    }
}
