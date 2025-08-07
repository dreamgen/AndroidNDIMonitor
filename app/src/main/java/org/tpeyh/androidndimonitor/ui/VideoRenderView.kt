package org.tpeyh.androidndimonitor.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import org.tpeyh.androidndimonitor.ndi.NDIVideoFrame

/**
 * NDI 視頻渲染視圖
 * 用於顯示 NDI 視頻流的自定義 SurfaceView
 */
class VideoRenderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : SurfaceView(context, attrs, defStyleAttr), SurfaceHolder.Callback {
    
    companion object {
        private const val TAG = "VideoRenderView"
    }
    
    private var surfaceHolder: SurfaceHolder
    private var isDrawing = false
    private var currentBitmap: Bitmap? = null
    
    // 繪製相關
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 48f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    
    init {
        surfaceHolder = holder
        surfaceHolder.addCallback(this)
        
        // 設置 PixelFormat
        surfaceHolder.setFormat(PixelFormat.RGBA_8888)
        
        Log.d(TAG, "VideoRenderView 初始化完成")
    }
    
    override fun surfaceCreated(holder: SurfaceHolder) {
        Log.d(TAG, "Surface 已創建")
        isDrawing = true
        
        // 繪製初始畫面
        drawInitialFrame()
    }
    
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        Log.d(TAG, "Surface 尺寸變更: ${width}x${height}")
        
        // 重新繪製
        drawInitialFrame()
    }
    
    override fun surfaceDestroyed(holder: SurfaceHolder) {
        Log.d(TAG, "Surface 已銷毀")
        isDrawing = false
    }
    
    /**
     * 繪製初始畫面
     */
    private fun drawInitialFrame() {
        if (!isDrawing) return
        
        try {
            val canvas = surfaceHolder.lockCanvas()
            if (canvas != null) {
                // 清除畫布
                canvas.drawColor(Color.BLACK)
                
                // 繪製 NDI 標誌和狀態
                val centerX = canvas.width / 2f
                val centerY = canvas.height / 2f
                
                // NDI 標誌背景
                paint.color = Color.parseColor("#2196F3")
                val logoRect = RectF(
                    centerX - 150,
                    centerY - 80,
                    centerX + 150,
                    centerY + 80
                )
                canvas.drawRoundRect(logoRect, 16f, 16f, paint)
                
                // NDI 文字
                canvas.drawText("NDI", centerX, centerY + 20, textPaint)
                
                // 狀態文字
                textPaint.textSize = 24f
                canvas.drawText("準備播放視頻流", centerX, centerY + 80, textPaint)
                
                surfaceHolder.unlockCanvasAndPost(canvas)
            }
        } catch (e: Exception) {
            Log.e(TAG, "繪製初始畫面失敗", e)
        }
    }
    
    /**
     * 渲染視頻幀 (舊版本，使用 Bitmap)
     */
    fun renderFrame(bitmap: Bitmap?) {
        if (!isDrawing || bitmap == null) {
            return
        }
        
        currentBitmap = bitmap
        
        try {
            val canvas = surfaceHolder.lockCanvas()
            if (canvas != null) {
                // 計算縮放以保持寬高比
                val scaleX = canvas.width.toFloat() / bitmap.width
                val scaleY = canvas.height.toFloat() / bitmap.height
                val scale = maxOf(scaleX, scaleY)
                
                val scaledWidth = bitmap.width * scale
                val scaledHeight = bitmap.height * scale
                
                val left = (canvas.width - scaledWidth) / 2
                val top = (canvas.height - scaledHeight) / 2
                
                val destRect = RectF(left, top, left + scaledWidth, top + scaledHeight)
                
                // 清除畫布
                canvas.drawColor(Color.BLACK)
                
                // 繪製視頻幀
                canvas.drawBitmap(bitmap, null, destRect, paint)
                
                surfaceHolder.unlockCanvasAndPost(canvas)
                
                Log.v(TAG, "已渲染視頻幀: ${bitmap.width}x${bitmap.height}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "渲染視頻幀失敗", e)
        }
    }
    
    /**
     * 渲染 NDI 視頻幀 (新版本)
     */
    fun renderNDIFrame(frame: NDIVideoFrame) {
        if (!isDrawing) {
            return
        }
        
        try {
            // 嘗試將 NDI 幀數據轉換為 Bitmap
            val bitmap = convertNDIFrameToBitmap(frame)
            
            if (bitmap != null) {
                renderFrame(bitmap)
            } else {
                // 如果無法轉換，顯示模擬幀
                renderMockFrame(frame)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "渲染 NDI 視頻幀失敗", e)
            renderMockFrame(frame)
        }
    }
    
    /**
     * 將 NDI 幀數據轉換為 Bitmap
     */
    private fun convertNDIFrameToBitmap(frame: NDIVideoFrame): Bitmap? {
        return try {
            // 嘗試使用 BitmapFactory 解碼（針對壓縮格式）
            val bitmap = BitmapFactory.decodeByteArray(frame.data, 0, frame.data.size)
            
            if (bitmap != null) {
                Log.d(TAG, "NDI 幀解碼成功: ${bitmap.width}x${bitmap.height}")
                return bitmap
            }
            
            // 如果 BitmapFactory 失敗，嘗試手動創建 Bitmap
            Log.d(TAG, "嘗試手動轉換 NDI 幀: ${frame.width}x${frame.height}, 數據大小: ${frame.data.size}")
            
            val expectedSize = frame.width * frame.height * 4 // ARGB
            if (frame.data.size >= expectedSize) {
                // 手動創建 ARGB Bitmap
                val bitmap = Bitmap.createBitmap(frame.width, frame.height, Bitmap.Config.ARGB_8888)
                
                // 使用像素陣列設置方式
                val pixels = IntArray(frame.width * frame.height)
                
                for (i in 0 until frame.width * frame.height) {
                    val byteIndex = i * 4
                    if (byteIndex + 3 < frame.data.size) {
                        // ARGB 格式轉換
                        val a = (frame.data[byteIndex].toInt() and 0xFF) shl 24
                        val r = (frame.data[byteIndex + 1].toInt() and 0xFF) shl 16
                        val g = (frame.data[byteIndex + 2].toInt() and 0xFF) shl 8
                        val b = frame.data[byteIndex + 3].toInt() and 0xFF
                        
                        pixels[i] = a or r or g or b
                    }
                }
                
                bitmap.setPixels(pixels, 0, frame.width, 0, 0, frame.width, frame.height)
                Log.d(TAG, "NDI 手動轉換成功: ${frame.width}x${frame.height}")
                return bitmap
            }
            
            Log.w(TAG, "NDI 幀數據大小不正確: 期望 $expectedSize，實際 ${frame.data.size}")
            null
        } catch (e: Exception) {
            Log.e(TAG, "NDI 幀轉換 Bitmap 失敗", e)
            null
        }
    }
    
    /**
     * 渲染模擬幀 (當無法解碼實際數據時)
     */
    private fun renderMockFrame(frame: NDIVideoFrame) {
        if (!isDrawing) return
        
        try {
            val canvas = surfaceHolder.lockCanvas()
            if (canvas != null) {
                // 清除畫布
                canvas.drawColor(Color.BLACK)
                
                val centerX = canvas.width / 2f
                val centerY = canvas.height / 2f
                
                // 動態彩色背景
                val time = (System.currentTimeMillis() % 5000) / 5000f
                val r = (kotlin.math.sin(time * 2 * Math.PI).toFloat() * 127 + 128).toInt()
                val g = (kotlin.math.sin(time * 2 * Math.PI + Math.PI * 2 / 3).toFloat() * 127 + 128).toInt()
                val b = (kotlin.math.sin(time * 2 * Math.PI + Math.PI * 4 / 3).toFloat() * 127 + 128).toInt()
                
                paint.color = Color.rgb(r, g, b)
                val mockRect = RectF(
                    centerX - 300,
                    centerY - 200,
                    centerX + 300,
                    centerY + 200
                )
                canvas.drawRoundRect(mockRect, 20f, 20f, paint)
                
                // 內框
                paint.color = Color.BLACK
                paint.alpha = 150
                val innerRect = RectF(
                    centerX - 280,
                    centerY - 180,
                    centerX + 280,
                    centerY + 180
                )
                canvas.drawRoundRect(innerRect, 16f, 16f, paint)
                paint.alpha = 255
                
                // NDI 標誌（動態編色）
                textPaint.textSize = 56f
                val titleR = (kotlin.math.sin(time * 4 * Math.PI).toFloat() * 127 + 128).toInt()
                val titleG = (kotlin.math.sin(time * 4 * Math.PI + Math.PI / 2).toFloat() * 127 + 128).toInt()
                val titleB = (kotlin.math.sin(time * 4 * Math.PI + Math.PI).toFloat() * 127 + 128).toInt()
                textPaint.color = Color.rgb(titleR, titleG, titleB)
                canvas.drawText("NDI STREAM", centerX, centerY - 80, textPaint)
                
                // 幀資訊
                textPaint.textSize = 28f
                textPaint.color = Color.WHITE
                canvas.drawText("${frame.width} x ${frame.height}", centerX, centerY - 30, textPaint)
                canvas.drawText("${frame.frameRate.toInt()} FPS", centerX, centerY + 10, textPaint)
                
                // 來源資訊
                textPaint.textSize = 20f
                textPaint.color = Color.parseColor("#FFEB3B")
                canvas.drawText("來源: ${frame.sourceName}", centerX, centerY + 60, textPaint)
                
                // 時間戳
                val timeStr = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                    .format(java.util.Date(frame.timestamp))
                textPaint.textSize = 16f
                textPaint.color = Color.parseColor("#00BCD4")
                canvas.drawText("時間: $timeStr", centerX, centerY + 100, textPaint)
                
                // 動畫效果 - 多個旋轉圓圈
                val animationTime = (System.currentTimeMillis() % 3000) / 3000f * 360f
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 4f
                
                for (i in 1..3) {
                    paint.color = Color.WHITE
                    paint.alpha = 255 / i
                    val radius = 30f + i * 20f
                    canvas.save()
                    canvas.rotate(animationTime * (if (i % 2 == 0) -1 else 1), centerX, centerY + 140)
                    canvas.drawCircle(centerX, centerY + 140, radius, paint)
                    canvas.restore()
                }
                
                paint.style = Paint.Style.FILL
                paint.alpha = 255
                
                // 信號指示器
                val signalStrength = kotlin.math.sin(time * 6 * Math.PI).toFloat() * 0.5f + 0.5f
                paint.color = if (signalStrength > 0.7) Color.GREEN 
                           else if (signalStrength > 0.4) Color.YELLOW 
                           else Color.RED
                
                for (i in 1..5) {
                    val barHeight = 10f + (signalStrength * 40f * i / 5f)
                    canvas.drawRect(
                        centerX - 60f + i * 25f - 10f,
                        centerY + 160f - barHeight,
                        centerX - 60f + i * 25f + 10f,
                        centerY + 160f,
                        paint
                    )
                }
                
                surfaceHolder.unlockCanvasAndPost(canvas)
                
                Log.v(TAG, "已渲染動態模擬 NDI 幀: ${frame.width}x${frame.height}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "渲染模擬幀失敗", e)
        }
    }
    
    /**
     * 顯示錯誤狀態
     */
    fun showError(errorMessage: String) {
        if (!isDrawing) return
        
        try {
            val canvas = surfaceHolder.lockCanvas()
            if (canvas != null) {
                // 清除畫布
                canvas.drawColor(Color.BLACK)
                
                val centerX = canvas.width / 2f
                val centerY = canvas.height / 2f
                
                // 錯誤背景
                paint.color = Color.parseColor("#F44336")
                val errorRect = RectF(
                    centerX - 200,
                    centerY - 100,
                    centerX + 200,
                    centerY + 100
                )
                canvas.drawRoundRect(errorRect, 16f, 16f, paint)
                
                // 錯誤圖標（簡單的 X）
                paint.color = Color.WHITE
                paint.strokeWidth = 8f
                canvas.drawLine(centerX - 30, centerY - 50, centerX + 30, centerY - 10, paint)
                canvas.drawLine(centerX + 30, centerY - 50, centerX - 30, centerY - 10, paint)
                
                // 錯誤標題
                textPaint.textSize = 32f
                textPaint.color = Color.WHITE
                canvas.drawText("連接錯誤", centerX, centerY + 20, textPaint)
                
                // 錯誤消息
                textPaint.textSize = 18f
                canvas.drawText(errorMessage, centerX, centerY + 60, textPaint)
                
                surfaceHolder.unlockCanvasAndPost(canvas)
            }
        } catch (e: Exception) {
            Log.e(TAG, "顯示錯誤狀態失敗", e)
        }
    }
    
    /**
     * 顯示載入狀態
     */
    fun showLoading(message: String = "正在連接...") {
        if (!isDrawing) return
        
        try {
            val canvas = surfaceHolder.lockCanvas()
            if (canvas != null) {
                // 清除畫布
                canvas.drawColor(Color.BLACK)
                
                val centerX = canvas.width / 2f
                val centerY = canvas.height / 2f
                
                // 載入動畫背景
                paint.color = Color.parseColor("#FF9800")
                val loadingRect = RectF(
                    centerX - 150,
                    centerY - 80,
                    centerX + 150,
                    centerY + 80
                )
                canvas.drawRoundRect(loadingRect, 16f, 16f, paint)
                
                // 載入文字
                textPaint.textSize = 28f
                textPaint.color = Color.WHITE
                canvas.drawText("NDI 載入中", centerX, centerY, textPaint)
                
                textPaint.textSize = 18f
                canvas.drawText(message, centerX, centerY + 40, textPaint)
                
                surfaceHolder.unlockCanvasAndPost(canvas)
            }
        } catch (e: Exception) {
            Log.e(TAG, "顯示載入狀態失敗", e)
        }
    }
    
    /**
     * 顯示連接成功狀態
     */
    fun showConnected(sourceName: String) {
        if (!isDrawing) return
        
        try {
            val canvas = surfaceHolder.lockCanvas()
            if (canvas != null) {
                // 清除畫布
                canvas.drawColor(Color.BLACK)
                
                val centerX = canvas.width / 2f
                val centerY = canvas.height / 2f
                
                // 成功狀態背景
                paint.color = Color.parseColor("#4CAF50")
                val connectedRect = RectF(
                    centerX - 200,
                    centerY - 100,
                    centerX + 200,
                    centerY + 100
                )
                canvas.drawRoundRect(connectedRect, 16f, 16f, paint)
                
                // 勾選圖標（簡單的勾）
                paint.color = Color.WHITE
                paint.strokeWidth = 8f
                paint.style = Paint.Style.STROKE
                val checkPath = Path()
                checkPath.moveTo(centerX - 30, centerY - 30)
                checkPath.lineTo(centerX - 10, centerY - 10)
                checkPath.lineTo(centerX + 30, centerY - 50)
                canvas.drawPath(checkPath, paint)
                paint.style = Paint.Style.FILL
                
                // 成功文字
                textPaint.textSize = 28f
                textPaint.color = Color.WHITE
                canvas.drawText("連接成功", centerX, centerY + 20, textPaint)
                
                textPaint.textSize = 16f
                canvas.drawText(sourceName, centerX, centerY + 60, textPaint)
                
                surfaceHolder.unlockCanvasAndPost(canvas)
            }
        } catch (e: Exception) {
            Log.e(TAG, "顯示連接成功狀態失敗", e)
        }
    }
    
    /**
     * 清理資源
     */
    fun cleanup() {
        isDrawing = false
        currentBitmap?.recycle()
        currentBitmap = null
        Log.d(TAG, "VideoRenderView 已清理")
    }
}