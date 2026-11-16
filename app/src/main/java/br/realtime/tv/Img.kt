package br.realtime.tv

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.widget.ImageView
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** carregador de imagens leve: memória + disco, fila "mais novo primeiro", reduz o tamanho ao decodificar */
object Img {
    private class Lifo : LinkedBlockingDeque<Runnable>() {
        override fun offer(e: Runnable): Boolean = offerFirst(e)
    }

    private val pool = ThreadPoolExecutor(4, 4, 30L, TimeUnit.SECONDS, Lifo()).apply { allowCoreThreadTimeOut(true) }
    private val dir: File by lazy { File(App.ctx.cacheDir, "img").apply { mkdirs() } }
    private val failed = ConcurrentHashMap<String, Long>()
    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 6).toInt()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun clearDisk() {
        cache.evictAll()
        failed.clear()
        dir.listFiles()?.forEach { it.delete() }
    }

    /** [small] = true: capas sem transparência (usa metade da memória) */
    fun load(v: ImageView, url: String?, w: Int, h: Int, fade: Boolean = true, small: Boolean = false, onLoaded: (() -> Unit)? = null) {
        v.tag = url
        if (url.isNullOrEmpty() || !url.startsWith("http")) {
            v.setImageDrawable(null)
            return
        }
        val key = url + "@" + w + (if (small) "s" else "")
        val hit = cache.get(key)
        if (hit != null) {
            v.setImageBitmap(hit)
            v.alpha = 1f
            onLoaded?.invoke()
            return
        }
        v.setImageDrawable(null)
        val bad = failed[url]
        if (bad != null && System.currentTimeMillis() - bad < 120000) return
        pool.execute {
            // a imagem já saiu da tela (lista rolou): nem baixa
            if (v.tag != url) return@execute
            val bmp = try { fetch(url, w, h, small) } catch (e: Throwable) { null }
            if (bmp != null) cache.put(key, bmp) else failed[url] = System.currentTimeMillis()
            Bg.ui {
                if (v.tag == url && bmp != null) {
                    v.setImageBitmap(bmp)
                    if (fade) {
                        v.alpha = 0f
                        v.animate().alpha(1f).setDuration(180).start()
                    } else {
                        v.alpha = 1f
                    }
                    onLoaded?.invoke()
                }
            }
        }
    }

    private fun fetch(url: String, w: Int, h: Int, small: Boolean): Bitmap? {
        val f = File(dir, Api.md5(url))
        if (!f.exists() || f.length() < 100) {
            val tmp = File(dir, Api.md5(url) + ".tmp")
            try {
                val c = Api.open(url, 7000, retry = false) ?: return null
                c.inputStream.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
                if (tmp.length() < 100 || !tmp.renameTo(f)) {
                    tmp.delete()
                    return null
                }
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
        if (small) o2.inPreferredConfig = Bitmap.Config.RGB_565
        return BitmapFactory.decodeFile(f.absolutePath, o2)
    }
}
