package org.tpeyh.androidndimonitor.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.tpeyh.androidndimonitor.R
import org.tpeyh.androidndimonitor.ndi.NDIDiscoveryManager
import org.tpeyh.androidndimonitor.ndi.NDISource

/**
 * NDI 源瀏覽片段
 * 顯示發現的 NDI 源列表，支援 Android TV 導航
 */
class NDISourceBrowseFragment : Fragment() {
    
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: NDISourceAdapter
    private lateinit var discoveryManager: NDIDiscoveryManager
    
    companion object {
        private const val TAG = "NDISourceBrowseFragment"
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_ndi_source_browse, container, false)
        
        // 初始化 RecyclerView
        recyclerView = view.findViewById(R.id.ndi_sources_recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(context)
        
        // 初始化適配器
        adapter = NDISourceAdapter { ndiSource ->
            onSourceSelected(ndiSource)
        }
        recyclerView.adapter = adapter
        
        // 初始化 NDI 發現管理器
        discoveryManager = NDIDiscoveryManager.getInstance()
        
        return view
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupObservers()
        initializeNDI()
    }
    
    /**
     * 設置觀察者
     */
    private fun setupObservers() {
        // 觀察 NDI 源列表變化
        discoveryManager.sources.observe(viewLifecycleOwner, Observer { sources ->
            Log.d(TAG, "收到 NDI 源更新: ${sources.size} 個源")
            adapter.updateSources(sources)
            
            if (sources.isEmpty()) {
                showEmptyState()
            } else {
                hideEmptyState()
            }
        })
        
        // 觀察掃描狀態
        discoveryManager.isScanning.observe(viewLifecycleOwner, Observer { isScanning ->
            Log.d(TAG, "掃描狀態: $isScanning")
            updateScanningStatus(isScanning)
        })
        
        // 觀察錯誤狀態
        discoveryManager.connectionError.observe(viewLifecycleOwner, Observer { error ->
            error?.let {
                Log.e(TAG, "NDI 連接錯誤: $it")
                showError(it)
            }
        })
        
        // 觀察初始化狀態
        discoveryManager.isInitialized.observe(viewLifecycleOwner, Observer { isInitialized ->
            Log.d(TAG, "NDI 初始化狀態: $isInitialized")
            if (isInitialized) {
                startScanning()
            }
        })
    }
    
    /**
     * 初始化 NDI
     */
    private fun initializeNDI() {
        Log.i(TAG, "開始初始化 NDI...")
        
        if (!discoveryManager.initialize()) {
            showError("NDI 初始化失敗，請檢查網路連接和權限設置")
        }
    }
    
    /**
     * 開始掃描
     */
    private fun startScanning() {
        Log.i(TAG, "開始掃描 NDI 源...")
        discoveryManager.startScanning()
    }
    
    /**
     * NDI 源被選中
     */
    private fun onSourceSelected(source: NDISource) {
        Log.i(TAG, "選中 NDI 源: ${source.name}")
        
        Toast.makeText(
            context,
            "已選擇: ${source.getDisplayName()}",
            Toast.LENGTH_SHORT
        ).show()
        
        // TODO: 實作視頻播放功能
        // 暫時只顯示源信息
        showSourceInfo(source)
    }
    
    /**
     * 顯示源信息
     */
    private fun showSourceInfo(source: NDISource) {
        val info = """
            名稱: ${source.getDisplayName()}
            類型: ${source.sourceType.name}
            狀態: ${source.connectionStatus.name}
            描述: ${source.getShortDescription()}
            在線: ${if (source.isOnline) "是" else "否"}
        """.trimIndent()
        
        Toast.makeText(context, info, Toast.LENGTH_LONG).show()
    }
    
    /**
     * 顯示空狀態
     */
    private fun showEmptyState() {
        // TODO: 實作空狀態視圖
        Log.d(TAG, "顯示空狀態")
    }
    
    /**
     * 隱藏空狀態
     */
    private fun hideEmptyState() {
        // TODO: 隱藏空狀態視圖
        Log.d(TAG, "隱藏空狀態")
    }
    
    /**
     * 更新掃描狀態指示器
     */
    private fun updateScanningStatus(isScanning: Boolean) {
        // TODO: 更新 UI 掃描狀態指示器
        Log.d(TAG, "更新掃描狀態: $isScanning")
    }
    
    /**
     * 顯示錯誤訊息
     */
    private fun showError(message: String) {
        Toast.makeText(context, "錯誤: $message", Toast.LENGTH_LONG).show()
    }
    
    /**
     * 手動重新整理
     */
    fun refresh() {
        Log.i(TAG, "手動重新整理")
        discoveryManager.refresh()
    }
    
    override fun onResume() {
        super.onResume()
        Log.d(TAG, "Fragment 已恢復")
        
        // 如果已初始化但未在掃描，則開始掃描
        if (discoveryManager.isInitialized.value == true && 
            discoveryManager.isScanning.value != true) {
            startScanning()
        }
    }
    
    override fun onPause() {
        super.onPause()
        Log.d(TAG, "Fragment 已暫停")
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(TAG, "Fragment 視圖銷毀")
        
        // 停止掃描以節省資源
        discoveryManager.stopScanning()
    }
}