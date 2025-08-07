package org.tpeyh.androidndimonitor.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.tpeyh.androidndimonitor.R
import org.tpeyh.androidndimonitor.ndi.NDISource

/**
 * NDI 源列表適配器
 * 為 RecyclerView 提供 NDI 源數據顯示
 */
class NDISourceAdapter(
    private val onSourceClick: (NDISource) -> Unit
) : RecyclerView.Adapter<NDISourceAdapter.NDISourceViewHolder>() {
    
    private var sources = listOf<NDISource>()
    
    /**
     * NDI 源視圖持有者
     */
    class NDISourceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val sourceName: TextView = itemView.findViewById(R.id.ndi_source_name)
        val sourceMachine: TextView = itemView.findViewById(R.id.ndi_source_machine)
        val sourceStatus: TextView = itemView.findViewById(R.id.ndi_source_status)
        val sourceType: TextView = itemView.findViewById(R.id.ndi_source_type)
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NDISourceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.ndi_source_item, parent, false)
        return NDISourceViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: NDISourceViewHolder, position: Int) {
        val source = sources[position]
        
        // 設置源名稱
        holder.sourceName.text = source.name
        
        // 設置機器名稱
        holder.sourceMachine.text = source.machineName
        
        // 設置狀態
        holder.sourceStatus.text = getStatusText(source)
        holder.sourceStatus.setTextColor(getStatusColor(holder.itemView.context, source))
        
        // 設置源類型
        holder.sourceType.text = source.sourceType.name
        
        // 設置點擊事件
        holder.itemView.setOnClickListener {
            onSourceClick(source)
        }
        
        // 設置焦點效果（Android TV）
        holder.itemView.isFocusable = true
        holder.itemView.isClickable = true
    }
    
    override fun getItemCount(): Int = sources.size
    
    /**
     * 更新源列表
     */
    fun updateSources(newSources: List<NDISource>) {
        sources = newSources
        notifyDataSetChanged()
    }
    
    /**
     * 構建源信息文字
     */
    private fun buildSourceInfo(source: NDISource): String {
        val parts = mutableListOf<String>()
        
        // 添加源類型
        parts.add(source.sourceType.name)
        
        // 添加解析度信息
        if (source.videoWidth > 0 && source.videoHeight > 0) {
            parts.add("${source.videoWidth}×${source.videoHeight}")
        }
        
        // 添加幀率信息
        if (source.frameRate > 0) {
            parts.add("${source.frameRate.toInt()}fps")
        }
        
        // 添加機器名稱
        if (source.machineName.isNotEmpty() && source.machineName != "unknown") {
            parts.add("來自: ${source.machineName}")
        }
        
        return parts.joinToString(" • ")
    }
    
    /**
     * 取得狀態文字
     */
    private fun getStatusText(source: NDISource): String {
        return when {
            !source.isOnline -> "離線"
            !source.isAvailable() -> "不可用"
            else -> when (source.connectionStatus) {
                org.tpeyh.androidndimonitor.ndi.ConnectionStatus.CONNECTED -> "已連接"
                org.tpeyh.androidndimonitor.ndi.ConnectionStatus.CONNECTING -> "連接中"
                org.tpeyh.androidndimonitor.ndi.ConnectionStatus.CONNECTION_FAILED -> "連接失敗"
                org.tpeyh.androidndimonitor.ndi.ConnectionStatus.NETWORK_ERROR -> "網路錯誤"
                org.tpeyh.androidndimonitor.ndi.ConnectionStatus.SOURCE_UNAVAILABLE -> "源不可用"
                else -> "可用"
            }
        }
    }
    
    /**
     * 取得狀態顏色
     */
    private fun getStatusColor(context: android.content.Context, source: NDISource): Int {
        val colorRes = when {
            !source.isOnline -> android.R.color.holo_red_dark
            !source.isAvailable() -> android.R.color.holo_orange_dark
            source.connectionStatus == org.tpeyh.androidndimonitor.ndi.ConnectionStatus.CONNECTED -> 
                android.R.color.holo_green_dark
            source.connectionStatus == org.tpeyh.androidndimonitor.ndi.ConnectionStatus.CONNECTING -> 
                android.R.color.holo_blue_dark
            else -> android.R.color.white
        }
        
        return androidx.core.content.ContextCompat.getColor(context, colorRes)
    }
}