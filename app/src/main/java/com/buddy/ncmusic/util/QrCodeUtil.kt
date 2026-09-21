package com.buddy.ncmusic.util

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Base64
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.io.ByteArrayOutputStream

/**
 * 二维码生成工具。
 * 直连模式下登录二维码由客户端本地渲染（服务器模式则由接口直接返回图片）。
 */
object QrCodeUtil {

    /** 把文本生成二维码 Bitmap，失败返回 null */
    fun toBitmap(content: String, size: Int = 480): Bitmap? = runCatching {
        val hints = mapOf(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.MARGIN to 1,
        )
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
        val pixels = IntArray(size * size)
        for (y in 0 until size) {
            for (x in 0 until size) {
                pixels[y * size + x] = if (matrix.get(x, y)) Color.BLACK else Color.WHITE
            }
        }
        Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565).apply {
            setPixels(pixels, 0, size, 0, 0, size, size)
        }
    }.getOrNull()

    /** 把文本生成二维码，返回裸 base64（不含 data URI 前缀，由调用方拼装） */
    fun toBase64(content: String, size: Int = 480): String? = runCatching {
        val bitmap = toBitmap(content, size) ?: return null
        val bytes = ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray()
        }
        bitmap.recycle()
        Base64.encodeToString(bytes, Base64.NO_WRAP)
    }.getOrNull()
}
