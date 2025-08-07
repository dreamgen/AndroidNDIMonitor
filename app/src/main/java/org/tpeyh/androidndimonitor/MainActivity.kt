package org.tpeyh.androidndimonitor

import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import org.tpeyh.androidndimonitor.ndi.NDIDiscoveryManager
import org.tpeyh.androidndimonitor.ui.NDISourceBrowseFragment

/**
 * NDI Monitor 主活動
 * Android TV 優化的 NDI 源瀏覽器
 */
class MainActivity : FragmentActivity() {
    
    private lateinit var discoveryManager: NDIDiscoveryManager
    
    companion object {
        private const val TAG = "MainActivity"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        Log.i(TAG, "NDI Monitor 應用程式啟動")
        
        // 初始化 NDI 發現管理器
        discoveryManager = NDIDiscoveryManager.getInstance()
        
        // 設置應用標題
        val textView = findViewById<TextView>(R.id.app_title)
        textView?.text = "NDI Monitor for Android TV"
        
        // 載入 NDI 源瀏覽片段
        if (savedInstanceState == null) {
            loadNDISourceFragment()
        }
        
        Log.i(TAG, "應用程式初始化完成")
    }
    
    /**
     * 載入 NDI 源瀏覽片段
     */
    private fun loadNDISourceFragment() {
        Log.d(TAG, "載入 NDI 源瀏覽片段")
        
        val fragment = NDISourceBrowseFragment()
        
        supportFragmentManager.beginTransaction()
            .replace(R.id.main_content_frame, fragment)
            .commit()
    }
    
    override fun onResume() {
        super.onResume()
        Log.d(TAG, "MainActivity 已恢復")
        
        // 顯示統計信息
        val stats = discoveryManager.getSourceStats()
        Log.i(TAG, "NDI 統計: $stats")
    }
    
    override fun onPause() {
        super.onPause()
        Log.d(TAG, "MainActivity 已暫停")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "MainActivity 正在銷毀")
        
        // 清理 NDI 資源
        discoveryManager.cleanup()
    }
    
    /**
     * 處理返回鍵
     */
    override fun onBackPressed() {
        // 停止 NDI 掃描並退出
        discoveryManager.stopScanning()
        super.onBackPressed()
    }
}