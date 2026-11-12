package com.ciweihaha.tvappstore.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ciweihaha.tvappstore.R
import com.ciweihaha.tvappstore.model.AppInfo
import com.ciweihaha.tvappstore.util.FormatUtils
import com.ciweihaha.tvappstore.util.IconLoader

/**
 * 应用网格。ListAdapter + DiffUtil：切换分类时只更新变化的项，
 * 不再全量 notifyDataSetChanged（2900 条目录时全量重绘会卡一下，
 * 还会丢掉电视遥控器的焦点）。
 */
class AppAdapter(
    private val onClick: (AppInfo) -> Unit
) : ListAdapter<AppInfo, AppAdapter.ViewHolder>(AppDiffCallback) {

    /** packageName -> 状态文案（下载中/失败等），由 Activity 更新 */
    private val statusMap = mutableMapOf<String, String>()

    init {
        // 稳定 ID：配合 DiffUtil，过滤/刷新后焦点尽量留在同一个应用上
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long =
        getItem(position).packageName.hashCode().toLong()

    fun setStatus(packageName: String, status: String?) {
        if (status == null) statusMap.remove(packageName) else statusMap[packageName] = status
        val index = currentList.indexOfFirst { it.packageName == packageName }
        if (index >= 0) notifyItemChanged(index)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app, parent, false)
        return ViewHolder(view, onClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = getItem(position)
        holder.bind(app, statusMap[app.packageName])
    }

    object AppDiffCallback : DiffUtil.ItemCallback<AppInfo>() {
        override fun areItemsTheSame(oldItem: AppInfo, newItem: AppInfo): Boolean =
            oldItem.packageName == newItem.packageName

        override fun areContentsTheSame(oldItem: AppInfo, newItem: AppInfo): Boolean =
            oldItem == newItem
    }

    class ViewHolder(
        itemView: View,
        private val onClick: (AppInfo) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val icon: ImageView = itemView.findViewById(R.id.appIcon)
        private val name: TextView = itemView.findViewById(R.id.appName)
        private val version: TextView = itemView.findViewById(R.id.appVersion)
        private val size: TextView = itemView.findViewById(R.id.appSize)
        private val status: TextView = itemView.findViewById(R.id.appStatus)
        private var current: AppInfo? = null

        init {
            itemView.setOnClickListener { current?.let(onClick) }
        }

        fun bind(app: AppInfo, statusText: String?) {
            current = app
            name.text = app.displayName
            version.text = app.versionName?.let { "版本 $it" } ?: ""
            size.text = app.size?.let { FormatUtils.formatSize(it) } ?: ""
            IconLoader.load(icon, app.icon, R.drawable.ic_placeholder)
            if (statusText.isNullOrBlank()) {
                status.visibility = View.GONE
            } else {
                status.visibility = View.VISIBLE
                status.text = statusText
            }
        }
    }
}
