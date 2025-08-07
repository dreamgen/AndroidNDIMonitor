package org.tpeyh.androidndimonitor.ndi

/**
 * NDI 連接狀態枚舉
 * 定義 NDI 源的各種連接狀態
 */
enum class ConnectionStatus {
    /** 未連接狀態 */
    DISCONNECTED,
    
    /** 連接中 */
    CONNECTING,
    
    /** 已連接 */
    CONNECTED,
    
    /** 連接失敗 */
    CONNECTION_FAILED,
    
    /** 連接中斷 */
    DISCONNECTING,
    
    /** 網路錯誤 */
    NETWORK_ERROR,
    
    /** NDI 源不可用 */
    SOURCE_UNAVAILABLE
}

/**
 * NDI 源類型
 * 支援不同的 NDI 格式
 */
enum class NDISourceType {
    /** 標準 NDI */
    NDI,
    
    /** NDI HX2 壓縮格式 */
    NDI_HX2,
    
    /** NDI HX3 壓縮格式 */
    NDI_HX3,
    
    /** 未知格式 */
    UNKNOWN
}