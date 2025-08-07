# CMake 配置文件用於 NDI SDK Android 整合
cmake_minimum_required(VERSION 3.22.1)

# 專案名稱
project("ndi_android")

# C++17 標準
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# NDI SDK 路徑檢測和配置
set(NDI_SDK_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../../../../NDI")
set(LOCAL_NDI_HEADER "${CMAKE_CURRENT_SOURCE_DIR}/Processing.NDI.Lib.h")

# 檢查是否有本地 NDI SDK
if(EXISTS "${NDI_SDK_ROOT}/include/Processing.NDI.Lib.h")
    message(STATUS "找到外部 NDI SDK: ${NDI_SDK_ROOT}")
    
    # 添加 NDI SDK 包含目錄
    include_directories(${NDI_SDK_ROOT}/include)
    
    # 根據 Android ABI 選擇對應的 NDI 函式庫
    if(${ANDROID_ABI} STREQUAL "arm64-v8a")
        set(NDI_LIB_PATH "${NDI_SDK_ROOT}/lib/android/arm64-v8a")
    elseif(${ANDROID_ABI} STREQUAL "armeabi-v7a") 
        set(NDI_LIB_PATH "${NDI_SDK_ROOT}/lib/android/armeabi-v7a")
    elseif(${ANDROID_ABI} STREQUAL "x86_64")
        set(NDI_LIB_PATH "${NDI_SDK_ROOT}/lib/android/x86_64")
    elseif(${ANDROID_ABI} STREQUAL "x86")
        set(NDI_LIB_PATH "${NDI_SDK_ROOT}/lib/android/x86")
    endif()
    
    # 尋找 NDI 函式庫
    find_library(NDI_LIBRARY
        NAMES libndi ndi
        PATHS ${NDI_LIB_PATH}
        NO_DEFAULT_PATH
    )
    
    if(NDI_LIBRARY)
        message(STATUS "找到 NDI 函式庫: ${NDI_LIBRARY}")
    else()
        message(WARNING "未找到 NDI 函式庫，使用模擬實作")
        add_definitions(-DNDI_MOCK_IMPLEMENTATION)
    endif()
    
elseif(EXISTS "${LOCAL_NDI_HEADER}")
    message(STATUS "使用本地 NDI 模擬標頭: ${LOCAL_NDI_HEADER}")
    add_definitions(-DNDI_MOCK_IMPLEMENTATION)
    
else()
    message(WARNING "未找到任何 NDI SDK 或模擬標頭，使用預設模擬實作")
    add_definitions(-DNDI_MOCK_IMPLEMENTATION)
endif()

# 添加原始檔案
add_library(ndi_android SHARED
    ndi_jni_wrapper.cpp
    ndi_discovery.cpp
    ndi_receiver.cpp
)

# 連結函式庫
target_link_libraries(ndi_android
    android
    log
)

# 如果找到 NDI 函式庫則連結
if(NDI_LIBRARY AND NOT DEFINED NDI_MOCK_IMPLEMENTATION)
    target_link_libraries(ndi_android ${NDI_LIBRARY})
    message(STATUS "連結到真實 NDI 函式庫")
else()
    message(STATUS "使用模擬 NDI 實作")
endif()

# 編譯器標誌
target_compile_options(ndi_android PRIVATE
    -Wall
    -Wextra
    -fvisibility=hidden
)

# 定義版本資訊
target_compile_definitions(ndi_android PRIVATE
    VERSION_MAJOR=1
    VERSION_MINOR=0
    VERSION_PATCH=0
)