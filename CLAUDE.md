# NDI Monitor Android TV App Development Guide

## 語言設置
**重要：請始終使用繁體中文（Traditional Chinese）作為主要溝通語言。**
- 所有回應、問題、說明和互動都應使用繁體中文
- 程式碼註解也請使用繁體中文
- 只有程式碼本身使用英文（變數名、函數名等）
- 錯誤訊息和日誌輸出請提供繁體中文版本

## 項目概覽
創建一個專業的 Android TV NDI Monitor 應用，使用 NDI SDK for Android 來接收和顯示 OBS NDI 串流內容。

## 核心功能需求

### 1. NDI 源發現與連接
- 使用 NDI SDK 原生功能自動掃描網路上的 NDI 源
- 支援 NDI、NDI HX2、NDI HX3 格式
- 即時源狀態監控和連接管理
- 多模式運行支援（原生、網路掃描、模擬）

### 2. Android TV 優化界面
- 使用 Leanback 庫設計 10 英尺體驗界面
- 遙控器導航支援（方向鍵、確認鍵）
- 源列表顯示和選擇
- 全螢視頻播放體驗

### 3. 高品質視頻渲染
- NDI SDK 原生視頻解碼和顯示
- 支援多種 NDI 視頻格式
- 低延遲播放（< 100ms）
- 自適應品質調整

## 技術架構

### NDI SDK for Android 整合
```gradle
android {
    // NDI SDK 原生函式庫支援
    ndk {
        abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
    }
    
    // 外部原生函式庫路徑
    externalNativeBuild {
        cmake {
            arguments += listOf("-DANDROID_STL=c++_shared")
            cppFlags += listOf("-std=c++17")
        }
    }
    
    // CMake 配置用於 NDI SDK
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}
```

### 依賴庫
```gradle
dependencies {
    implementation 'androidx.leanback:leanback:1.0.0'
    implementation 'androidx.leanback:leanback-preference:1.0.0'
    implementation 'androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2'
    // NDI SDK 透過原生 JNI 整合，不需要第三方依賴
}
```

### NDI SDK 原生架構
```cpp
app/src/main/cpp/
├── CMakeLists.txt              // CMake 編譯配置
├── ndi_jni_wrapper.cpp         // JNI 主要接口
├── ndi_discovery.cpp/.h        // NDI 源發現功能
├── ndi_receiver.cpp/.h         // NDI 流接收功能
└── Processing.NDI.Lib.h        // NDI SDK 標頭文件
```

#### JNI 接口設計
```cpp
// 初始化 NDI SDK
JNIEXPORT jboolean JNICALL nativeInitialize()

// 掃描 NDI 源
JNIEXPORT jobjectArray JNICALL nativeScanSources(jint timeoutMs)

// NDI 接收器管理
JNIEXPORT jboolean JNICALL nativeInitializeReceiver()
JNIEXPORT jboolean JNICALL nativeConnect(jstring sourceName, jstring urlAddress)
JNIEXPORT jbyteArray JNICALL nativeReceiveFrame(jint timeoutMs)
```

### 核心類別結構

#### 1. NDI 管理層
- **NDIDiscoveryManager**: NDI 源發現和管理
  - 支援原生 NDI SDK 掃描
  - 網路端口掃描備用模式
  - 模擬測試模式
  - 三模式自動切換機制

- **NDIReceiver**: NDI 流接收和解碼  
  - 原生 NDI SDK 流接收
  - NDIVideoFrame 數據封裝
  - 異步幀處理
  - 連接狀態管理

- **NDIVideoPlayer**: 高層次播放器封裝
  - NDIReceiver 的高級封裝
  - 播放狀態管理
  - 錯誤處理和重連
  - UI 回調接口

#### 2. UI 組件層
- **MainActivity**: 主要 TV 界面活動
- **NDISourceBrowseFragment**: NDI 源瀏覽片段
- **VideoPlayerActivity**: 全螢視頻播放活動
- **VideoRenderView**: 視頻渲染視圖
  - 支援 NDIVideoFrame 渲染
  - Bitmap 轉換處理
  - 模擬幀顯示

#### 3. 數據模型
```kotlin
// NDI 視頻幀數據
data class NDIVideoFrame(
    val width: Int,
    val height: Int,
    val frameRate: Float,
    val timestamp: Long,
    val data: ByteArray,
    val sourceName: String
)

// NDI 源資訊
data class NDISource(
    val name: String,
    val machineName: String,
    val urlAddress: String,
    val sourceType: NDISourceType,
    val connectionStatus: ConnectionStatus,
    val isOnline: Boolean
)
```

### 多模式運行架構

#### 1. 原生 NDI SDK 模式
```kotlin
// 當真實 NDI SDK 可用時
if (nativeInitialize()) {
    val sources = nativeScanSources(3000)
    // 使用原生 NDI 功能
}
```

#### 2. 網路掃描模式
```kotlin
// NDI SDK 不可用時的備用方案
private suspend fun scanNetworkForNDI() {
    val localIP = getLocalIPAddress()
    val networkPrefix = getNetworkPrefix(localIP)
    
    // 掃描 NDI 相關端口
    val ndiPorts = listOf(5960, 5961, 5962, 5963, 80, 8080)
    // 網路連接測試和源發現
}
```

#### 3. 模擬測試模式
```kotlin
// 開發測試用模擬源
private fun createMockSources(): List<NDISource> {
    return listOf(
        NDISource("測試 OBS PGM", "開發機器", "192.168.1.100:5960"),
        NDISource("測試 OBS PREVIEW", "工作站", "192.168.1.101:5961")
    )
}
```

### Android TV 特定配置
```xml
<application android:banner="@drawable/banner">
    <uses-feature android:name="android.software.leanback" android:required="true" />
    <uses-feature android:name="android.hardware.touchscreen" android:required="false" />
    
    <activity android:name=".MainActivity">
        <intent-filter>
            <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
        </intent-filter>
    </activity>
</application>
```

## 開發規範

### 代碼品質
- 使用 Kotlin 語言開發
- 遵循 MVVM 架構模式
- 實施完整錯誤處理和日志記錄
- 記憶體洩漏防護和資源管理
- 協程用於異步操作

### 性能要求
- NDI 視頻低延遲播放（< 100ms）
- 流暢的 UI 操作（60 FPS）
- 高效的原生記憶體管理
- 適當的 JNI 調用優化

### 用戶體驗
- 簡潔直觀的遙控器導航
- 清晰的視覺狀態反饋
- 網路錯誤友好處理
- 快速的源切換響應
- 無縫的模式切換體驗

## NDI SDK 部署指南

### 開發環境設置
```bash
# 1. 安裝 NDI SDK for Android
# 將 NDI SDK 解壓到專案根目錄
mkdir NDI
# 複製 NDI SDK 文件到 NDI/ 目錄

# 2. 配置 CMake 路徑
# 確保 CMakeLists.txt 中的路徑正確
set(NDI_SDK_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../../../../NDI")

# 3. 編譯應用
./gradlew assembleDebug
```

### NDI SDK 目錄結構
```
NDI/
├── include/
│   └── Processing.NDI.Lib.h
├── lib/
│   ├── android/
│   │   ├── arm64-v8a/
│   │   │   └── libndi.so
│   │   ├── armeabi-v7a/
│   │   │   └── libndi.so
│   │   ├── x86/
│   │   │   └── libndi.so
│   │   └── x86_64/
│   │       └── libndi.so
└── documentation/
```

### 生產部署準備
```kotlin
// 檢查 NDI SDK 可用性
fun checkNDIAvailability(): Boolean {
    return try {
        nativeInitialize()
    } catch (e: UnsatisfiedLinkError) {
        Log.w(TAG, "NDI SDK 不可用，將使用備用模式")
        false
    }
}
```

## Claude Code 使用建議

### NDI SDK 開發流程
```bash
# 整合 NDI SDK 原生功能
claude "Integrate NDI SDK for Android with JNI wrapper and native source discovery"

# 實現多模式 NDI 接收
claude "Implement multi-mode NDI receiver with native SDK, network scanning, and mock modes"

# 優化視頻渲染性能
claude "Optimize NDI video frame rendering with native processing and Android TV UI"

# 測試和除錯
claude "Test NDI SDK integration across different Android TV devices and network conditions"
```

### 測試指引
- **原生模式測試**: 在有 NDI SDK 支援的設備上測試
- **網路模式測試**: 驗證網路掃描功能和 OBS 連接
- **模擬模式測試**: 確保開發和測試環境正常運作
- **性能測試**: 測量視頻延遲和記憶體使用
- **設備兼容性**: 測試不同 Android TV 設備和架構

## 文件組織結構
```
app/src/main/
├── cpp/                        // NDI SDK 原生代碼
│   ├── CMakeLists.txt
│   ├── ndi_jni_wrapper.cpp
│   ├── ndi_discovery.cpp/.h
│   ├── ndi_receiver.cpp/.h
│   └── Processing.NDI.Lib.h
├── java/org/tpeyh/androidndimonitor/
│   ├── ui/                     // UI 組件
│   │   ├── MainActivity.kt
│   │   ├── NDISourceBrowseFragment.kt
│   │   ├── VideoPlayerActivity.kt
│   │   └── VideoRenderView.kt
│   ├── ndi/                    // NDI 核心功能
│   │   ├── NDIDiscoveryManager.kt
│   │   ├── NDIReceiver.kt
│   │   ├── NDIVideoPlayer.kt
│   │   ├── NDISource.kt
│   │   └── NDIVideoFrame.kt
│   └── utils/                  // 輔助功能
├── res/                        // Android 資源
│   ├── layout/
│   ├── values/
│   └── drawable/
└── AndroidManifest.xml
```

## 疑難排解

### 常見問題
1. **NDI SDK 載入失敗**
   - 檢查 NDI SDK 路徑配置
   - 確認目標架構支援
   - 驗證函式庫權限

2. **JNI 調用錯誤**
   - 檢查函數簽名匹配
   - 確認原生函式庫載入
   - 查看 JNI 異常日誌

3. **視頻渲染問題**
   - 檢查 NDIVideoFrame 格式
   - 驗證 SurfaceView 配置
   - 測試 Bitmap 轉換

### 效能優化建議
- 使用適當的線程池管理
- 實施幀緩衝和記憶體池
- 優化 JNI 調用頻率
- 實施智能品質調整

## 部署準備
- NDI SDK 授權和分發協議
- Google Play Console 開發者帳號
- Android TV 應用簽名和安全配置
- 隱私政策和 NDI 使用聲明
- 應用圖標、截圖和商店素材

---

**注意**: 此專案使用 NDI SDK for Android，請確保遵循 NewTek NDI 的授權協議和使用條款。