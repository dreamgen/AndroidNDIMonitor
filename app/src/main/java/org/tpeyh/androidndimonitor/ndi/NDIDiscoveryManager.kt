package org.tpeyh.androidndimonitor.ndi

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.*
import me.walkerknapp.devolay.Devolay
import me.walkerknapp.devolay.DevolayFinder
import me.walkerknapp.devolay.DevolaySource
import java.util.concurrent.ConcurrentHashMap

/**
 * NDI 源發現與管理器（簡化版）
 * 負責自動掃描網路上的 NDI 源並提供即時狀態監控
 */
class NDIDiscoveryManager private constructor() {
    
    private val _sources = MutableLiveData<List<NDISource>>(emptyList())
    val sources: LiveData<List<NDISource>> = _sources
    
    private val _isScanning = MutableLiveData(false)
    val isScanning: LiveData<Boolean> = _isScanning
    
    private val _connectionError = MutableLiveData<String?>()
    val connectionError: LiveData<String?> = _connectionError
    
    private val _isInitialized = MutableLiveData(false)
    val isInitialized: LiveData<Boolean> = _isInitialized
    
    private var finder: DevolayFinder? = null
    private var discoveryJob: Job? = null
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // 源狀態追蹤
    private val sourceStatusMap = ConcurrentHashMap<String, NDISourceStatus>()
    
    companion object {
        private const val TAG = "NDIDiscoveryManager"
        private const val SCAN_INTERVAL_MS = 5000L // 5 秒掃描間隔
        private const val SOURCE_TIMEOUT_MS = 15000L // 15 秒無響應視為離線
        
        @Volatile
        private var instance: NDIDiscoveryManager? = null
        
        fun getInstance(): NDIDiscoveryManager {
            return instance ?: synchronized(this) {
                instance ?: NDIDiscoveryManager().also { instance = it }
            }
        }
    }
    
    /**
     * 初始化 NDI SDK
     */
    fun initialize(): Boolean {
        return try {
            Log.i(TAG, "開始初始化 NDI SDK...")
            
            // 嘗試載入 Devolay 函式庫
            try {
                Devolay.loadLibraries()
            } catch (e: Exception) {
                val error = "無法載入 NDI 函式庫: ${e.message}"
                _connectionError.postValue(error)
                Log.e(TAG, error)
                return false
            }
            
            finder = DevolayFinder()
            _isInitialized.postValue(true)
            _connectionError.postValue(null)
            
            Log.i(TAG, "NDI SDK 初始化成功")
            true
            
        } catch (e: Exception) {
            val error = "NDI SDK 初始化失敗: ${e.message}"
            _connectionError.postValue(error)
            Log.e(TAG, error, e)
            false
        }
    }
    
    /**
     * 開始掃描 NDI 源
     */
    fun startScanning() {
        if (_isScanning.value == true) {
            Log.d(TAG, "掃描已在進行中")
            return
        }
        
        if (!_isInitialized.value!!) {
            Log.w(TAG, "NDI SDK 未初始化，無法開始掃描")
            return
        }
        
        Log.i(TAG, "開始掃描 NDI 源...")
        _isScanning.postValue(true)
        
        discoveryJob = coroutineScope.launch {
            try {
                while (isActive) {
                    scanForSources()
                    delay(SCAN_INTERVAL_MS)
                }
            } catch (e: Exception) {
                Log.e(TAG, "掃描過程中發生錯誤", e)
                _connectionError.postValue("掃描錯誤: ${e.message}")
            } finally {
                _isScanning.postValue(false)
            }
        }
    }
    
    /**
     * 停止掃描
     */
    fun stopScanning() {
        Log.i(TAG, "停止掃描 NDI 源")
        discoveryJob?.cancel()
        _isScanning.postValue(false)
    }
    
    /**
     * 執行一次 NDI 源掃描
     */
    private suspend fun scanForSources() {
        try {
            val finder = this.finder ?: return
            val currentTime = System.currentTimeMillis()
            
            // 等待 NDI 源發現（超時 1 秒）
            finder.waitForSources(1000)
            val devolaySources = finder.currentSources
            
            if (devolaySources != null && devolaySources.isNotEmpty()) {
                val discoveredSources = processSources(devolaySources, currentTime)
                _sources.postValue(discoveredSources)
                
                Log.d(TAG, "發現 ${devolaySources.size} 個 NDI 源")
            } else {
                // 檢查現有源是否超時
                checkSourceTimeouts(currentTime)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "掃描 NDI 源時發生錯誤", e)
            _connectionError.postValue("掃描錯誤: ${e.message}")
        }
    }
    
    /**
     * 處理發現的 NDI 源
     */
    private fun processSources(devolaySources: Array<DevolaySource>, currentTime: Long): List<NDISource> {
        val activeSources = mutableSetOf<String>()
        
        return devolaySources.mapNotNull { source ->
            try {
                val sourceKey = source.sourceName
                activeSources.add(sourceKey)
                
                // 更新或創建源狀態
                val status = sourceStatusMap.getOrPut(sourceKey) {
                    NDISourceStatus(
                        isOnline = true,
                        firstSeen = currentTime,
                        lastSeen = currentTime
                    )
                }
                
                status.updateSeen()
                
                // 創建 NDI 源對象
                NDISource(
                    name = source.sourceName,
                    machineName = "本地網路", // 簡化處理
                    urlAddress = source.sourceName, // 簡化處理
                    sourceType = determineSourceType(source.sourceName),
                    connectionStatus = ConnectionStatus.DISCONNECTED,
                    lastSeenTime = currentTime,
                    isOnline = true
                )
                
            } catch (e: Exception) {
                Log.w(TAG, "處理 NDI 源時發生錯誤: ${source.sourceName}", e)
                null
            }
        }.also { sources ->
            // 標記未見到的源為離線
            markInactiveSources(activeSources, currentTime)
        }
    }
    
    /**
     * 根據源名稱判斷 NDI 類型
     */
    private fun determineSourceType(sourceName: String): NDISourceType {
        return when {
            sourceName.contains("HX3", ignoreCase = true) -> NDISourceType.NDI_HX3
            sourceName.contains("HX2", ignoreCase = true) -> NDISourceType.NDI_HX2
            else -> NDISourceType.NDI
        }
    }
    
    /**
     * 標記非活動源為離線
     */
    private fun markInactiveSources(activeSources: Set<String>, currentTime: Long) {
        sourceStatusMap.keys.forEach { sourceKey ->
            if (!activeSources.contains(sourceKey)) {
                sourceStatusMap[sourceKey]?.markOffline()
            }
        }
    }
    
    /**
     * 檢查源超時
     */
    private fun checkSourceTimeouts(currentTime: Long) {
        val timeoutSources = sourceStatusMap.filterValues { status ->
            status.isOfflineTooLong(SOURCE_TIMEOUT_MS / 1000)
        }
        
        if (timeoutSources.isNotEmpty()) {
            timeoutSources.forEach { (sourceKey, _) ->
                sourceStatusMap[sourceKey]?.markOffline()
            }
            
            // 更新源列表，移除超時源
            val currentSources = _sources.value ?: emptyList()
            val activeSources = currentSources.filter { source ->
                !timeoutSources.containsKey(source.name)
            }
            
            if (activeSources.size != currentSources.size) {
                _sources.postValue(activeSources)
                Log.d(TAG, "移除 ${timeoutSources.size} 個超時的 NDI 源")
            }
        }
    }
    
    /**
     * 手動重新整理源列表
     */
    fun refresh() {
        Log.i(TAG, "手動重新整理 NDI 源列表")
        coroutineScope.launch {
            scanForSources()
        }
    }
    
    /**
     * 清除錯誤狀態
     */
    fun clearError() {
        _connectionError.postValue(null)
    }
    
    /**
     * 釋放資源
     */
    fun cleanup() {
        Log.i(TAG, "清理 NDI 發現管理器")
        stopScanning()
        finder?.close()
        finder = null
        sourceStatusMap.clear()
        _sources.postValue(emptyList())
        _isInitialized.postValue(false)
        coroutineScope.cancel()
    }
    
    /**
     * 取得源統計信息
     */
    fun getSourceStats(): Map<String, Any> {
        return mapOf(
            "totalDiscovered" to sourceStatusMap.size,
            "currentlyOnline" to sourceStatusMap.values.count { it.isOnline },
            "isScanning" to (_isScanning.value ?: false),
            "isInitialized" to (_isInitialized.value ?: false)
        )
    }
}