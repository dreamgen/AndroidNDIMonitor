package org.tpeyh.androidndimonitor.ndi

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.*
import java.net.*
import java.util.concurrent.ConcurrentHashMap

/**
 * NDI 源發現與管理器 (使用 NDI SDK)
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
    
    private val _scanStatus = MutableLiveData<String>()
    val scanStatus: LiveData<String> = _scanStatus
    
    private val _scanProgress = MutableLiveData<Pair<Int, Int>>() // (current, total)
    val scanProgress: LiveData<Pair<Int, Int>> = _scanProgress
    
    private var discoveryJob: Job? = null
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // 源狀態追蹤
    private val sourceStatusMap = ConcurrentHashMap<String, NDISourceStatus>()
    
    // 相容模式標誌
    private var isCompatibilityMode = false
    
    // 已發現的源緩存
    private val discoveredSources = mutableListOf<NDISource>()
    private var lastScanTime = 0L
    private val SCAN_COOLDOWN_MS = 30000L // 30 秒內不重複掃描
    
    // NDI JNI 包裝器狀態
    private var isNativeLibraryLoaded = false
    
    companion object {
        private const val TAG = "NDIDiscoveryManager"
        private const val SCAN_INTERVAL_MS = 3000L // 3 秒掃描間隔
        private const val SOURCE_TIMEOUT_MS = 12000L // 12 秒無響應視為離線
        
        @Volatile
        private var instance: NDIDiscoveryManager? = null
        
        fun getInstance(): NDIDiscoveryManager {
            return instance ?: synchronized(this) {
                instance ?: NDIDiscoveryManager().also { instance = it }
            }
        }
        
        // 載入原生函式庫
        init {
            try {
                System.loadLibrary("ndi_android")
                Log.i(TAG, "✓ 成功載入 NDI Android 原生函式庫")
            } catch (e: UnsatisfiedLinkError) {
                Log.w(TAG, "⚠️ 無法載入 NDI 原生函式庫: ${e.message}")
                Log.w(TAG, "將使用網路掃描備用模式")
            } catch (e: Exception) {
                Log.e(TAG, "載入 NDI 原生函式庫時發生未知錯誤", e)
            }
        }
    }
    
    /**
     * 初始化 NDI SDK
     */
    fun initialize(): Boolean {
        return try {
            Log.i(TAG, "開始初始化 NDI SDK...")
            logDeviceInfo()
            
            // 首先嘗試使用原生 NDI SDK
            try {
                if (nativeInitialize()) {
                    Log.i(TAG, "✓ NDI SDK 原生初始化成功")
                    val version = nativeGetVersion()
                    Log.i(TAG, "NDI SDK 版本: $version")
                    _isInitialized.postValue(true)
                    _connectionError.postValue(null)
                    isNativeLibraryLoaded = true
                    return true
                } else {
                    Log.w(TAG, "NDI SDK 原生初始化失敗，嘗試相容模式")
                }
            } catch (e: UnsatisfiedLinkError) {
                Log.w(TAG, "NDI 原生函式庫不可用: ${e.message}")
            } catch (e: Exception) {
                Log.e(TAG, "NDI 原生初始化異常: ${e.message}", e)
            }
            
            // 如果原生 NDI 不可用，使用相容模式
            Log.i(TAG, "啟用網路掃描相容模式")
            initializeCompatibilityMode()
            true
            
        } catch (e: Exception) {
            val error = "NDI SDK 初始化失敗: ${e.message}"
            Log.e(TAG, "✗ $error", e)
            _connectionError.postValue(error)
            
            // 最終備用方案
            initializeCompatibilityMode()
            true
        }
    }
    
    /**
     * 記錄設備資訊用於除錯
     */
    private fun logDeviceInfo() {
        try {
            Log.i(TAG, "設備資訊:")
            Log.i(TAG, "  型號: ${android.os.Build.MODEL}")
            Log.i(TAG, "  製造商: ${android.os.Build.MANUFACTURER}")  
            Log.i(TAG, "  CPU ABI: ${android.os.Build.CPU_ABI}")
            Log.i(TAG, "  CPU ABI2: ${android.os.Build.CPU_ABI2}")
            Log.i(TAG, "  支援的 ABI: ${android.os.Build.SUPPORTED_ABIS?.joinToString(", ") ?: "未知"}")
        } catch (e: Exception) {
            Log.w(TAG, "無法記錄設備資訊: ${e.message}")
        }
    }
    
    /**
     * 初始化相容模式（網路掃描）
     */
    private fun initializeCompatibilityMode() {
        Log.i(TAG, "啟動相容模式 - 使用網路掃描進行 NDI 發現")
        isCompatibilityMode = true
        _isInitialized.postValue(true)
        _connectionError.postValue(null)
        Log.i(TAG, "相容模式已啟用，將使用網路服務發現")
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
        
        // 根據不同模式選擇掃描策略
        when {
            isNativeLibraryLoaded -> {
                Log.i(TAG, "使用 NDI SDK 原生掃描")
                startNativeScanning()
            }
            isCompatibilityMode -> {
                Log.i(TAG, "使用相容模式網路掃描")
                startCompatibilityScanning()
            }
            else -> {
                Log.d(TAG, "使用模擬掃描模式") 
                startMockScanning()
            }
        }
    }
    
    /**
     * 開始原生 NDI SDK 掃描
     */
    private fun startNativeScanning() {
        discoveryJob = coroutineScope.launch {
            try {
                Log.i(TAG, "啟動 NDI SDK 原生掃描協程")
                while (isActive) {
                    scanNativeSources()
                    delay(SCAN_INTERVAL_MS)
                }
            } catch (e: Exception) {
                Log.e(TAG, "原生掃描過程中發生錯誤", e)
                _connectionError.postValue("NDI SDK 掃描錯誤: ${e.message}")
            } finally {
                _isScanning.postValue(false)
                Log.i(TAG, "NDI SDK 原生掃描協程已結束")
            }
        }
    }
    
    /**
     * 執行原生 NDI 源掃描
     */
    private suspend fun scanNativeSources() {
        try {
            Log.d(TAG, "開始原生 NDI 源掃描...")
            
            // 更新掃描狀態
            _scanStatus.postValue("正在使用 NDI SDK 掃描...")
            
            // 呼叫原生函數掃描源
            val sourceNames = nativeScanSources(3000) // 3秒超時
            
            if (sourceNames.isNotEmpty()) {
                val currentTime = System.currentTimeMillis()
                val ndiSources = sourceNames.mapIndexed { index, sourceName ->
                    val (name, machineInfo) = parseNDISourceName(sourceName)
                    val (machineName, urlAddress) = parseSourceInfo(machineInfo)
                    
                    NDISource(
                        name = name,
                        machineName = machineName,
                        urlAddress = urlAddress,
                        sourceType = determineSourceType(sourceName),
                        connectionStatus = ConnectionStatus.DISCONNECTED,
                        lastSeenTime = currentTime,
                        isOnline = true,
                        description = "透過 NDI SDK 發現"
                    )
                }
                
                _sources.postValue(ndiSources)
                _scanStatus.postValue("✓ NDI SDK 發現 ${ndiSources.size} 個源")
                
                Log.i(TAG, "✓ NDI SDK 掃描成功！發現 ${ndiSources.size} 個 NDI 源:")
                ndiSources.forEach { source ->
                    Log.i(TAG, "  📺 ${source.name} @ ${source.urlAddress}")
                }
            } else {
                Log.d(TAG, "NDI SDK 掃描未發現任何源")
                _scanStatus.postValue("未發現 NDI 源")
                
                // 如果沒有發現源，顯示掃描狀態
                if (_sources.value.isNullOrEmpty()) {
                    val statusSources = createScanningStatusSources(System.currentTimeMillis())
                    _sources.postValue(statusSources)
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "原生 NDI 源掃描時發生錯誤", e)
            _connectionError.postValue("NDI SDK 掃描錯誤: ${e.message}")
        }
    }
    
    /**
     * 解析 NDI 源名稱
     */
    private fun parseNDISourceName(fullSourceName: String): Pair<String, String> {
        // NDI 源格式通常是 "SOURCE_NAME (MACHINE_INFO)"
        val regex = """^(.+?)\s*\(([^)]+)\)$""".toRegex()
        val matchResult = regex.find(fullSourceName)
        
        return if (matchResult != null) {
            val sourceName = matchResult.groupValues[1].trim()
            val machineInfo = matchResult.groupValues[2].trim()
            Pair(sourceName, machineInfo)
        } else {
            // 如果無法解析，使用完整名稱
            Pair(fullSourceName, "未知機器")
        }
    }
    
    /**
     * 開始相容模式掃描
     */
    private fun startCompatibilityScanning() {
        val currentTime = System.currentTimeMillis()
        
        // 如果有已發現的源且在冷卻時間內，直接返回已發現的源
        if (discoveredSources.isNotEmpty() && (currentTime - lastScanTime) < SCAN_COOLDOWN_MS) {
            Log.d(TAG, "使用已緩存的 NDI 源 (${discoveredSources.size} 個)")
            _sources.postValue(discoveredSources.toList())
            _isScanning.postValue(false)
            return
        }
        
        discoveryJob = coroutineScope.launch {
            try {
                Log.i(TAG, "啟動相容模式掃描協程")
                scanNetworkForNDI()
                lastScanTime = System.currentTimeMillis()
            } catch (e: Exception) {
                Log.e(TAG, "相容模式掃描過程中發生錯誤", e)
                _connectionError.postValue("網路掃描錯誤: ${e.message}")
            } finally {
                _isScanning.postValue(false)
                Log.i(TAG, "相容模式掃描協程已結束")
            }
        }
    }
    
    /**
     * 開始模擬掃描（用於測試）
     */
    private fun startMockScanning() {
        discoveryJob = coroutineScope.launch {
            var counter = 0
            try {
                while (isActive) {
                    // 模擬源狀態變化
                    updateMockSourcesStatus(counter)
                    counter++
                    delay(SCAN_INTERVAL_MS)
                }
            } catch (e: Exception) {
                Log.e(TAG, "模擬掃描過程中發生錯誤", e)
            } finally {
                _isScanning.postValue(false)
            }
        }
    }
    
    /**
     * 更新模擬源狀態
     */
    private fun updateMockSourcesStatus(counter: Int) {
        val mockSources = listOf(
            NDISource(
                name = "測試 OBS PGM",
                machineName = "開發機器",
                urlAddress = "192.168.1.100:5960",
                sourceType = NDISourceType.NDI,
                connectionStatus = ConnectionStatus.DISCONNECTED,
                lastSeenTime = System.currentTimeMillis(),
                isOnline = true,
                description = "模擬 NDI 源用於測試"
            ),
            NDISource(
                name = "測試 OBS PREVIEW",
                machineName = "工作站",
                urlAddress = "192.168.1.101:5961", 
                sourceType = NDISourceType.NDI_HX2,
                connectionStatus = ConnectionStatus.DISCONNECTED,
                lastSeenTime = System.currentTimeMillis(),
                isOnline = true,
                description = "模擬 NDI 源用於測試"
            )
        )
        
        _sources.postValue(mockSources)
        _scanStatus.postValue("模擬掃描：發現 ${mockSources.size} 個測試源")
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
     * 強制重新掃描
     */
    fun refresh() {
        Log.i(TAG, "強制重新掃描 NDI 源")
        discoveredSources.clear()
        lastScanTime = 0L
        startScanning()
    }
    
    /**
     * 網路掃描 NDI 源（相容模式實作）
     */
    private suspend fun scanNetworkForNDI() {
        try {
            Log.d(TAG, "開始網路掃描 NDI 源...")
            val currentTime = System.currentTimeMillis()
            
            discoveredSources.clear()
            
            val localIP = getLocalIPAddress()
            if (localIP != null) {
                Log.i(TAG, "本機 IP: $localIP")
                val networkPrefix = getNetworkPrefix(localIP)
                Log.i(TAG, "掃描網段: $networkPrefix.*")
                
                _scanStatus.postValue("正在掃描網段: $networkPrefix.*")
                
                val scanTargets = buildScanTargets(networkPrefix, localIP)
                _scanProgress.postValue(Pair(0, scanTargets.size))
                
                scanTargets.forEachIndexed { index, host ->
                    _scanStatus.postValue("正在掃描: $host (${index + 1}/${scanTargets.size})")
                    _scanProgress.postValue(Pair(index + 1, scanTargets.size))
                    
                    val sources = scanHostForAllNDI(host, currentTime)
                    if (sources.isNotEmpty()) {
                        discoveredSources.addAll(sources)
                        Log.i(TAG, "✓ 在 $host 發現 ${sources.size} 個 NDI 服務")
                        _sources.postValue(discoveredSources.toList())
                        _scanStatus.postValue("✓ 發現 ${sources.size} 個 NDI 源")
                    }
                }
                
                _scanStatus.postValue("掃描完成，共檢查 ${scanTargets.size} 個主機")
            } else {
                _scanStatus.postValue("無法取得本機 IP 地址")
            }
            
            if (discoveredSources.isNotEmpty()) {
                _sources.postValue(discoveredSources)
                Log.i(TAG, "✓ 網路掃描成功！發現 ${discoveredSources.size} 個 NDI 源")
            } else {
                Log.w(TAG, "網路掃描未發現任何 NDI 源")
                val testSources = createScanningStatusSources(currentTime)
                _sources.postValue(testSources)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "網路掃描 NDI 源時發生錯誤", e)
            _connectionError.postValue("網路掃描錯誤: ${e.message}")
        }
    }
    
    // ... [網路掃描相關的輔助函數保持不變] ...
    
    /**
     * 取得本機 IP 地址
     */
    private fun getLocalIPAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val intf = interfaces.nextElement()
                if (!intf.isLoopback && intf.isUp) {
                    val addresses = intf.inetAddresses
                    while (addresses.hasMoreElements()) {
                        val addr = addresses.nextElement()
                        if (!addr.isLoopbackAddress && addr is Inet4Address) {
                            return addr.hostAddress
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "無法取得本機 IP 地址", e)
        }
        return null
    }
    
    /**
     * 取得網路前綴
     */
    private fun getNetworkPrefix(ip: String): String {
        val parts = ip.split(".")
        return if (parts.size >= 3) {
            "${parts[0]}.${parts[1]}.${parts[2]}"
        } else {
            "192.168.1"
        }
    }
    
    /**
     * 建立掃描目標列表
     */
    private fun buildScanTargets(networkPrefix: String, localIP: String): List<String> {
        val scanTargets = mutableListOf<String>()
        
        // 優先掃描已知的主機
        scanTargets.addAll(listOf(
            "${networkPrefix}.1", "${networkPrefix}.21", "${networkPrefix}.58", 
            "${networkPrefix}.61", "${networkPrefix}.100", "${networkPrefix}.101", 
            "${networkPrefix}.102", "${networkPrefix}.110", "${networkPrefix}.111"
        ))
        
        // 掃描本機附近的主機
        val localLastOctet = localIP.split(".").lastOrNull()?.toIntOrNull()
        if (localLastOctet != null) {
            for (offset in -5..5) {
                val targetOctet = localLastOctet + offset
                if (targetOctet in 1..254 && targetOctet != localLastOctet) {
                    scanTargets.add("$networkPrefix.$targetOctet")
                }
            }
        }
        
        return scanTargets.distinct().sorted()
    }
    
    /**
     * 掃描單一主機的所有 NDI 服務
     */
    private suspend fun scanHostForAllNDI(ip: String, currentTime: Long): List<NDISource> {
        return try {
            withTimeout(1000) {
                val ndiPorts = listOf(5960, 5961, 5962, 5963, 80, 8080)
                val foundSources = mutableListOf<NDISource>()
                
                for (port in ndiPorts) {
                    try {
                        val socket = Socket()
                        socket.connect(InetSocketAddress(ip, port), 300)
                        socket.close()
                        
                        val hostName = getHostName(ip)
                        val sourceName = generateSourceName(ip, port, hostName)
                        
                        foundSources.add(NDISource(
                            name = sourceName,
                            machineName = hostName,
                            urlAddress = "$ip:$port",
                            sourceType = determineSourceTypeFromPort(port),
                            connectionStatus = ConnectionStatus.DISCONNECTED,
                            lastSeenTime = currentTime,
                            isOnline = true,
                            description = "透過網路掃描發現"
                        ))
                    } catch (e: Exception) {
                        continue
                    }
                }
                
                foundSources
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    private fun generateSourceName(ip: String, port: Int, hostName: String): String {
        return when {
            ip.endsWith(".21") || ip.endsWith(".58") -> {
                when (port) {
                    5960 -> "OBS PGM (程式輸出) @ $ip"
                    5961 -> "OBS PREVIEW (預覽輸出) @ $ip"
                    5962 -> "OBS AUX 1 (輔助輸出1) @ $ip"
                    5963 -> "OBS AUX 2 (輔助輸出2) @ $ip"
                    80 -> "OBS Web 服務 @ $ip"
                    8080 -> "OBS HTTP 串流 @ $ip"
                    else -> "OBS 輸出 @ $ip:$port"
                }
            }
            port == 5960 -> "NDI 主要輸出 @ $ip"
            port == 5961 -> "NDI 預覽輸出 @ $ip"
            port == 5962 -> "NDI 輔助輸出1 @ $ip"
            port == 5963 -> "NDI 輔助輸出2 @ $ip"
            port == 80 || port == 8080 -> "NDI Web 服務 @ $ip"
            hostName != "主機 $ip" -> "NDI 源 ($hostName:$port)"
            else -> "NDI 源 @ $ip:$port"
        }
    }
    
    private fun determineSourceTypeFromPort(port: Int): NDISourceType {
        return when (port) {
            5960 -> NDISourceType.NDI
            5961 -> NDISourceType.NDI_HX2
            5962 -> NDISourceType.NDI_HX3
            else -> NDISourceType.NDI
        }
    }
    
    private fun getHostName(ip: String): String {
        return try {
            InetAddress.getByName(ip).hostName
        } catch (e: Exception) {
            "主機 $ip"
        }
    }
    
    /**
     * 解析源資訊
     */
    private fun parseSourceInfo(machineInfo: String): Pair<String, String> {
        try {
            // 尋找 IP 地址模式
            val ipRegex = """(\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3})""".toRegex()
            val ipMatch = ipRegex.find(machineInfo)
            
            return if (ipMatch != null) {
                val ip = ipMatch.value
                Pair(machineInfo, "$ip:5960") // NDI 預設端口
            } else {
                Pair(machineInfo, machineInfo)
            }
        } catch (e: Exception) {
            return Pair(machineInfo, machineInfo)
        }
    }
    
    /**
     * 根據源名稱判斷 NDI 類型
     */
    private fun determineSourceType(sourceName: String): NDISourceType {
        val lowerName = sourceName.lowercase()
        return when {
            lowerName.contains("hx3") -> NDISourceType.NDI_HX3
            lowerName.contains("hx2") -> NDISourceType.NDI_HX2
            lowerName.contains("obs") -> {
                if (lowerName.contains("hx")) NDISourceType.NDI_HX2 else NDISourceType.NDI
            }
            else -> NDISourceType.NDI
        }
    }
    
    /**
     * 創建掃描狀態源
     */
    private fun createScanningStatusSources(currentTime: Long): List<NDISource> {
        return listOf(
            NDISource(
                name = "正在掃描 NDI 源...",
                machineName = "網路掃描中",
                urlAddress = "掃描中...",
                sourceType = NDISourceType.NDI,
                connectionStatus = ConnectionStatus.CONNECTING,
                lastSeenTime = currentTime,
                isOnline = true,
                description = "正在掃描本地網路中的 NDI 輸出"
            )
        )
    }
    
    /**
     * 清除錯誤狀態
     */
    fun clearError() {
        _connectionError.postValue(null)
    }
    
    /**
     * 清理資源
     */
    fun cleanup() {
        Log.i(TAG, "清理 NDI 發現管理器")
        stopScanning()
        
        try {
            if (isNativeLibraryLoaded) {
                nativeCleanup()
            }
        } catch (e: Exception) {
            Log.w(TAG, "清理 NDI 原生資源時發生錯誤", e)
        }
        
        sourceStatusMap.clear()
        _sources.postValue(emptyList())
        _isInitialized.postValue(false)
        coroutineScope.cancel()
    }
    
    /**
     * 取得源統計資訊
     */
    fun getSourceStats(): Map<String, Any> {
        return mapOf(
            "totalDiscovered" to sourceStatusMap.size,
            "currentlyOnline" to sourceStatusMap.values.count { it.isOnline },
            "isScanning" to (_isScanning.value ?: false),
            "isInitialized" to (_isInitialized.value ?: false),
            "useNativeSDK" to isNativeLibraryLoaded,
            "compatibilityMode" to isCompatibilityMode
        )
    }
    
    // ===== NDI 原生 JNI 函數聲明 =====
    
    /**
     * 初始化 NDI SDK (原生)
     */
    private external fun nativeInitialize(): Boolean
    
    /**
     * 掃描 NDI 源 (原生)
     */
    private external fun nativeScanSources(timeoutMs: Int): Array<String>
    
    /**
     * 取得 NDI SDK 版本 (原生)
     */
    private external fun nativeGetVersion(): String
    
    /**
     * 清理 NDI 資源 (原生)
     */
    private external fun nativeCleanup()
}