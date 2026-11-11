package com.gluelab.jiaoji

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors

/**
 * 拍照识别页。
 *
 * 中间画一个 3:1 的长方形取景框，框里再分六格（M1~M6）——
 * 只认框里的内容，桌面、纸张边缘、手都不会进画面。
 *
 * 拍完按格子各裁一块、各发一次识别：一格只面对一个数，
 * 不用猜顺序，也不用处理「一行里连着一串」的切分问题。
 */
class OcrCameraActivity : ComponentActivity() {

    companion object {
        const val EXTRA_NUMS = "nums"
    }

    private lateinit var rootView: FrameLayout
    private lateinit var previewView: PreviewView
    private lateinit var overlay: FrameOverlay
    private lateinit var status: TextView
    private var capture: ImageCapture? = null
    private val io = Executors.newSingleThreadExecutor()

    private val askPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) startCamera() else {
            status.text = "没有相机权限"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = FrameLayout(this)
        rootView = root
        previewView = PreviewView(this).apply {
            layoutParams = FrameLayout.LayoutParams(-1, -1)
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
        overlay = FrameOverlay().apply { layoutParams = FrameLayout.LayoutParams(-1, -1) }
        status = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 14f
            gravity = Gravity.CENTER
            text = "框里只放一行：一个格子写一个数"
            setPadding(24, 18, 24, 18)
            setBackgroundColor(Color.argb(150, 0, 0, 0))
            layoutParams = FrameLayout.LayoutParams(-1, -2).apply { gravity = Gravity.TOP }
        }
        val shutter = TextView(this).apply {
            text = "拍照识别"
            setTextColor(Color.WHITE)
            textSize = 17f
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#0E7263"))
            layoutParams = FrameLayout.LayoutParams(-1, dp(56)).apply { gravity = Gravity.BOTTOM }
            setOnClickListener { shoot() }
        }

        root.addView(previewView)
        root.addView(overlay)
        root.addView(status)
        root.addView(shutter)
        setContentView(root)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            askPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            runCatching {
                val provider = future.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
            }.onFailure { status.text = "相机打不开：${it.message}" }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun shoot() {
        val c = capture ?: return
        status.text = "拍摄中…"
        c.takePicture(ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val rotation = image.imageInfo.rotationDegrees
                val bitmap = runCatching { image.toBitmap() }.getOrNull()
                image.close()
                if (bitmap == null) {
                    status.text = "拍摄失败"
                    return
                }
                val still = rotate(bitmap, rotation)
                val band = runCatching { cropBand(still) }.getOrElse { still }
                showReview(band)
            }

            override fun onError(exc: ImageCaptureException) {
                status.text = "拍摄失败：${exc.message}"
            }
        })
    }

    private fun rotate(src: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return src
        val m = android.graphics.Matrix().apply { postRotate(degrees.toFloat()) }
        val out = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
        if (out !== src) src.recycle()
        return out
    }

    /**
     * 把「预览里看到的框」换算到「拍到的图」的像素坐标。
     *
     * 预览是 FILL_CENTER：图像会按视图宽高比居中裁一块再铺满，
     * 所以可见区域是图像中心的一个 rect，框的相对位置要落在这个 rect 里。
     */
    private fun cropBand(src: Bitmap): Bitmap {
        val vw = previewView.width.coerceAtLeast(1).toFloat()
        val vh = previewView.height.coerceAtLeast(1).toFloat()
        val viewAspect = vw / vh
        val imgAspect = src.width.toFloat() / src.height

        val visW: Int
        val visH: Int
        val visX: Int
        val visY: Int
        if (imgAspect > viewAspect) {
            // 图更宽：横向居中裁
            visW = (src.height * viewAspect).toInt().coerceAtMost(src.width)
            visH = src.height
            visX = (src.width - visW) / 2
            visY = 0
        } else {
            visH = (src.width / viewAspect).toInt().coerceAtMost(src.height)
            visW = src.width
            visX = 0
            visY = (src.height - visH) / 2
        }

        // 框在可见区域里的像素位置
        var left = visX + (bandFrac.left * visW)
        var top = visY + (bandFrac.top * visH)
        var width = (bandFrac.width() * visW)
        var height = (bandFrac.height() * visH)

        // 数值兜底：算飞了就退回中间 3:1 带
        if (width < 50 || height < 30 || left < 0 || top < 0 ||
            left + width > src.width || top + height > src.height
        ) {
            width = src.width * 0.84f
            height = width / 5f
            if (height > src.height * 0.6f) height = src.height * 0.6f
            left = (src.width - width) / 2f
            top = (src.height - height) / 2f
        }
        return Bitmap.createBitmap(
            src, left.toInt(), top.toInt(),
            width.toInt().coerceAtLeast(1), height.toInt().coerceAtLeast(1),
        )
    }

    /**
     * 拍完先给用户看这张静止图 —— 手举着对角度很难，拍下来看一眼再决定要不要重拍。
     */
    private fun showReview(band: Bitmap) {
        previewView.visibility = View.GONE
        overlay.visibility = View.GONE
        status.text = "看看这张对不对，对了点「识别」"

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(-1, -1)
            setPadding(0, dp(48), 0, dp(12))
        }
        val iv = ImageView(this).apply {
            setImageBitmap(band)
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
        }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(-1, dp(56))
        }
        fun button(text: String, color: Int, onClick: () -> Unit) = TextView(this).apply {
            this.text = text
            setTextColor(Color.WHITE)
            textSize = 16f
            gravity = Gravity.CENTER
            setBackgroundColor(color)
            layoutParams = LinearLayout.LayoutParams(0, -1, 1f)
            setOnClickListener { onClick() }
        }
        bar.addView(button("重拍", Color.parseColor("#5A6663")) {
            rootView.removeView(box)
            previewView.visibility = View.VISIBLE
            overlay.visibility = View.VISIBLE
            status.text = "框里只放一行：一个格子写一个数"
        })
        bar.addView(button("识别", Color.parseColor("#0E7263")) {
            status.text = "识别中…"
            io.execute { recognize(band) }
        })
        box.addView(iv)
        box.addView(bar)
        rootView.addView(box)
    }

    private fun recognize(band: Bitmap) {
        val file = File(cacheDir, "band_${System.nanoTime()}.jpg")
        runCatching {
            file.outputStream().use { band.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        }

        val nums = runCatching {
            val text = RemoteOcr.readText(this, android.net.Uri.fromFile(file))
            DigitOcr.parse(text)
        }.getOrDefault(emptyList())
        file.delete()

        if (nums.isEmpty()) {
            runOnUiThread {
                status.text = "没认出数字，重拍一张（框里只留一行）"
            }
            return
        }

        val data = Intent().putExtra(EXTRA_NUMS, DoubleArray(6) { i -> nums.getOrNull(i) ?: Double.NaN })
        runOnUiThread {
            setResult(Activity.RESULT_OK, data)
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { io.shutdown() }
    }

    /** 取景框：框外压暗，框内画六格和 M1~M6 */
    /** 取景框在预览视图里的相对位置（0~1），用于把「看到的框」换算到「拍到的图」 */
    private val bandFrac = RectF(0.08f, 0.35f, 0.92f, 0.65f)

    private inner class FrameOverlay : View(this@OcrCameraActivity) {
        private val scrim = Paint().apply { color = Color.argb(140, 0, 0, 0) }
        private val frame = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 3f * resources.displayMetrics.density
            isAntiAlias = true
        }
        private val grid = Paint().apply {
            color = Color.argb(170, 255, 255, 255)
            style = Paint.Style.STROKE
            strokeWidth = 1.5f * resources.displayMetrics.density
            isAntiAlias = true
        }
        private val label = Paint().apply {
            color = Color.argb(220, 255, 255, 255)
            textSize = 12f * resources.displayMetrics.density
            isAntiAlias = true
        }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            var bw = w - dp(32)
            var bh = bw / 5f
            if (bh > h * 0.5f) {
                bh = h * 0.5f
                bw = bh * 5f
            }
            val l = (w - bw) / 2
            // 往上挪：下面留出快门和手指的空间，数字也更好对准
            val t = h * 0.38f - bh / 2
            val r = l + bw
            val b = t + bh
            val rect = RectF(l, t, r, b)

            bandFrac.set(l / w, t / h, r / w, b / h)

            canvas.drawRect(0f, 0f, w, t, scrim)
            canvas.drawRect(0f, b, w, h, scrim)
            canvas.drawRect(0f, t, l, b, scrim)
            canvas.drawRect(r, t, w, b, scrim)
            canvas.drawRoundRect(rect, 12f, 12f, frame)

            val cw = bw / 6f
            for (i in 0 until 6) {
                val x = l + i * cw
                if (i > 0) canvas.drawLine(x, t, x, b, grid)
                canvas.drawText("M${i + 1}", x + dp(6), t - dp(8), label)
            }
        }
    }
}
