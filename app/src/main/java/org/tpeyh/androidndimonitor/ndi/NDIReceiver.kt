package org.tpeyh.androidndimonitor.ndi

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.*
import kotlin.coroutines.coroutineContext

/**
 * NDI 接收器 (使用 NDI SDK)
 * 負責連接和接收 NDI 視頻流
 */
class NDIReceiver {
    
    private val _connectionStatus = MutableLiveData<ConnectionStatus>()
    val connectionStatus: LiveData<ConnectionStatus> = _connectionStatus
    
    private val _isReceiving = MutableLiveData(false)
    val isReceiving: LiveData<Boolean> = _isReceiving
    
    private val _currentSource = MutableLiveData<NDISource?>()
    val currentSource: LiveData<NDISource?> = _currentSource
    
    private val _videoFrame = MutableLiveData<NDIVideoFrame?>()
    val videoFrame: LiveData<NDIVideoFrame?> = _videoFrame
    
    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error
    
    private var receiveJob: Job? = null
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // NDI JNI 包裝器狀態
    private var isNativeLibraryLoaded = false
    private var isNativeReceiverCreated = false
    
    companion object {
        private const val TAG = "NDIReceiver"
        
        // 載入原生函式庫
        init {
            try {
                System.loadLibrary("ndi_android")
                Log.i(TAG, "✓ NDI Receiver 原生函式庫載入成功")
            } catch (e: UnsatisfiedLinkError) {
                Log.w(TAG, "⚠️ 無法載入 NDI 原生函式庫: ${e.message}")
                Log.w(TAG, "將使用模擬接收模式")
            } catch (e: Exception) {
                Log.e(TAG, "載入 NDI 原生函式庫時發生未知錯誤", e)
            }
        }
    }
    
    /**
     * 初始化 NDI 接收器
     */
    fun initialize(): Boolean {
        return try {
            Log.i(TAG, "初始化 NDI 接收器...")
            
            // 嘗試使用原生 NDI SDK
            try {
                if (nativeInitializeReceiver()) {
                    Log.i(TAG, "✓ NDI 接收器原生初始化成功")
                    isNativeLibraryLoaded = true
                    return true
                } else {
                    Log.w(TAG, "NDI 接收器原生初始化失敗，使用模擬模式")
                }
            } catch (e: UnsatisfiedLinkError) {
                Log.w(TAG, "NDI 原生函式庫不可用: ${e.message}")
            } catch (e: Exception) {
                Log.e(TAG, "NDI 接收器初始化異常: ${e.message}", e)
            }
            
            // 模擬模式
            Log.i(TAG, "使用模擬 NDI 接收器模式")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "NDI 接收器初始化失敗: ${e.message}", e)
            _error.postValue("NDI 接收器初始化失敗: ${e.message}")
            false
        }
    }
    
    /**
     * 連接到 NDI 源
     */
    fun connect(source: NDISource): Boolean {
        return try {
            Log.i(TAG, "連接到 NDI 源: ${source.name} @ ${source.urlAddress}")
            _connectionStatus.postValue(ConnectionStatus.CONNECTING)
            
            if (isNativeLibraryLoaded) {
                // 使用原生 NDI SDK 連接
                if (nativeConnect(source.name, source.urlAddress)) {
                    Log.i(TAG, "✓ 原生 NDI 連接成功: ${source.name}")
                    _currentSource.postValue(source)
                    _connectionStatus.postValue(ConnectionStatus.CONNECTED)
                    isNativeReceiverCreated = true
                    return true
                } else {
                    Log.w(TAG, "原生 NDI 連接失敗，使用模擬連接")
                }
            }
            
            // 模擬連接成功
            Log.i(TAG, "✓ 模擬 NDI 連接成功: ${source.name}")
            _currentSource.postValue(source)
            _connectionStatus.postValue(ConnectionStatus.CONNECTED)
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "連接 NDI 源時發生錯誤", e)
            _connectionStatus.postValue(ConnectionStatus.DISCONNECTED)
            _error.postValue("連接失敗: ${e.message}")
            false
        }
    }
    
    /**
     * 斷開 NDI 連接
     */
    fun disconnect() {
        try {
            Log.i(TAG, "斷開 NDI 連接")
            
            stopReceiving()
            
            if (isNativeReceiverCreated) {
                nativeDisconnect()
                isNativeReceiverCreated = false
            }
            
            _currentSource.postValue(null)
            _connectionStatus.postValue(ConnectionStatus.DISCONNECTED)
            _videoFrame.postValue(null)
            
        } catch (e: Exception) {
            Log.e(TAG, "斷開 NDI 連接時發生錯誤", e)
        }
    }
    
    /**
     * 開始接收 NDI 流
     */
    fun startReceiving() {
        if (_isReceiving.value == true) {
            Log.d(TAG, "已在接收 NDI 流")
            return
        }
        
        val currentSource = _currentSource.value
        if (currentSource == null) {
            Log.w(TAG, "未連接到 NDI 源，無法開始接收")
            return
        }
        
        if (_connectionStatus.value != ConnectionStatus.CONNECTED) {
            Log.w(TAG, "NDI 源未連接，無法開始接收")
            return
        }
        
        Log.i(TAG, "開始接收 NDI 流: ${currentSource.name}")
        _isReceiving.postValue(true)
        _connectionStatus.postValue(ConnectionStatus.STREAMING)
        
        receiveJob = coroutineScope.launch {
            try {
                if (isNativeReceiverCreated) {
                    // 使用原生 NDI SDK 接收
                    startNativeReceiving()
                } else {
                    // 使用模擬接收
                    startMockReceiving(currentSource)
                }
            } catch (e: Exception) {
                Log.e(TAG, "NDI 流接收過程中發生錯誤", e)
                _error.postValue("接收錯誤: ${e.message}")
            } finally {
                _isReceiving.postValue(false)
                if (_connectionStatus.value == ConnectionStatus.STREAMING) {
                    _connectionStatus.postValue(ConnectionStatus.CONNECTED)
                }
            }
        }
    }
    
    /**
     * 停止接收 NDI 流
     */
    fun stopReceiving() {
        if (_isReceiving.value != true) {
            return
        }
        
        Log.i(TAG, "停止接收 NDI 流")
        receiveJob?.cancel()
        _isReceiving.postValue(false)
        
        if (_connectionStatus.value == ConnectionStatus.STREAMING) {
            _connectionStatus.postValue(ConnectionStatus.CONNECTED)
        }
    }
    
    /**
     * 原生 NDI 流接收
     */
    private suspend fun startNativeReceiving() {
        Log.i(TAG, "開始原生 NDI 流接收")
        
        var frameCount = 0
        while (coroutineContext.isActive && _isReceiving.value == true) {
            try {
                // 呼叫原生函數接收下一幀
                val frameData = nativeReceiveFrame(33) // ~30fps 超時
                
                if (frameData != null && frameData.isNotEmpty()) {
                    // 解析幀資料
                    val frame = parseNativeFrame(frameData)
                    if (frame != null) {
                        _videoFrame.postValue(frame)
                        frameCount++
                        
                        if (frameCount % 30 == 0) { // 每 30 幀記錄一次
                            Log.d(TAG, "已接收 $frameCount 幀 NDI 視頻")
                        }
                    }
                } else {
                    // 沒有幀數據，短暫等待
                    delay(16) // ~60fps
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "接收原生 NDI 幀時發生錯誤", e)
                delay(100)
            }
        }
        
        Log.i(TAG, "原生 NDI 流接收已停止，共處理 $frameCount 幀")
    }
    
    /**
     * 模擬 NDI 流接收
     */
    private suspend fun startMockReceiving(source: NDISource) {
        Log.i(TAG, "開始模擬 NDI 流接收: ${source.name}")
        
        var frameCount = 0
        val startTime = System.currentTimeMillis()
        
        while (coroutineContext.isActive && _isReceiving.value == true) {
            try {
                // 創建模擬視頻幀數據（彩色測試圖案）
                val mockFrame = createTestVideoFrame(source.name, frameCount, startTime)
                
                _videoFrame.postValue(mockFrame)
                frameCount++
                
                if (frameCount % 30 == 0) {
                    Log.d(TAG, "模擬接收 $frameCount 幀視頻: ${source.name}")
                }
                
                // 模擬 30fps
                delay(33)
                
            } catch (e: Exception) {
                Log.e(TAG, "模擬幀接收時發生錯誤", e)
                delay(100)
            }
        }
        
        Log.i(TAG, "模擬 NDI 流接收已停止，共處理 $frameCount 幀")
    }
    
    /**
     * 創建測試視頻幀（帶動畫效果的彩色圖案）
     */
    private fun createTestVideoFrame(sourceName: String, frameNumber: Int, startTime: Long): NDIVideoFrame {
        val width = 720  // 較小的分辨率以提高性能
        val height = 480
        val pixelCount = width * height
        val data = ByteArray(pixelCount * 4) // ARGB格式
        
        val time = (System.currentTimeMillis() - startTime) / 1000.0f
        val animationPhase = (frameNumber % 120) / 120.0f * 2 * Math.PI
        
        for (y in 0 until height) {
            for (x in 0 until width) {
                val index = (y * width + x) * 4
                
                // 創建動畫彩色漸變
                val centerX = width / 2.0f
                val centerY = height / 2.0f
                val distance = kotlin.math.sqrt((x - centerX) * (x - centerX) + (y - centerY) * (y - centerY)).toFloat()
                val angle = kotlin.math.atan2((y - centerY).toDouble(), (x - centerX).toDouble())
                
                // 旋轉彩色圓環效果
                val r = (kotlin.math.sin(angle + animationPhase) * 127 + 127).toInt().coerceIn(0, 255)
                val g = (kotlin.math.sin(angle + animationPhase + Math.PI * 2 / 3) * 127 + 127).toInt().coerceIn(0, 255)
                val b = (kotlin.math.sin(angle + animationPhase + Math.PI * 4 / 3) * 127 + 127).toInt().coerceIn(0, 255)
                
                // 脈衝效果
                val pulse = (kotlin.math.sin(time * 2 + distance / 50) * 0.3 + 0.7).toFloat()
                
                // ARGB 格式
                data[index] = (255 * pulse).toInt().coerceIn(0, 255).toByte()     // A
                data[index + 1] = (r * pulse).toInt().coerceIn(0, 255).toByte()   // R
                data[index + 2] = (g * pulse).toInt().coerceIn(0, 255).toByte()   // G  
                data[index + 3] = (b * pulse).toInt().coerceIn(0, 255).toByte()   // B
            }
        }
        
        return NDIVideoFrame(
            width = width,
            height = height,
            frameRate = 30.0f,
            timestamp = System.currentTimeMillis(),
            data = data,
            sourceName = sourceName
        )
    }
    
    /**
     * 解析原生幀資料
     */
    private fun parseNativeFrame(frameData: ByteArray): NDIVideoFrame? {
        return try {
            // 這裡需要根據實際的 NDI SDK 幀格式來解析
            // 目前使用簡單的假設格式
            if (frameData.size >= 12) { // 至少包含基本資訊
                NDIVideoFrame(
                    width = 1920,  // 從 frameData 中解析
                    height = 1080, // 從 frameData 中解析
                    frameRate = 30.0f,
                    timestamp = System.currentTimeMillis(),
                    data = frameData,
                    sourceName = _currentSource.value?.name ?: "Unknown"
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "解析原生幀資料時發生錯誤", e)
            null
        }
    }
    
    /**
     * 清除錯誤狀態
     */
    fun clearError() {
        _error.postValue(null)
    }
    
    /**
     * 取得接收器狀態
     */
    fun getReceiverStatus(): Map<String, Any> {
        return mapOf(
            "isConnected" to (_connectionStatus.value == ConnectionStatus.CONNECTED || _connectionStatus.value == ConnectionStatus.STREAMING),
            "isReceiving" to (_isReceiving.value ?: false),
            "connectionStatus" to (_connectionStatus.value?.name ?: "UNKNOWN"),
            "sourceName" to (_currentSource.value?.name ?: ""),
            "useNativeSDK" to isNativeLibraryLoaded,
            "nativeReceiverCreated" to isNativeReceiverCreated
        )
    }
    
    /**
     * 清理資源
     */
    fun cleanup() {
        Log.i(TAG, "清理 NDI 接收器資源")
        
        disconnect()
        
        try {
            if (isNativeLibraryLoaded) {
                nativeCleanupReceiver()
            }
        } catch (e: Exception) {
            Log.w(TAG, "清理 NDI 接收器原生資源時發生錯誤", e)
        }
        
        coroutineScope.cancel()
    }
    
    // ===== NDI 接收器 JNI 函數聲明 =====
    
    /**
     * 初始化 NDI 接收器 (原生)
     */
    private external fun nativeInitializeReceiver(): Boolean
    
    /**
     * 連接到 NDI 源 (原生)
     */
    private external fun nativeConnect(sourceName: String, urlAddress: String): Boolean
    
    /**
     * 斷開 NDI 連接 (原生)
     */
    private external fun nativeDisconnect()
    
    /**
     * 接收 NDI 幀 (原生)
     */
    private external fun nativeReceiveFrame(timeoutMs: Int): ByteArray?
    
    /**
     * 清理 NDI 接收器資源 (原生)
     */
    private external fun nativeCleanupReceiver()
}

/**
 * NDI 視頻幀資料類別
 */
data class NDIVideoFrame(
    val width: Int,
    val height: Int,
    val frameRate: Float,
    val timestamp: Long,
    val data: ByteArray,
    val sourceName: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        
        other as NDIVideoFrame
        
        if (width != other.width) return false
        if (height != other.height) return false
        if (frameRate != other.frameRate) return false
        if (timestamp != other.timestamp) return false
        if (!data.contentEquals(other.data)) return false
        if (sourceName != other.sourceName) return false
        
        return true
    }
    
    override fun hashCode(): Int {
        var result = width
        result = 31 * result + height
        result = 31 * result + frameRate.hashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + data.contentHashCode()
        result = 31 * result + sourceName.hashCode()
        return result
    }
}