package org.tpeyh.androidndimonitor.ndi

/**
 * NDI 源狀態追蹤類別
 * 用於監控和記錄 NDI 源的狀態變化
 */
data class NDISourceStatus(
    /** 是否在線 */
    var isOnline: Boolean = false,
    
    /** 首次發現時間 */
    val firstSeen: Long = System.currentTimeMillis(),
    
    /** 最後一次發現時間 */
    var lastSeen: Long = System.currentTimeMillis(),
    
    /** 連接嘗試次數 */
    var connectionAttempts: Int = 0,
    
    /** 連接成功次數 */
    var successfulConnections: Int = 0,
    
    /** 最後一次連接時間 */
    var lastConnectionTime: Long = 0,
    
    /** 平均延遲（毫秒） */
    var averageLatency: Float = 0.0f,
    
    /** 最後一次錯誤訊息 */
    var lastError: String = ""
) {
    
    /**
     * 更新最後發現時間並標記為在線
     */
    fun updateSeen() {
        lastSeen = System.currentTimeMillis()
        isOnline = true
    }
    
    /**
     * 標記為離線
     */
    fun markOffline() {
        isOnline = false
    }
    
    /**
     * 記錄連接嘗試
     */
    fun recordConnectionAttempt() {
        connectionAttempts++
        lastConnectionTime = System.currentTimeMillis()
    }
    
    /**
     * 記錄連接成功
     */
    fun recordSuccessfulConnection() {
        successfulConnections++
    }
    
    /**
     * 更新延遲
     */
    fun updateLatency(latency: Float) {
        averageLatency = if (averageLatency == 0.0f) {
            latency
        } else {
            (averageLatency + latency) / 2
        }
    }
    
    /**
     * 記錄錯誤
     */
    fun recordError(error: String) {
        lastError = error
    }
    
    /**
     * 取得連接成功率
     */
    fun getSuccessRate(): Float {
        return if (connectionAttempts > 0) {
            successfulConnections.toFloat() / connectionAttempts
        } else {
            0.0f
        }
    }
    
    /**
     * 取得在線時長（秒）
     */
    fun getOnlineDuration(): Long {
        return (lastSeen - firstSeen) / 1000
    }
    
    /**
     * 檢查是否長時間離線（超過指定秒數）
     */
    fun isOfflineTooLong(timeoutSeconds: Long): Boolean {
        val currentTime = System.currentTimeMillis()
        return (currentTime - lastSeen) > (timeoutSeconds * 1000)
    }
}