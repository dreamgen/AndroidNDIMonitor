package org.tpeyh.androidndimonitor.ndi

/**
 * NDI 源資料類別
 * 表示網路上發現的 NDI 源
 */
data class NDISource(
    /** NDI 源名稱 */
    val name: String,
    
    /** 來源機器名稱 */
    val machineName: String,
    
    /** NDI 源的網路地址 */
    val urlAddress: String,
    
    /** NDI 源類型 */
    val sourceType: NDISourceType = NDISourceType.NDI,
    
    /** 當前連接狀態 */
    val connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    
    /** 源的描述信息 */
    val description: String = "",
    
    /** 最後發現時間（毫秒） */
    val lastSeenTime: Long = System.currentTimeMillis(),
    
    /** 是否在線 */
    val isOnline: Boolean = true,
    
    /** 視頻解析度寬度 */
    val videoWidth: Int = 0,
    
    /** 視頻解析度高度 */
    val videoHeight: Int = 0,
    
    /** 幀率 */
    val frameRate: Float = 0.0f
) {
    
    /**
     * 取得顯示用的完整名稱
     */
    fun getDisplayName(): String {
        return if (machineName.isNotEmpty() && machineName != "unknown") {
            "$name ($machineName)"
        } else {
            name
        }
    }
    
    /**
     * 取得源的簡短描述
     */
    fun getShortDescription(): String {
        return when {
            videoWidth > 0 && videoHeight > 0 -> "${videoWidth}x${videoHeight}"
            frameRate > 0 -> "${frameRate}fps"
            else -> sourceType.name
        }
    }
    
    /**
     * 檢查源是否可用
     */
    fun isAvailable(): Boolean {
        return isOnline && connectionStatus != ConnectionStatus.SOURCE_UNAVAILABLE
    }
    
    /**
     * 取得狀態圖示資源 ID
     */
    fun getStatusIconRes(): String {
        return when (connectionStatus) {
            ConnectionStatus.CONNECTED -> "ic_connected"
            ConnectionStatus.CONNECTING -> "ic_connecting"
            ConnectionStatus.CONNECTION_FAILED, 
            ConnectionStatus.NETWORK_ERROR -> "ic_error"
            ConnectionStatus.SOURCE_UNAVAILABLE -> "ic_unavailable"
            else -> "ic_disconnected"
        }
    }
}