package cn.sanchuang.jianqu.admission

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.content.res.Resources
import android.view.View
import cn.sanchuang.jianqu.JianquDimensions
import cn.sanchuang.jianqu.R
import kotlin.math.max

/**
 * The gate has a static wallpaper. Sample its preblurred image in the same
 * center-crop coordinates as the root, rather than blurring any foreground views.
 * Works on Android 11 without a live backdrop filter or per-frame bitmap allocation.
 */
internal class LeaseFrostedBackground(
    private val root: View,
    private val panel: View
) : Drawable() {
    private val resources = root.resources
    private val bitmap = blurredWallpaper(resources)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val path = Path()
    private val rect = RectF()
    private val target = RectF()
    private val rootLocation = IntArray(2)
    private val panelLocation = IntArray(2)
    private val density = resources.displayMetrics.density
    private val radius = JianquDimensions.companionRadiusDp * density
    private val scrim = root.context.getColor(R.color.jq_lease_scrim)
    private val tint = root.context.getColor(R.color.jq_lease_glass_tint)
    private val edge = root.context.getColor(R.color.jq_lease_glass_edge)

    override fun draw(canvas: Canvas) {
        if (root.width == 0 || root.height == 0 || bounds.isEmpty) return
        root.getLocationInWindow(rootLocation)
        panel.getLocationInWindow(panelLocation)
        val scale = max(root.width.toFloat() / bitmap.width, root.height.toFloat() / bitmap.height)
        val left = (root.width - bitmap.width * scale) / 2f + rootLocation[0] - panelLocation[0]
        val top = (root.height - bitmap.height * scale) / 2f + rootLocation[1] - panelLocation[1]
        target.set(left, top, left + bitmap.width * scale, top + bitmap.height * scale)
        rect.set(bounds)
        path.reset()
        path.addRoundRect(rect, radius, radius, Path.Direction.CW)
        val save = canvas.save()
        canvas.clipPath(path)
        canvas.drawBitmap(bitmap, null, target, paint)
        canvas.drawColor(scrim)
        canvas.drawColor(tint)
        canvas.restoreToCount(save)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = density
        paint.color = edge
        rect.inset(density / 2f, density / 2f)
        canvas.drawRoundRect(rect, radius, radius, paint)
        paint.style = Paint.Style.FILL
        paint.alpha = 255
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
    @Suppress("DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    companion object {
        private var cached: Bitmap? = null

        @Synchronized
        private fun blurredWallpaper(resources: Resources): Bitmap {
            cached?.let { return it }
            val source = BitmapFactory.decodeResource(resources, R.drawable.jq_daylight_background,
                BitmapFactory.Options().apply { inSampleSize = 8; inPreferredConfig = Bitmap.Config.ARGB_8888 })
            val w = source.width
            val h = source.height
            val pixels = IntArray(w * h)
            val scratch = IntArray(w * h)
            source.getPixels(pixels, 0, w, 0, 0, w, h)
            // Two separable box passes yield a soft Gaussian-like blur on the small bitmap.
            repeat(2) {
                blur(pixels, scratch, w, h, true)
                blur(scratch, pixels, w, h, false)
            }
            source.recycle()
            return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888).also { cached = it }
        }

        private fun blur(input: IntArray, output: IntArray, w: Int, h: Int, horizontal: Boolean) {
            for (y in 0 until h) for (x in 0 until w) {
                var r = 0; var g = 0; var b = 0
                for (offset in -2..2) {
                    val sx = if (horizontal) (x + offset).coerceIn(0, w - 1) else x
                    val sy = if (horizontal) y else (y + offset).coerceIn(0, h - 1)
                    val p = input[sy * w + sx]
                    r += (p ushr 16) and 255; g += (p ushr 8) and 255; b += p and 255
                }
                output[y * w + x] = (255 shl 24) or ((r / 5) shl 16) or ((g / 5) shl 8) or (b / 5)
            }
        }
    }
}
