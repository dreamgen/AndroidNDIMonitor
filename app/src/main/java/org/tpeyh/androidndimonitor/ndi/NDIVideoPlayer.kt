package org.tpeyh.androidndimonitor.ndi

import android.util.Log
import kotlinx.coroutines.*

/**
 * NDI 視頻播放器（使用 NDI SDK）
 * 用於接收和播放 NDI 視頻流
 */
class NDIVideoPlayer {
    
    companion object {
        private const val TAG = "NDIVideoPlayer"
    }
    
    // 回調接口
    interface StatusCallback {
        fun onStatusChanged(status: ConnectionStatus)
        fun onError(message: String)
        fun onVideoFrame(frame: NDIVideoFrame)
    }
    
    private var statusCallback: StatusCallback? = null
    private var currentStatus = ConnectionStatus.DISCONNECTED
    
    private var ndiReceiver: NDIReceiver? = null
    private var currentSource: NDISource? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    /**
     * 設置狀態回調
     */
    fun setStatusCallback(callback: StatusCallback?) {
        statusCallback = callback
    }
    
    /**
     * 取得當前狀態
     */
    fun getCurrentStatus(): ConnectionStatus = currentStatus
    
    /**
     * 更新狀態
     */
    private fun updateStatus(status: ConnectionStatus) {
        currentStatus = status
        statusCallback?.onStatusChanged(status)
    }
    
    /**
     * 報告錯誤
     */
    private fun reportError(message: String) {
        statusCallback?.onError(message)
    }
    
    /**
     * 初始化播放器
     */
    fun initialize(): Boolean {
        return try {
            Log.i(TAG, "初始化 NDI 視頻播放器...")
            
            // 創建 NDI 接收器
            ndiReceiver = NDIReceiver().apply {
                if (!initialize()) {
                    Log.w(TAG, "NDI 接收器初始化失敗")
                    return false
                }
            }
            
            // 設置接收器觀察器
            setupReceiverObservers()
            
            Log.i(TAG, "✓ NDI 視頻播放器初始化成功")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "NDI 視頻播放器初始化失敗", e)
            reportError("播放器初始化失敗: ${e.message}")
            false
        }
    }
    
    /**
     * 設置接收器觀察器
     */
    private fun setupReceiverObservers() {
        val receiver = ndiReceiver ?: return
        
        // 觀察連接狀態
        coroutineScope.launch {
            receiver.connectionStatus.observeForever { status ->
                if (status != null) {
                    updateStatus(status)
                }
            }
        }
        
        // 觀察視頻幀
        coroutineScope.launch {
            receiver.videoFrame.observeForever { frame ->
                if (frame != null) {
                    statusCallback?.onVideoFrame(frame)
                }
            }
        }
        
        // 觀察錯誤
        coroutineScope.launch {
            receiver.error.observeForever { error ->
                if (!error.isNullOrEmpty()) {
                    reportError(error)
                }
            }
        }
    }
    
    /**
     * 連接到 NDI 源
     */
    fun connectToSource(ndiSource: NDISource) {
        Log.i(TAG, "嘗試連接到 NDI 源: ${ndiSource.name}")
        
        if (currentStatus == ConnectionStatus.CONNECTING ||
            currentStatus == ConnectionStatus.STREAMING) {
            Log.w(TAG, "已在連接或串流中，先斷開現有連接")
            disconnect()
        }
        
        val receiver = ndiReceiver
        if (receiver == null) {
            reportError("NDI 接收器未初始化")
            return
        }
        
        currentSource = ndiSource
        
        coroutineScope.launch {
            try {
                // 連接到 NDI 源
                if (receiver.connect(ndiSource)) {
                    Log.i(TAG, "✓ 成功連接到 NDI 源: ${ndiSource.name}")
                    
                    // 開始接收視頻流
                    receiver.startReceiving()
                    
                } else {
                    throw Exception("無法連接到 NDI 源")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "連接 NDI 源失敗", e)
                updateStatus(ConnectionStatus.CONNECTION_FAILED)
                reportError("連接失敗: ${e.message}")
            }
        }
    }
    
    /**
     * 開始播放
     */
    fun startPlaying() {
        val receiver = ndiReceiver
        if (receiver == null) {
            reportError("NDI 接收器未初始化")
            return
        }
        
        if (currentSource == null) {
            reportError("未連接到 NDI 源")
            return
        }
        
        Log.i(TAG, "開始播放 NDI 視頻: ${currentSource?.name}")
        receiver.startReceiving()
    }
    
    /**
     * 停止播放
     */
    fun stopPlaying() {
        Log.i(TAG, "停止播放 NDI 視頻")
        ndiReceiver?.stopReceiving()
    }
    
    /**
     * 暫停播放
     */
    fun pausePlaying() {
        Log.i(TAG, "暫停播放 NDI 視頻")
        ndiReceiver?.stopReceiving()
    }
    
    /**
     * 恢復播放
     */
    fun resumePlaying() {
        Log.i(TAG, "恢復播放 NDI 視頻")
        ndiReceiver?.startReceiving()
    }
    
    /**
     * 斷開連接
     */
    fun disconnect() {
        Log.i(TAG, "斷開 NDI 連接")
        
        ndiReceiver?.disconnect()
        currentSource = null
        updateStatus(ConnectionStatus.DISCONNECTED)
    }
    
    /**
     * 取得當前連接的源
     */
    fun getCurrentSource(): NDISource? = currentSource
    
    /**
     * 取得播放器統計資訊
     */
    fun getPlayerStats(): Map<String, Any> {
        val receiverStats = ndiReceiver?.getReceiverStatus() ?: emptyMap()
        return mapOf(
            "currentSource" to (currentSource?.name ?: ""),
            "connectionStatus" to currentStatus.name,
            "isPlaying" to (currentStatus == ConnectionStatus.STREAMING)
        ) + receiverStats
    }
    
    /**
     * 檢查是否正在播放
     */
    fun isPlaying(): Boolean {
        return currentStatus == ConnectionStatus.STREAMING
    }
    
    /**
     * 檢查是否已連接
     */
    fun isConnected(): Boolean {
        return currentStatus == ConnectionStatus.CONNECTED || 
               currentStatus == ConnectionStatus.STREAMING
    }
    
    /**
     * 清理資源
     */
    fun cleanup() {
        Log.i(TAG, "清理 NDI 視頻播放器資源")
        
        disconnect()
        
        ndiReceiver?.cleanup()
        ndiReceiver = null
        
        coroutineScope.cancel()
    }
}