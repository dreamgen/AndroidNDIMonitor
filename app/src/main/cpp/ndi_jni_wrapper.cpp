/**
 * NDI JNI 包裝器
 * 提供 Kotlin/Java 與 NDI SDK 之間的橋樑
 */
#include <jni.h>
#include <string>
#include <vector>
#include <memory>
#include <cstdint>
#include <android/log.h>

#ifdef NDI_MOCK_IMPLEMENTATION
// 模擬實作，當 NDI SDK 不可用時使用
#define LOG_TAG "NDIJNIWrapper"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#else
#include "Processing.NDI.Lib.h"
#define LOG_TAG "NDIJNIWrapper"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#endif

// NDI 發現器結構
struct NDIFinderWrapper {
#ifndef NDI_MOCK_IMPLEMENTATION
    NDIlib_find_instance_t finder_instance;
#endif
    bool is_initialized;
    
    NDIFinderWrapper() : is_initialized(false) {
#ifndef NDI_MOCK_IMPLEMENTATION
        finder_instance = nullptr;
#endif
    }
};

// 全域 NDI 發現器實例
static std::unique_ptr<NDIFinderWrapper> g_ndi_finder;

extern "C" {

/**
 * 初始化 NDI SDK
 */
JNIEXPORT jboolean JNICALL
Java_org_tpeyh_androidndimonitor_ndi_NDIDiscoveryManager_nativeInitialize(JNIEnv *env, jobject thiz) {
    LOGI("開始初始化 NDI SDK...");
    
#ifdef NDI_MOCK_IMPLEMENTATION
    LOGW("使用模擬 NDI 實作 (NDI SDK 不可用)");
    g_ndi_finder = std::make_unique<NDIFinderWrapper>();
    g_ndi_finder->is_initialized = true;
    return JNI_TRUE;
#else
    try {
        // 初始化 NDI 運行時
        if (!NDIlib_initialize()) {
            LOGE("NDI SDK 初始化失敗");
            return JNI_FALSE;
        }
        
        LOGI("NDI SDK 初始化成功");
        
        // 創建 NDI 發現器實例
        g_ndi_finder = std::make_unique<NDIFinderWrapper>();
        
        // 配置 NDI 發現器參數
        NDIlib_find_create_t find_desc;
        find_desc.show_local_sources = true;
        find_desc.p_groups = nullptr;
        find_desc.p_extra_ips = nullptr;
        
        // 創建 NDI 發現器
        g_ndi_finder->finder_instance = NDIlib_find_create_v2(&find_desc);
        
        if (!g_ndi_finder->finder_instance) {
            LOGE("創建 NDI 發現器失敗");
            NDIlib_destroy();
            return JNI_FALSE;
        }
        
        g_ndi_finder->is_initialized = true;
        LOGI("NDI 發現器創建成功");
        return JNI_TRUE;
        
    } catch (const std::exception& e) {
        LOGE("NDI 初始化異常: %s", e.what());
        return JNI_FALSE;
    }
#endif
}

/**
 * 掃描 NDI 源
 */
JNIEXPORT jobjectArray JNICALL
Java_org_tpeyh_androidndimonitor_ndi_NDIDiscoveryManager_nativeScanSources(JNIEnv *env, jobject thiz, jint timeout_ms) {
    if (!g_ndi_finder || !g_ndi_finder->is_initialized) {
        LOGE("NDI 發現器未初始化");
        return nullptr;
    }
    
#ifdef NDI_MOCK_IMPLEMENTATION
    // 模擬實作：返回幾個測試源
    LOGD("模擬掃描 NDI 源 (超時: %d ms)", timeout_ms);
    
    // 創建模擬源數據
    std::vector<std::string> mock_sources = {
        "模擬 OBS PGM (OBS-PC)",
        "模擬 OBS PREVIEW (OBS-PC)", 
        "測試攝影機 (Test-Machine)"
    };
    
    // 創建 Java String 數組
    jclass string_class = env->FindClass("java/lang/String");
    jobjectArray result = env->NewObjectArray(mock_sources.size(), string_class, nullptr);
    
    for (size_t i = 0; i < mock_sources.size(); i++) {
        jstring source_name = env->NewStringUTF(mock_sources[i].c_str());
        env->SetObjectArrayElement(result, i, source_name);
        env->DeleteLocalRef(source_name);
    }
    
    return result;
    
#else
    try {
        LOGD("開始掃描 NDI 源 (超時: %d ms)", timeout_ms);
        
        // 等待 NDI 源發現
        if (!NDIlib_find_wait_for_sources(g_ndi_finder->finder_instance, timeout_ms)) {
            LOGD("NDI 源掃描超時");
            return env->NewObjectArray(0, env->FindClass("java/lang/String"), nullptr);
        }
        
        // 獲取發現的源
        uint32_t num_sources = 0;
        const NDIlib_source_t* sources = NDIlib_find_get_current_sources(g_ndi_finder->finder_instance, &num_sources);
        
        if (!sources || num_sources == 0) {
            LOGD("未發現任何 NDI 源");
            return env->NewObjectArray(0, env->FindClass("java/lang/String"), nullptr);
        }
        
        LOGI("發現 %d 個 NDI 源", num_sources);
        
        // 創建 Java String 數組
        jclass string_class = env->FindClass("java/lang/String");
        jobjectArray result = env->NewObjectArray(num_sources, string_class, nullptr);
        
        for (uint32_t i = 0; i < num_sources; i++) {
            // 格式化源名稱字串
            std::string source_info = std::string(sources[i].p_ndi_name) + " (" + std::string(sources[i].p_url_address) + ")";
            
            jstring source_name = env->NewStringUTF(source_info.c_str());
            env->SetObjectArrayElement(result, i, source_name);
            env->DeleteLocalRef(source_name);
            
            LOGD("  - 源 %d: %s", i, source_info.c_str());
        }
        
        return result;
        
    } catch (const std::exception& e) {
        LOGE("掃描 NDI 源時發生異常: %s", e.what());
        return nullptr;
    }
#endif
}

/**
 * 取得 NDI SDK 版本資訊
 */
JNIEXPORT jstring JNICALL
Java_org_tpeyh_androidndimonitor_ndi_NDIDiscoveryManager_nativeGetVersion(JNIEnv *env, jobject thiz) {
#ifdef NDI_MOCK_IMPLEMENTATION
    return env->NewStringUTF("Mock NDI Implementation v1.0");
#else
    const char* version = NDIlib_version();
    return env->NewStringUTF(version ? version : "Unknown");
#endif
}

/**
 * 清理 NDI 資源
 */
JNIEXPORT void JNICALL
Java_org_tpeyh_androidndimonitor_ndi_NDIDiscoveryManager_nativeCleanup(JNIEnv *env, jobject thiz) {
    LOGI("清理 NDI 資源");
    
    if (g_ndi_finder) {
#ifndef NDI_MOCK_IMPLEMENTATION
        if (g_ndi_finder->finder_instance) {
            NDIlib_find_destroy(g_ndi_finder->finder_instance);
        }
        NDIlib_destroy();
#endif
        g_ndi_finder.reset();
    }
    
    LOGI("NDI 資源清理完成");
}

/**
 * JNI 載入函數
 */
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    LOGI("NDI JNI 函式庫載入");
    return JNI_VERSION_1_6;
}

// ===== NDI 接收器 JNI 函數 =====

/**
 * 初始化 NDI 接收器
 */
JNIEXPORT jboolean JNICALL
Java_org_tpeyh_androidndimonitor_ndi_NDIReceiver_nativeInitializeReceiver(JNIEnv *env, jobject thiz) {
    LOGI("初始化 NDI 接收器...");
    
#ifdef NDI_MOCK_IMPLEMENTATION
    LOGI("使用模擬 NDI 接收器");
    return JNI_TRUE;
#else
    // 實際 NDI 接收器初始化邏輯
    LOGI("NDI 接收器初始化成功");
    return JNI_TRUE;
#endif
}

/**
 * 連接到 NDI 源
 */
JNIEXPORT jboolean JNICALL
Java_org_tpeyh_androidndimonitor_ndi_NDIReceiver_nativeConnect(JNIEnv *env, jobject thiz, jstring sourceName, jstring urlAddress) {
    const char* sourceNameStr = env->GetStringUTFChars(sourceName, nullptr);
    const char* urlAddressStr = env->GetStringUTFChars(urlAddress, nullptr);
    
    LOGI("連接到 NDI 源: %s @ %s", sourceNameStr, urlAddressStr);
    
#ifdef NDI_MOCK_IMPLEMENTATION
    LOGI("模擬連接成功");
    env->ReleaseStringUTFChars(sourceName, sourceNameStr);
    env->ReleaseStringUTFChars(urlAddress, urlAddressStr);
    return JNI_TRUE;
#else
    // 實際 NDI 連接邏輯
    env->ReleaseStringUTFChars(sourceName, sourceNameStr);
    env->ReleaseStringUTFChars(urlAddress, urlAddressStr);
    return JNI_TRUE;
#endif
}

/**
 * 斷開 NDI 連接
 */
JNIEXPORT void JNICALL
Java_org_tpeyh_androidndimonitor_ndi_NDIReceiver_nativeDisconnect(JNIEnv *env, jobject thiz) {
    LOGI("斷開 NDI 連接");
    
#ifdef NDI_MOCK_IMPLEMENTATION
    LOGI("模擬斷開成功");
#else
    // 實際 NDI 斷開邏輯
#endif
}

/**
 * 接收 NDI 幀
 */
JNIEXPORT jbyteArray JNICALL
Java_org_tpeyh_androidndimonitor_ndi_NDIReceiver_nativeReceiveFrame(JNIEnv *env, jobject thiz, jint timeoutMs) {
#ifdef NDI_MOCK_IMPLEMENTATION
    // 模擬幀數據 (簡單的 RGBA 數據)
    const int width = 1920;
    const int height = 1080;
    const int dataSize = width * height * 4; // RGBA
    
    jbyteArray result = env->NewByteArray(dataSize);
    if (result) {
        // 填充模擬數據 (藍色游射)
        std::vector<uint8_t> mockData(dataSize);
        for (int i = 0; i < dataSize; i += 4) {
            mockData[i] = 100;     // R
            mockData[i + 1] = 150; // G  
            mockData[i + 2] = 255; // B
            mockData[i + 3] = 255; // A
        }
        
        env->SetByteArrayRegion(result, 0, dataSize, reinterpret_cast<const jbyte*>(mockData.data()));
        LOGV("返回模擬幀數據: %d 字節", dataSize);
    }
    
    return result;
#else
    // 實際 NDI 幀接收邏輯
    LOGD("接收 NDI 幀 (超時: %d ms)", timeoutMs);
    return nullptr;
#endif
}

/**
 * 清理 NDI 接收器資源
 */
JNIEXPORT void JNICALL
Java_org_tpeyh_androidndimonitor_ndi_NDIReceiver_nativeCleanupReceiver(JNIEnv *env, jobject thiz) {
    LOGI("清理 NDI 接收器資源");
    
#ifdef NDI_MOCK_IMPLEMENTATION
    LOGI("模擬接收器清理完成");
#else
    // 實際 NDI 接收器清理邏輯
#endif
}

/**
 * JNI 卸載函數
 */
JNIEXPORT void JNICALL JNI_OnUnload(JavaVM* vm, void* reserved) {
    LOGI("NDI JNI 函式庫卸載");
    
    // 確保資源被清理
    if (g_ndi_finder) {
#ifndef NDI_MOCK_IMPLEMENTATION
        if (g_ndi_finder->finder_instance) {
            NDIlib_find_destroy(g_ndi_finder->finder_instance);
        }
        NDIlib_destroy();
#endif
        g_ndi_finder.reset();
    }
}

} // extern "C"