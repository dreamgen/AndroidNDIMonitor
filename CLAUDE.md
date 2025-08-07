# NDI Monitor Android TV App Development Guide

## 語言設置
**重要：請始終使用繁體中文（Traditional Chinese）作為主要溝通語言。**
- 所有回應、問題、說明和互動都應使用繁體中文
- 程式碼註解也請使用繁體中文
- 只有程式碼本身使用英文（變數名、函數名等）
- 錯誤訊息和日誌輸出請提供繁體中文版本

## 項目概覽
創建一個專業的 Android TV NDI Monitor 應用，用於接收和顯示 OBS NDI 串流內容。

## 核心功能需求

### 1. NDI 源發現與連接
- 自動掃描網路上的 NDI 源
- 支援 NDI、NDI HX2、NDI HX3 格式
- 即時源狀態監控

### 2. Android TV 優化界面
- 使用 Leanback 庫設計 10 英尺體驗界面
- 遙控器導航支援（方向鍵、確認鍵）
- 源列表顯示和選擇

### 3. 視頻渲染
- 高品質視頻解碼和顯示
- 支援多種視頻格式轉換
- 低延遲播放

## 技術架構

### 依賴庫
```gradle
dependencies {
    implementation 'androidx.leanback:leanback:1.0.0'
    implementation 'androidx.leanback:leanback-preference:1.0.0'
    implementation 'me.walkerknapp:devolay:2.1.0'
    implementation 'androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2'
}
```

### 核心類別結構
1. **NDIDiscoveryManager**: NDI 源發現和管理
2. **NDIReceiver**: NDI 流接收和解碼
3. **MainTVActivity**: 主要 TV 界面活動
4. **NDISourceBrowseFragment**: NDI 源瀏覽片段
5. **VideoRenderView**: 視頻渲染視圖

### Android TV 特定配置
```xml
<uses-feature android:name="android.software.leanback" android:required="true" />
<uses-feature android:name="android.hardware.touchscreen" android:required="false" />
<category android:name="android.intent.category.LEANBACK_LAUNCHER" />
```

## 開發規範

### 代碼品質
- 使用 Kotlin 語言
- 遵循 MVVM 架構模式
- 實施錯誤處理和日志記錄
- 內存洩漏防護

### 性能要求
- 低延遲視頻播放（< 100ms）
- 流暢的 UI 操作（60 FPS）
- 高效的網路資源使用
- 適當的資源回收

### 用戶體驗
- 簡潔直觀的遙控器導航
- 清晰的視覺反饋
- 錯誤狀態友好提示
- 快速的源切換響應

## Claude Code 使用建議

### 開發流程命令
```bash
# 初始化項目結構
claude "Set up Android TV project with NDI monitoring capabilities using Leanback library"

# 添加核心功能
claude "Implement NDI source discovery using Devolay library with automatic network scanning"

# 創建 UI 組件  
claude "Create TV-optimized user interface for NDI source selection and video playback"

# 性能優化
claude "Optimize video rendering performance and implement proper resource management"
```

### 測試指引
- 在實體 Android TV 設備上測試
- 驗證不同 NDI 源的兼容性
- 測試網路異常情況處理
- 確認遙控器操作流暢性

## 文件組織
```
app/src/main/
├── java/com/yourname/ndimonitor/
│   ├── ui/
│   │   ├── MainActivity.kt
│   │   ├── NDISourceBrowseFragment.kt
│   │   └── VideoRenderView.kt
│   ├── ndi/
│   │   ├── NDIDiscoveryManager.kt
│   │   └── NDIReceiver.kt
│   └── utils/
├── res/
│   ├── layout/
│   ├── values/
│   └── drawable/
└── AndroidManifest.xml
```

## 部署準備
- Google Play Console 開發者帳號
- Android TV 應用簽名
- 隱私政策和用戶協議
- 應用圖標和截圖素材