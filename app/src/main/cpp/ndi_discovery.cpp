/**
 * NDI 發現功能實作
 * 負責掃描和管理 NDI 源
 */
#include "ndi_discovery.h"
#include <android/log.h>
#include <thread>
#include <chrono>

#define LOG_TAG "NDIDiscovery"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace ndi {

NDIDiscovery::NDIDiscovery() : finder_instance_(nullptr), is_scanning_(false) {
    LOGD("創建 NDI 發現器實例");
}

NDIDiscovery::~NDIDiscovery() {
    cleanup();
}

bool NDIDiscovery::initialize() {
    LOGI("初始化 NDI 發現器");
    
#ifdef NDI_MOCK_IMPLEMENTATION
    LOGW("使用模擬 NDI 發現器");
    is_initialized_ = true;
    return true;
#else
    try {
        // 初始化 NDI 運行時
        if (!NDIlib_initialize()) {
            LOGE("NDI 運行時初始化失敗");
            return false;
        }
        
        // 配置發現器參數
        NDIlib_find_create_t find_desc = {};
        find_desc.show_local_sources = true;
        find_desc.p_groups = nullptr;
        find_desc.p_extra_ips = nullptr;
        
        // 創建 NDI 發現器
        finder_instance_ = NDIlib_find_create_v2(&find_desc);
        
        if (!finder_instance_) {
            LOGE("創建 NDI 發現器失敗");
            NDIlib_destroy();
            return false;
        }
        
        is_initialized_ = true;
        LOGI("NDI 發現器初始化成功");
        return true;
        
    } catch (const std::exception& e) {
        LOGE("NDI 發現器初始化異常: %s", e.what());
        return false;
    }
#endif
}

std::vector<NDISourceInfo> NDIDiscovery::scanSources(int timeout_ms) {
    std::vector<NDISourceInfo> sources;
    
    if (!is_initialized_) {
        LOGE("NDI 發現器未初始化");
        return sources;
    }
    
#ifdef NDI_MOCK_IMPLEMENTATION
    LOGD("模擬掃描 NDI 源");
    
    // 創建模擬源
    NDISourceInfo source1;
    source1.name = "模擬 OBS PGM";
    source1.machine_name = "OBS-PC"; 
    source1.url_address = "192.168.1.21:5960";
    source1.source_type = NDISourceType::NDI;
    sources.push_back(source1);
    
    NDISourceInfo source2;
    source2.name = "模擬 OBS PREVIEW";
    source2.machine_name = "OBS-PC";
    source2.url_address = "192.168.1.21:5961";
    source2.source_type = NDISourceType::NDI_HX2;
    sources.push_back(source2);
    
    NDISourceInfo source3;
    source3.name = "測試攝影機";
    source3.machine_name = "Test-Machine";
    source3.url_address = "192.168.1.100:5960";
    source3.source_type = NDISourceType::NDI;
    sources.push_back(source3);
    
    LOGI("模擬發現 %zu 個 NDI 源", sources.size());
    
#else
    try {
        LOGD("開始真實 NDI 源掃描 (超時: %d ms)", timeout_ms);
        
        // 等待 NDI 源發現
        if (!NDIlib_find_wait_for_sources(finder_instance_, timeout_ms)) {
            LOGD("NDI 源掃描超時，未發現新源");
            return sources;
        }
        
        // 獲取當前發現的源
        uint32_t num_sources = 0;
        const NDIlib_source_t* ndi_sources = NDIlib_find_get_current_sources(finder_instance_, &num_sources);
        
        if (!ndi_sources || num_sources == 0) {
            LOGD("未發現任何 NDI 源");
            return sources;
        }
        
        LOGI("發現 %d 個 NDI 源", num_sources);
        
        // 轉換 NDI 源到我們的格式
        for (uint32_t i = 0; i < num_sources; i++) {
            NDISourceInfo source_info;
            
            if (ndi_sources[i].p_ndi_name) {
                source_info.name = ndi_sources[i].p_ndi_name;
            }
            
            if (ndi_sources[i].p_url_address) {
                source_info.url_address = ndi_sources[i].p_url_address;
                
                // 從 URL 中提取機器名稱
                std::string url = source_info.url_address;
                size_t colon_pos = url.find(':');
                if (colon_pos != std::string::npos) {
                    source_info.machine_name = url.substr(0, colon_pos);
                } else {
                    source_info.machine_name = url;
                }
            }
            
            // 根據源名稱判斷 NDI 類型
            source_info.source_type = determineSourceType(source_info.name);
            
            sources.push_back(source_info);
            
            LOGD("  - 源 %d: %s @ %s", i, source_info.name.c_str(), source_info.url_address.c_str());
        }
        
    } catch (const std::exception& e) {
        LOGE("掃描 NDI 源時發生異常: %s", e.what());
    }
#endif
    
    return sources;
}

void NDIDiscovery::startContinuousScanning(std::function<void(const std::vector<NDISourceInfo>&)> callback) {
    if (is_scanning_) {
        LOGW("連續掃描已在進行中");
        return;
    }
    
    is_scanning_ = true;
    
    // 在背景執行緒中進行連續掃描
    scan_thread_ = std::thread([this, callback]() {
        LOGI("開始連續 NDI 源掃描");
        
        while (is_scanning_) {
            try {
                auto sources = scanSources(3000); // 3 秒超時
                
                if (callback) {
                    callback(sources);
                }
                
                // 等待下次掃描
                std::this_thread::sleep_for(std::chrono::milliseconds(5000)); // 5 秒間隔
                
            } catch (const std::exception& e) {
                LOGE("連續掃描異常: %s", e.what());
                std::this_thread::sleep_for(std::chrono::milliseconds(1000));
            }
        }
        
        LOGI("連續 NDI 源掃描已停止");
    });
}

void NDIDiscovery::stopContinuousScanning() {
    if (!is_scanning_) {
        return;
    }
    
    LOGI("停止連續 NDI 源掃描");
    is_scanning_ = false;
    
    if (scan_thread_.joinable()) {
        scan_thread_.join();
    }
}

std::string NDIDiscovery::getVersion() const {
#ifdef NDI_MOCK_IMPLEMENTATION
    return "Mock NDI Discovery v1.0";
#else
    const char* version = NDIlib_version();
    return version ? std::string(version) : "Unknown";
#endif
}

void NDIDiscovery::cleanup() {
    LOGI("清理 NDI 發現器資源");
    
    stopContinuousScanning();
    
#ifndef NDI_MOCK_IMPLEMENTATION
    if (finder_instance_) {
        NDIlib_find_destroy(finder_instance_);
        finder_instance_ = nullptr;
    }
    
    if (is_initialized_) {
        NDIlib_destroy();
    }
#endif
    
    is_initialized_ = false;
}

NDISourceType NDIDiscovery::determineSourceType(const std::string& source_name) const {
    std::string lower_name = source_name;
    std::transform(lower_name.begin(), lower_name.end(), lower_name.begin(), ::tolower);
    
    if (lower_name.find("hx3") != std::string::npos) {
        return NDISourceType::NDI_HX3;
    } else if (lower_name.find("hx2") != std::string::npos || 
               lower_name.find("hx") != std::string::npos) {
        return NDISourceType::NDI_HX2;
    } else {
        return NDISourceType::NDI;
    }
}

} // namespace ndi