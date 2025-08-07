/**
 * NDI 接收器標頭檔
 */
#pragma once

#include "ndi_discovery.h"
#include <thread>
#include <atomic>
#include <functional>
#include <chrono>

#ifndef NDI_MOCK_IMPLEMENTATION
#include "Processing.NDI.Lib.h"
#endif

namespace ndi {

// NDI 幀資料結構
struct NDIFrame {
    int width = 0;              // 幀寬度
    int height = 0;             // 幀高度
    float frame_rate = 0.0f;    // 幀率
    int64_t timestamp = 0;      // 時間戳
    size_t data_size = 0;       // 數據大小
    const void* data = nullptr; // 幀數據指針（僅在回調中有效）
    
    NDIFrame() = default;
};

// NDI 接收器類別
class NDIReceiver {
public:
    NDIReceiver();
    ~NDIReceiver();
    
    // 初始化接收器
    bool initialize();
    
    // 連接到指定的 NDI 源
    bool connect(const NDISourceInfo& source_info);
    
    // 斷開當前連接
    void disconnect();
    
    // 開始接收幀
    void startReceiving(std::function<void(const NDIFrame&)> frame_callback);
    
    // 停止接收幀
    void stopReceiving();
    
    // 取得當前連接的源資訊
    NDISourceInfo getCurrentSource() const;
    
    // 檢查是否已連接
    bool isConnected() const;
    
    // 檢查是否正在接收
    bool isReceiving() const;
    
    // 清理資源
    void cleanup();
    
private:
#ifndef NDI_MOCK_IMPLEMENTATION
    NDIlib_recv_instance_t receiver_instance_;
#else
    void* receiver_instance_;
#endif
    
    std::atomic<bool> is_initialized_{false};
    std::atomic<bool> is_connected_{false};
    std::atomic<bool> is_receiving_{false};
    
    NDISourceInfo current_source_;
    std::thread receive_thread_;
};

} // namespace ndi