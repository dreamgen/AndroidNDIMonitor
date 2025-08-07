package org.tpeyh.androidndimonitor

import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.fragment.app.FragmentActivity

/**
 * NDI Monitor 主活動
 * Android TV 優化的 NDI 源瀏覽器
 */
class MainActivity : FragmentActivity() {
    
    companion object {
        private const val TAG = "MainActivity"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        Log.i(TAG, "NDI Monitor 應用程式啟動")
        
        // 顯示基本信息
        val textView = findViewById<TextView>(R.id.app_title)
        textView?.text = "NDI Monitor for Android TV"
        
        Log.i(TAG, "應用程式初始化完成")
    }
    
    override fun onResume() {
        super.onResume()
        Log.d(TAG, "MainActivity 已恢復")
    }
    
    override fun onPause() {
        super.onPause()
        Log.d(TAG, "MainActivity 已暫停")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "MainActivity 正在銷毀")
    }
}