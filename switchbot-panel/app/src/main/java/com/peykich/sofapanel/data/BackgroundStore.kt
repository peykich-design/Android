package com.peykich.sofapanel.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import java.io.File
import kotlin.math.max

/**
 * 背景画像。選んだ画像を画面に合う大きさ（長辺 1920px まで）に縮めて、アプリ内に JPEG で保存する。
 * 元の画像を後で消しても背景は残る。
 */
class BackgroundStore(context: Context) {
    private val app = context.applicationContext
    private val file = File(app.filesDir, "background.jpg")

    /** 画像を取り込んで保存する。失敗したら例外 */
    fun import(uri: Uri) {
        val bitmap = decode(uri)
        val tmp = File(app.filesDir, "background.tmp")
        tmp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bitmap.recycle()
        if (!tmp.renameTo(file)) {
            tmp.copyTo(file, overwrite = true)
            tmp.delete()
        }
    }

    fun load(): Bitmap? = if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null

    fun clear() {
        file.delete()
    }

    private fun decode(uri: Uri): Bitmap {
        if (Build.VERSION.SDK_INT >= 28) {
            // ImageDecoder は写真の向き（EXIF）も反映してくれる
            val source = ImageDecoder.createSource(app.contentResolver, uri)
            return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val longSide = max(info.size.width, info.size.height)
                if (longSide > MAX_SIDE) {
                    val scale = MAX_SIDE.toFloat() / longSide
                    decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
                }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        app.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return app.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: error("画像を読み込めません")
    }

    private companion object {
        const val MAX_SIDE = 1920
    }
}
