/**
 * NDI 接收器實作
 * 負責接收和解碼 NDI 視頻流
 */
#include "ndi_receiver.h"
#include <android/log.h>

#define LOG_TAG "NDIReceiver"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace ndi {

NDIReceiver::NDIReceiver() : receiver_instance_(nullptr), is_receiving_(false) {
    LOGD("創建 NDI 接收器實例");
}

NDIReceiver::~NDIReceiver() {
    cleanup();
}

bool NDIReceiver::initialize() {
    LOGI("初始化 NDI 接收器");
    
#ifdef NDI_MOCK_IMPLEMENTATION
    LOGW("使用模擬 NDI 接收器");
    is_initialized_ = true;
    return true;
#else
    try {
        // NDI 運行時應該已經初始化了，但我們確保一下
        if (!NDIlib_initialize()) {
            LOGE("NDI 運行時初始化失敗");
            return false;
        }
        
        is_initialized_ = true;
        LOGI("NDI 接收器初始化成功");
        return true;
        
    } catch (const std::exception& e) {
        LOGE("NDI 接收器初始化異常: %s", e.what());
        return false;
    }
#endif
}

bool NDIReceiver::connect(const NDISourceInfo& source_info) {
    if (!is_initialized_) {
        LOGE("NDI 接收器未初始化");
        return false;
    }
    
    LOGI("連接到 NDI 源: %s @ %s", source_info.name.c_str(), source_info.url_address.c_str());
    
#ifdef NDI_MOCK_IMPLEMENTATION
    // 模擬連接
    current_source_ = source_info;
    is_connected_ = true;
    LOGI("模擬連接成功到: %s", source_info.name.c_str());
    return true;
#else
    try {
        // 如果已經連接，先斷開
        if (receiver_instance_) {
            disconnect();
        }
        
        // 設置 NDI 源
        NDIlib_source_t ndi_source;
        ndi_source.p_ndi_name = source_info.name.c_str();
        ndi_source.p_url_address = source_info.url_address.c_str();
        
        // 配置接收器參數
        NDIlib_recv_create_v3_t recv_desc = {};
        recv_desc.source_to_connect_to = ndi_source;
        recv_desc.color_format = NDIlib_recv_color_format_UYVY_RGBA;
        recv_desc.bandwidth = NDIlib_recv_bandwidth_highest;
        recv_desc.allow_video_fields = true;
        
        // 創建 NDI 接收器
        receiver_instance_ = NDIlib_recv_create_v3(&recv_desc);
        
        if (!receiver_instance_) {
            LOGE("創建 NDI 接收器失敗");
            return false;
        }
        
        current_source_ = source_info;
        is_connected_ = true;
        
        LOGI("成功連接到 NDI 源: %s", source_info.name.c_str());
        return true;
        
    } catch (const std::exception& e) {
        LOGE("連接 NDI 源時發生異常: %s", e.what());
        return false;
    }
#endif
}

void NDIReceiver::disconnect() {
    if (!is_connected_) {
        return;
    }
    
    LOGI("斷開 NDI 源連接: %s", current_source_.name.c_str());
    
    stopReceiving();
    
#ifndef NDI_MOCK_IMPLEMENTATION
    if (receiver_instance_) {
        NDIlib_recv_destroy(receiver_instance_);
        receiver_instance_ = nullptr;
    }
#endif
    
    is_connected_ = false;
    current_source_ = NDISourceInfo();
}

void NDIReceiver::startReceiving(std::function<void(const NDIFrame&)> frame_callback) {
    if (!is_connected_) {
        LOGE("未連接到 NDI 源，無法開始接收");
        return;
    }
    
    if (is_receiving_) {
        LOGW("已在接收 NDI 流");
        return;
    }
    
    is_receiving_ = true;
    
    // 在背景執行緒中接收幀
    receive_thread_ = std::thread([this, frame_callback]() {
        LOGI("開始接收 NDI 視頻流: %s", current_source_.name.c_str());
        
#ifdef NDI_MOCK_IMPLEMENTATION
        // 模擬幀接收
        int frame_count = 0;
        while (is_receiving_) {
            try {
                NDIFrame mock_frame;
                mock_frame.width = 1920;
                mock_frame.height = 1080;
                mock_frame.frame_rate = 30.0f;
                mock_frame.timestamp = std::chrono::steady_clock::now().time_since_epoch().count();
                mock_frame.data_size = mock_frame.width * mock_frame.height * 4; // RGBA
                
                if (frame_callback) {
                    frame_callback(mock_frame);
                }
                
                frame_count++;
                std::this_thread::sleep_for(std::chrono::milliseconds(33)); // ~30fps
                
            } catch (const std::exception& e) {
                LOGE("模擬幀接收異常: %s", e.what());
                std::this_thread::sleep_for(std::chrono::milliseconds(100));
            }
        }
        
        LOGI("模擬 NDI 流接收已停止，共處理 %d 幀", frame_count);
#else
        int frame_count = 0;
        
        while (is_receiving_ && receiver_instance_) {
            try {
                NDIlib_video_frame_v2_t video_frame;
                NDIlib_audio_frame_v2_t audio_frame;
                NDIlib_metadata_frame_t metadata_frame;
                
                // 等待下一幀
                switch (NDIlib_recv_capture_v2(receiver_instance_, &video_frame, &audio_frame, &metadata_frame, 100)) {
                    case NDIlib_frame_type_video: {
                        // 處理視頻幀
                        NDIFrame frame;
                        frame.width = video_frame.xres;
                        frame.height = video_frame.yres;
                        frame.frame_rate = static_cast<float>(video_frame.frame_rate_N) / static_cast<float>(video_frame.frame_rate_D);
                        frame.timestamp = video_frame.timestamp;
                        frame.data_size = video_frame.line_stride_in_bytes * video_frame.yres;
                        
                        if (frame_callback) {
                            frame_callback(frame);
                        }
                        
                        frame_count++;
                        
                        // 釋放視頻幀
                        NDIlib_recv_free_video_v2(receiver_instance_, &video_frame);
                        break;
                    }
                    case NDIlib_frame_type_audio: {
                        // 釋放音頻幀（暫時不處理音頻）
                        NDIlib_recv_free_audio_v2(receiver_instance_, &audio_frame);
                        break;
                    }
                    case NDIlib_frame_type_metadata: {
                        // 釋放元數據幀
                        NDIlib_recv_free_metadata(receiver_instance_, &metadata_frame);
                        break;
                    }
                    case NDIlib_frame_type_none: {
                        // 無幀，繼續等待
                        break;
                    }
                    default: {
                        LOGW("收到未知幀類型");
                        break;
                    }
                }
                
            } catch (const std::exception& e) {
                LOGE("接收 NDI 幀時發生異常: %s", e.what());
                std::this_thread::sleep_for(std::chrono::milliseconds(100));
            }
        }
        
        LOGI("NDI 流接收已停止，共處理 %d 幀", frame_count);
#endif
    });
}

void NDIReceiver::stopReceiving() {
    if (!is_receiving_) {
        return;
    }
    
    LOGI("停止接收 NDI 流");
    is_receiving_ = false;
    
    if (receive_thread_.joinable()) {
        receive_thread_.join();
    }
}

NDISourceInfo NDIReceiver::getCurrentSource() const {
    return current_source_;
}

bool NDIReceiver::isConnected() const {
    return is_connected_;
}

bool NDIReceiver::isReceiving() const {
    return is_receiving_;
}

void NDIReceiver::cleanup() {
    LOGI("清理 NDI 接收器資源");
    
    if (is_receiving_) {
        stopReceiving();
    }
    
    if (is_connected_) {
        disconnect();
    }
    
    is_initialized_ = false;
}

} // namespace ndi