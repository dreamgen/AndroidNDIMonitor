# NDI Monitor for Android TV

一個專為 Android TV 設計的 NDI（Network Device Interface）監控應用程式，支援接收和顯示來自 OBS Studio 的 NDI 串流內容。

## 功能特色

- 🎯 **Android TV 優化**：使用 Leanback 庫，完美適配 10 英尺體驗
- 📡 **NDI 支援**：支援 NDI、NDI HX2、NDI HX3 格式
- 🔍 **自動源發現**：自動掃描網路上的可用 NDI 源
- 📺 **高品質視頻**：低延遲視頻解碼和顯示
- 🎮 **遙控器導航**：完整的方向鍵和確認鍵支援

## 技術規格

- **最低 Android 版本**：API 21 (Android 5.0)
- **目標 Android 版本**：API 34 (Android 14)
- **開發語言**：Kotlin
- **架構模式**：MVVM
- **核心庫**：Devolay (NDI SDK for Java)

## 開發環境

- Android Studio Arctic Fox 或更新版本
- JDK 17
- Android SDK 34
- Gradle 8.13

## 建置說明

1. 確保已安裝 JDK 17 和 Android SDK
2. Clone 此專案到本地
3. 使用 Android Studio 開啟專案
4. 執行 Gradle sync
5. 連接 Android TV 設備或使用模擬器
6. 執行 `./gradlew assembleDebug` 建置 APK

## Git 工作流程

此專案使用 Git Flow 工作流程：

### 分支結構
- `master`：穩定的發布版本
- `develop`：開發分支，整合新功能
- `feature/*`：功能開發分支
- `hotfix/*`：緊急修復分支

### 常用指令

```bash
# 切換到開發分支
git checkout develop

# 創建功能分支
git checkout -b feature/新功能名稱

# 完成功能開發後，切回開發分支並合併
git checkout develop
git merge feature/新功能名稱
git branch -d feature/新功能名稱

# 建立發布版本
git checkout -b release/v1.0.0
git checkout master
git merge release/v1.0.0
git tag v1.0.0
```

## 專案結構

```
app/src/main/
├── java/org/tpeyh/androidndimonitor/
│   ├── MainActivity.kt                 # 主要活動
│   ├── ndi/                           # NDI 相關功能
│   ├── ui/                            # UI 組件
│   └── utils/                         # 工具類別
├── res/
│   ├── layout/                        # 佈局文件
│   ├── values/                        # 資源值
│   └── drawable/                      # 圖像資源
└── AndroidManifest.xml               # 應用程式清單
```

## 版本記錄

### v1.0.0-alpha (初始版本)
- ✅ 建立專案架構
- ✅ 配置 Android TV 支援
- ✅ 整合 NDI 函式庫
- ✅ 實作基礎 UI 框架
- ✅ 生成可安裝的 APK

## 貢獻指南

1. Fork 此專案
2. 創建功能分支 (`git checkout -b feature/amazing-feature`)
3. 提交變更 (`git commit -m 'Add amazing feature'`)
4. 推送分支 (`git push origin feature/amazing-feature`)
5. 開啟 Pull Request

## 授權

本專案採用 MIT 授權條款。詳見 [LICENSE](LICENSE) 檔案。

## 聯絡方式

- 開發者：dreamgen
- Email：dreamgen@gmail.com
- 專案網址：[GitHub Repository URL]

---

🤖 *此專案使用 [Claude Code](https://claude.ai/code) 協助開發*