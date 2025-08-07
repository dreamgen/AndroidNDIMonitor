/**
 * NDI 發現功能標頭檔
 */
#pragma once

#include <vector>
#include <string>
#include <functional>
#include <thread>
#include <atomic>

#ifndef NDI_MOCK_IMPLEMENTATION
#include "Processing.NDI.Lib.h"
#endif

namespace ndi {

// NDI 源類型枚舉
enum class NDISourceType {
    NDI = 0,
    NDI_HX2 = 1,
    NDI_HX3 = 2
};

// NDI 源資訊結構
struct NDISourceInfo {
    std::string name;           // 源名稱
    std::string machine_name;   // 機器名稱
    std::string url_address;    // URL 地址
    NDISourceType source_type;  // 源類型
    bool is_online = true;      // 是否在線
    long long last_seen_time = 0; // 最後見到時間
    
    NDISourceInfo() : source_type(NDISourceType::NDI) {}
};

// NDI 發現器類別
class NDIDiscovery {
public:
    NDIDiscovery();
    ~NDIDiscovery();
    
    // 初始化 NDI 發現器
    bool initialize();
    
    // 單次掃描 NDI 源
    std::vector<NDISourceInfo> scanSources(int timeout_ms = 3000);
    
    // 開始連續掃描
    void startContinuousScanning(std::function<void(const std::vector<NDISourceInfo>&)> callback);
    
    // 停止連續掃描
    void stopContinuousScanning();
    
    // 取得版本資訊
    std::string getVersion() const;
    
    // 清理資源
    void cleanup();
    
private:
#ifndef NDI_MOCK_IMPLEMENTATION
    NDIlib_find_instance_t finder_instance_;
#else
    void* finder_instance_;
#endif
    
    std::atomic<bool> is_initialized_{false};
    std::atomic<bool> is_scanning_{false};
    std::thread scan_thread_;
    
    // 根據源名稱判斷 NDI 類型
    NDISourceType determineSourceType(const std::string& source_name) const;
};

} // namespace ndi