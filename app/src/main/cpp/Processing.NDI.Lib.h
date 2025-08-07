/**
 * 模擬 NDI SDK 頭文件
 * 用於當實際 NDI SDK 不可用時的編譯支援
 * 
 * 注意：這是一個簡化的模擬版本，僅用於編譯
 * 實際使用時需要替換為真正的 NDI SDK
 */
#pragma once

#ifdef __cplusplus
extern "C" {
#endif

// NDI 基本類型定義
typedef void* NDIlib_find_instance_t;
typedef void* NDIlib_recv_instance_t;

// NDI 源結構
typedef struct NDIlib_source_t
{
    const char* p_ndi_name;
    const char* p_url_address;
} NDIlib_source_t;

// NDI 查找創建參數
typedef struct NDIlib_find_create_t
{
    bool show_local_sources;
    const char* p_groups;
    const char* p_extra_ips;
} NDIlib_find_create_t;

// NDI 接收創建參數
typedef struct NDIlib_recv_create_v3_t
{
    NDIlib_source_t source_to_connect_to;
    int color_format;
    int bandwidth;
    bool allow_video_fields;
} NDIlib_recv_create_v3_t;

// NDI 視頻幀結構
typedef struct NDIlib_video_frame_v2_t
{
    int xres;
    int yres;
    int frame_rate_N;
    int frame_rate_D;
    long long timestamp;
    int line_stride_in_bytes;
    const unsigned char* p_data;
} NDIlib_video_frame_v2_t;

// NDI 音頻幀結構
typedef struct NDIlib_audio_frame_v2_t
{
    int sample_rate;
    int no_channels;
    int no_samples;
    long long timestamp;
    const float* p_data;
} NDIlib_audio_frame_v2_t;

// NDI 元數據幀結構
typedef struct NDIlib_metadata_frame_t
{
    long long timestamp;
    const char* p_data;
} NDIlib_metadata_frame_t;

// NDI 幀類型枚舉
typedef enum NDIlib_frame_type_e
{
    NDIlib_frame_type_none = 0,
    NDIlib_frame_type_video = 1,
    NDIlib_frame_type_audio = 2,
    NDIlib_frame_type_metadata = 3
} NDIlib_frame_type_e;

// NDI 顏色格式枚舉
typedef enum NDIlib_recv_color_format_e
{
    NDIlib_recv_color_format_BGRX_BGRA = 0,
    NDIlib_recv_color_format_UYVY_BGRA = 1,
    NDIlib_recv_color_format_RGBX_RGBA = 2,
    NDIlib_recv_color_format_UYVY_RGBA = 3
} NDIlib_recv_color_format_e;

// NDI 帶寬枚举
typedef enum NDIlib_recv_bandwidth_e
{
    NDIlib_recv_bandwidth_metadata_only = -10,
    NDIlib_recv_bandwidth_audio_only = 10,
    NDIlib_recv_bandwidth_lowest = 0,
    NDIlib_recv_bandwidth_highest = 100
} NDIlib_recv_bandwidth_e;

// NDI 函數聲明（模擬實現，實際不執行任何操作）

// 初始化 NDI
inline bool NDIlib_initialize() { return false; }

// 銷毀 NDI
inline void NDIlib_destroy() { }

// 取得版本
inline const char* NDIlib_version() { return "Mock NDI SDK v1.0"; }

// 創建查找器
inline NDIlib_find_instance_t NDIlib_find_create_v2(const NDIlib_find_create_t* p_create_settings) { 
    return nullptr; 
}

// 銷毀查找器
inline void NDIlib_find_destroy(NDIlib_find_instance_t p_instance) { }

// 等待源
inline bool NDIlib_find_wait_for_sources(NDIlib_find_instance_t p_instance, unsigned int timeout_in_ms) { 
    return false; 
}

// 取得當前源
inline const NDIlib_source_t* NDIlib_find_get_current_sources(NDIlib_find_instance_t p_instance, unsigned int* p_no_sources) { 
    *p_no_sources = 0;
    return nullptr; 
}

// 創建接收器
inline NDIlib_recv_instance_t NDIlib_recv_create_v3(const NDIlib_recv_create_v3_t* p_create_settings) { 
    return nullptr; 
}

// 銷毀接收器
inline void NDIlib_recv_destroy(NDIlib_recv_instance_t p_instance) { }

// 捕獲幀
inline NDIlib_frame_type_e NDIlib_recv_capture_v2(NDIlib_recv_instance_t p_instance, 
                                                   NDIlib_video_frame_v2_t* p_video_data, 
                                                   NDIlib_audio_frame_v2_t* p_audio_data, 
                                                   NDIlib_metadata_frame_t* p_metadata, 
                                                   unsigned int timeout_in_ms) { 
    return NDIlib_frame_type_none; 
}

// 釋放視頻幀
inline void NDIlib_recv_free_video_v2(NDIlib_recv_instance_t p_instance, const NDIlib_video_frame_v2_t* p_video_data) { }

// 釋放音頻幀
inline void NDIlib_recv_free_audio_v2(NDIlib_recv_instance_t p_instance, const NDIlib_audio_frame_v2_t* p_audio_data) { }

// 釋放元數據幀
inline void NDIlib_recv_free_metadata(NDIlib_recv_instance_t p_instance, const NDIlib_metadata_frame_t* p_metadata) { }

#ifdef __cplusplus
}
#endif