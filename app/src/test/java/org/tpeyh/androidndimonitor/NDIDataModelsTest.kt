package org.tpeyh.androidndimonitor

import org.junit.Test
import org.tpeyh.androidndimonitor.ndi.*

/**
 * NDI 數據模型單元測試
 * 測試不依賴 JNI 的數據結構
 */
class NDIDataModelsTest {
    
    @Test
    fun testNDISourceCreation() {
        // 測試 NDI 源對象創建
        val ndiSource = NDISource(
            name = "測試源",
            machineName = "測試機器",
            urlAddress = "192.168.1.100:5960",
            sourceType = NDISourceType.NDI,
            connectionStatus = ConnectionStatus.DISCONNECTED,
            lastSeenTime = System.currentTimeMillis(),
            isOnline = true,
            description = "單元測試源"
        )
        
        assert(ndiSource.name == "測試源") { "源名稱應該正確" }
        assert(ndiSource.machineName == "測試機器") { "機器名稱應該正確" }
        assert(ndiSource.urlAddress == "192.168.1.100:5960") { "URL 地址應該正確" }
        assert(ndiSource.sourceType == NDISourceType.NDI) { "源類型應該正確" }
        assert(ndiSource.connectionStatus == ConnectionStatus.DISCONNECTED) { "連接狀態應該正確" }
        assert(ndiSource.isOnline) { "源應該在線" }
        assert(ndiSource.description == "單元測試源") { "描述應該正確" }
    }
    
    @Test
    fun testNDIVideoFrameCreation() {
        // 測試 NDI 視頻幀創建
        val timestamp = System.currentTimeMillis()
        val testData = ByteArray(1920 * 1080 * 4) { it.toByte() }
        
        val frame = NDIVideoFrame(
            width = 1920,
            height = 1080,
            frameRate = 30.0f,
            timestamp = timestamp,
            data = testData,
            sourceName = "測試源"
        )
        
        assert(frame.width == 1920) { "幀寬度應該正確" }
        assert(frame.height == 1080) { "幀高度應該正確" }
        assert(frame.frameRate == 30.0f) { "幀率應該正確" }
        assert(frame.timestamp == timestamp) { "時間戳應該正確" }
        assert(frame.sourceName == "測試源") { "源名稱應該正確" }
        assert(frame.data.size == 1920 * 1080 * 4) { "數據大小應該正確" }
        assert(frame.data.contentEquals(testData)) { "數據內容應該正確" }
    }
    
    @Test
    fun testNDISourceTypeEnum() {
        // 測試 NDI 源類型枚舉
        val types = NDISourceType.values()
        
        assert(types.contains(NDISourceType.NDI)) { "應包含 NDI 類型" }
        assert(types.contains(NDISourceType.NDI_HX2)) { "應包含 NDI_HX2 類型" }
        assert(types.contains(NDISourceType.NDI_HX3)) { "應包含 NDI_HX3 類型" }
        
        assert(NDISourceType.NDI.ordinal == 0) { "NDI 順序應為 0" }
        assert(NDISourceType.NDI_HX2.ordinal == 1) { "NDI_HX2 順序應為 1" }
        assert(NDISourceType.NDI_HX3.ordinal == 2) { "NDI_HX3 順序應為 2" }
    }
    
    @Test
    fun testConnectionStatusEnum() {
        // 測試連接狀態枚舉
        val statuses = ConnectionStatus.values()
        
        assert(statuses.contains(ConnectionStatus.DISCONNECTED)) { "應包含 DISCONNECTED" }
        assert(statuses.contains(ConnectionStatus.CONNECTING)) { "應包含 CONNECTING" }
        assert(statuses.contains(ConnectionStatus.CONNECTED)) { "應包含 CONNECTED" }
        assert(statuses.contains(ConnectionStatus.STREAMING)) { "應包含 STREAMING" }
        assert(statuses.contains(ConnectionStatus.CONNECTION_FAILED)) { "應包含 CONNECTION_FAILED" }
        assert(statuses.contains(ConnectionStatus.NETWORK_ERROR)) { "應包含 NETWORK_ERROR" }
    }
    
    @Test
    fun testNDISourceEquality() {
        // 測試 NDI 源相等性
        val source1 = NDISource(
            name = "測試源",
            machineName = "機器A",
            urlAddress = "192.168.1.100:5960",
            sourceType = NDISourceType.NDI,
            connectionStatus = ConnectionStatus.DISCONNECTED,
            lastSeenTime = 123456789L,
            isOnline = true
        )
        
        val source2 = NDISource(
            name = "測試源",
            machineName = "機器A",
            urlAddress = "192.168.1.100:5960",
            sourceType = NDISourceType.NDI,
            connectionStatus = ConnectionStatus.DISCONNECTED,
            lastSeenTime = 123456789L,
            isOnline = true
        )
        
        val source3 = source2.copy(name = "不同源")
        
        assert(source1 == source2) { "相同內容的源應該相等" }
        assert(source1 != source3) { "不同內容的源應該不相等" }
        assert(source1.hashCode() == source2.hashCode()) { "相同源的 hashCode 應該相等" }
    }
    
    @Test
    fun testNDIVideoFrameEquality() {
        // 測試 NDI 視頻幀相等性
        val testData = byteArrayOf(1, 2, 3, 4, 5)
        
        val frame1 = NDIVideoFrame(
            width = 100,
            height = 100,
            frameRate = 30.0f,
            timestamp = 123456789L,
            data = testData,
            sourceName = "源A"
        )
        
        val frame2 = NDIVideoFrame(
            width = 100,
            height = 100,
            frameRate = 30.0f,
            timestamp = 123456789L,
            data = testData,
            sourceName = "源A"
        )
        
        val frame3 = frame2.copy(width = 200)
        
        assert(frame1 == frame2) { "相同內容的幀應該相等" }
        assert(frame1 != frame3) { "不同內容的幀應該不相等" }
        assert(frame1.hashCode() == frame2.hashCode()) { "相同幀的 hashCode 應該相等" }
    }
}