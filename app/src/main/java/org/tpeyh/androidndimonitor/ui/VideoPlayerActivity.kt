package org.tpeyh.androidndimonitor.ui

import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.widget.*
import android.app.Activity
import androidx.lifecycle.Observer
import org.tpeyh.androidndimonitor.R
import org.tpeyh.androidndimonitor.ndi.*

/**
 * NDI 視頻播放活動
 * 為 Android TV 優化的全屏視頻播放界面
 */
class VideoPlayerActivity : Activity() {
    
    private lateinit var videoRenderView: VideoRenderView
    private lateinit var loadingContainer: LinearLayout
    private lateinit var errorContainer: LinearLayout
    private lateinit var controlOverlay: LinearLayout
    private lateinit var infoOverlay: LinearLayout
    
    private lateinit var loadingText: TextView
    private lateinit var errorMessage: TextView
    private lateinit var sourceNameText: TextView
    private lateinit var sourceMachineText: TextView
    private lateinit var streamInfoText: TextView
    private lateinit var streamStatusText: TextView
    private lateinit var closeButton: ImageButton
    private lateinit var retryButton: Button
    
    private lateinit var videoPlayer: NDIVideoPlayer
    private var ndiSource: NDISource? = null
    
    companion object {
        private const val TAG = "VideoPlayerActivity"
        const val EXTRA_NDI_SOURCE = "extra_ndi_source"
        
        private const val CONTROLS_HIDE_DELAY = 5000L // 5 秒後隱藏控制項
    }
    
    private val hideControlsRunnable = Runnable {
        hideControls()
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            Log.i(TAG, "VideoPlayerActivity onCreate 開始")
            setContentView(R.layout.activity_video_player)
            Log.i(TAG, "setContentView 成功")
            
            // 隱藏系統 UI 以獲得全屏體驗
            hideSystemUI()
            Log.i(TAG, "hideSystemUI 成功")
            
            initViews()
            Log.i(TAG, "initViews 成功")
            
            initVideoPlayer()
            Log.i(TAG, "initVideoPlayer 成功")
            
            // 從 Intent 獲取 NDI 源信息
            ndiSource = intent.getSerializableExtra(EXTRA_NDI_SOURCE) as? NDISource
            Log.i(TAG, "取得 NDI 源: ${ndiSource?.name ?: "null"}")
            
            if (ndiSource != null) {
                setupSourceInfo(ndiSource!!)
                connectToSource(ndiSource!!)
                Log.i(TAG, "連接到源成功")
            } else {
                Log.e(TAG, "未收到 NDI 源信息")
                showError("無效的 NDI 源信息")
                finish()
            }
        } catch (e: Exception) {
            Log.e(TAG, "VideoPlayerActivity onCreate 錯誤", e)
            finish()
        }
    }
    
    /**
     * 初始化視圖
     */
    private fun initViews() {
        try {
            Log.d(TAG, "開始初始化視圖")
            videoRenderView = findViewById(R.id.video_render_view)
            Log.d(TAG, "videoRenderView 初始化完成")
            
            loadingContainer = findViewById(R.id.loading_container)
            errorContainer = findViewById(R.id.error_container)
            controlOverlay = findViewById(R.id.control_overlay)
            infoOverlay = findViewById(R.id.info_overlay)
            Log.d(TAG, "容器視圖初始化完成")
            
            loadingText = findViewById(R.id.loading_text)
            errorMessage = findViewById(R.id.error_message)
            sourceNameText = findViewById(R.id.source_name_text)
            sourceMachineText = findViewById(R.id.source_machine_text)
            streamInfoText = findViewById(R.id.stream_info_text)
            streamStatusText = findViewById(R.id.stream_status_text)
            closeButton = findViewById(R.id.close_button)
            retryButton = findViewById(R.id.error_retry_button)
            Log.d(TAG, "文字和按鈕視圖初始化完成")
            
            // 設置按鈕事件
            closeButton.setOnClickListener {
                disconnect()
            }
            
            retryButton.setOnClickListener {
                ndiSource?.let { connectToSource(it) }
            }
            
            // 點擊視頻區域顯示/隱藏控制項
            videoRenderView.setOnClickListener {
                toggleControls()
            }
            Log.d(TAG, "事件監聽器設置完成")
            
            // 初始顯示載入狀態
            showLoadingState()
            Log.d(TAG, "初始狀態設置完成")
        } catch (e: Exception) {
            Log.e(TAG, "初始化視圖時發生錯誤", e)
            throw e
        }
    }
    
    /**
     * 初始化視頻播放器
     */
    private fun initVideoPlayer() {
        videoPlayer = NDIVideoPlayer()
        
        // 初始化播放器
        if (!videoPlayer.initialize()) {
            Log.e(TAG, "NDI 視頻播放器初始化失敗")
            showError("視頻播放器初始化失敗")
            return
        }
        
        // 設置狀態回調
        videoPlayer.setStatusCallback(object : NDIVideoPlayer.StatusCallback {
            override fun onStatusChanged(status: ConnectionStatus) {
                Log.d(TAG, "播放器狀態變更: $status")
                updateConnectionStatus(status)
            }
            
            override fun onError(message: String) {
                Log.e(TAG, "播放器錯誤: $message")
                showError(message)
            }
            
            override fun onVideoFrame(frame: NDIVideoFrame) {
                Log.d(TAG, "接收到視頻幀: ${frame.width}x${frame.height}, ${frame.frameRate}fps, 數據: ${frame.data.size} 字節")
                updateVideoDisplay(frame)
            }
        })
        
        Log.d(TAG, "NDIVideoPlayer 已初始化並設置回調")
    }
    
    /**
     * 設置源信息
     */
    private fun setupSourceInfo(source: NDISource) {
        sourceNameText.text = source.name
        sourceMachineText.text = source.machineName
        streamInfoText.text = "${source.sourceType.name} • ${source.urlAddress}"
    }
    
    /**
     * 連接到源
     */
    private fun connectToSource(source: NDISource) {
        Log.i(TAG, "連接到 NDI 源: ${source.name}")
        loadingText.text = "正在連接到 ${source.name}..."
        videoRenderView.showLoading("正在連接到 ${source.name}...")
        showLoadingState()
        videoPlayer.connectToSource(source)
    }
    
    /**
     * 更新連接狀態
     */
    private fun updateConnectionStatus(status: ConnectionStatus) {
        runOnUiThread {
            when (status) {
                ConnectionStatus.CONNECTING -> {
                    loadingText.text = "連接中..."
                    streamStatusText.text = "連接中"
                    videoRenderView.showLoading("連接中...")
                    showLoadingState()
                }
                ConnectionStatus.CONNECTED -> {
                    loadingText.text = "已連接"
                    streamStatusText.text = "已連接"
                    ndiSource?.let { 
                        videoRenderView.showConnected(it.name)
                    }
                    showConnectedState()
                }
                ConnectionStatus.STREAMING -> {
                    streamStatusText.text = "串流中"
                    showStreamingState()
                    scheduleHideControls()
                }
                ConnectionStatus.CONNECTION_FAILED -> {
                    streamStatusText.text = "連接失敗"
                    videoRenderView.showError("無法連接到 NDI 源")
                    showErrorState("連接失敗")
                }
                ConnectionStatus.NETWORK_ERROR -> {
                    streamStatusText.text = "網路錯誤" 
                    videoRenderView.showError("網路連接錯誤")
                    showErrorState("網路錯誤")
                }
                ConnectionStatus.DISCONNECTED -> {
                    streamStatusText.text = "已斷開"
                    showLoadingState()
                }
                else -> {
                    streamStatusText.text = "未知狀態"
                    showLoadingState()
                }
            }
        }
    }
    
    /**
     * 更新視頻顯示
     */
    private fun updateVideoDisplay(frame: NDIVideoFrame) {
        try {
            runOnUiThread {
                Log.d(TAG, "開始渲染視頻幀: ${frame.width}x${frame.height}")
                
                // 使用新的 NDIVideoFrame 對象
                videoRenderView.renderNDIFrame(frame)
                
                // 更新流信息
                val frameInfo = "${frame.width}x${frame.height} @ ${frame.frameRate.toInt()}fps"
                streamInfoText.text = "${ndiSource?.sourceType?.name ?: "NDI"} • $frameInfo"
                
                // 更新狀態為串流中
                if (streamStatusText.text != "串流中") {
                    streamStatusText.text = "串流中"
                    Log.i(TAG, "視頻串流狀態更新為: 串流中")
                }
                
                Log.d(TAG, "完成視頻幀渲染: $frameInfo, 來源: ${frame.sourceName}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "視頻幀處理錯誤", e)
        }
    }
    
    /**
     * 顯示載入狀態
     */
    private fun showLoadingState() {
        loadingContainer.visibility = View.VISIBLE
        errorContainer.visibility = View.GONE
        controlOverlay.visibility = View.GONE
        infoOverlay.visibility = View.GONE
    }
    
    /**
     * 顯示連接狀態
     */
    private fun showConnectedState() {
        loadingContainer.visibility = View.GONE
        errorContainer.visibility = View.GONE
        controlOverlay.visibility = View.VISIBLE
        infoOverlay.visibility = View.VISIBLE
    }
    
    /**
     * 顯示串流狀態
     */
    private fun showStreamingState() {
        loadingContainer.visibility = View.GONE
        errorContainer.visibility = View.GONE
        controlOverlay.visibility = View.VISIBLE
        infoOverlay.visibility = View.VISIBLE
    }
    
    /**
     * 顯示錯誤狀態
     */
    private fun showErrorState(message: String) {
        loadingContainer.visibility = View.GONE
        errorContainer.visibility = View.VISIBLE
        controlOverlay.visibility = View.GONE
        infoOverlay.visibility = View.GONE
        
        errorMessage.text = message
    }
    
    /**
     * 顯示錯誤
     */
    private fun showError(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            Log.e(TAG, message)
        }
    }
    
    /**
     * 斷開連接
     */
    private fun disconnect() {
        Log.i(TAG, "用戶選擇斷開連接")
        videoPlayer.disconnect()
        finish()
    }
    
    /**
     * 顯示控制項
     */
    private fun showControls() {
        controlOverlay.visibility = View.VISIBLE
        infoOverlay.visibility = View.VISIBLE
        controlOverlay.animate()
            .alpha(1f)
            .setDuration(300)
            .start()
        infoOverlay.animate()
            .alpha(1f)
            .setDuration(300)
            .start()
    }
    
    /**
     * 隱藏控制項
     */
    private fun hideControls() {
        controlOverlay.animate()
            .alpha(0f)
            .setDuration(300)
            .withEndAction {
                controlOverlay.visibility = View.GONE
            }
            .start()
        infoOverlay.animate()
            .alpha(0f)
            .setDuration(300)
            .withEndAction {
                infoOverlay.visibility = View.GONE
            }
            .start()
    }
    
    /**
     * 切換控制項顯示
     */
    private fun toggleControls() {
        if (controlOverlay.visibility == View.VISIBLE) {
            hideControls()
        } else {
            showControls()
            scheduleHideControls()
        }
    }
    
    /**
     * 排程隱藏控制項
     */
    private fun scheduleHideControls() {
        controlOverlay.removeCallbacks(hideControlsRunnable)
        controlOverlay.postDelayed(hideControlsRunnable, CONTROLS_HIDE_DELAY)
    }
    
    /**
     * 隱藏系統 UI
     */
    private fun hideSystemUI() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_FULLSCREEN
        )
    }
    
    /**
     * 處理按鍵事件（Android TV 遙控器）
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER -> {
                toggleControls()
                return true
            }
            KeyEvent.KEYCODE_BACK -> {
                disconnect()
                return true
            }
        }
        
        // 任何按鍵都顯示控制項
        if (controlOverlay.visibility != View.VISIBLE) {
            showControls()
            scheduleHideControls()
        }
        
        return super.onKeyDown(keyCode, event)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        try {
            if (::controlOverlay.isInitialized) {
                controlOverlay.removeCallbacks(hideControlsRunnable)
            }
            if (::videoPlayer.isInitialized) {
                videoPlayer.cleanup()
            }
            if (::videoRenderView.isInitialized) {
                videoRenderView.cleanup()
            }
        } catch (e: Exception) {
            Log.e(TAG, "清理資源時發生錯誤", e)
        }
    }
    
    override fun onPause() {
        super.onPause()
        // 在暫停時保持連接，用戶可能只是短暫離開
    }
    
    override fun onResume() {
        super.onResume()
        hideSystemUI()
        
        // 如果連接中斷，嘗試重新連接
        if (videoPlayer.getCurrentStatus() == ConnectionStatus.CONNECTION_FAILED ||
            videoPlayer.getCurrentStatus() == ConnectionStatus.DISCONNECTED) {
            ndiSource?.let { connectToSource(it) }
        }
    }
}