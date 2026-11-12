package com.ciweihaha.tvappstore.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.ciweihaha.tvappstore.R
import com.ciweihaha.tvappstore.model.AppInfo
import com.ciweihaha.tvappstore.util.IconLoader
import java.util.Locale

class AppAdapter(
    private val onClick: (AppInfo) -> Unit
) : RecyclerView.Adapter<AppAdapter.ViewHolder>() {

    private var items: List<AppInfo> = emptyList()

    /** packageName -> 状态文案（下载中/失败等），由 Activity 更新 */
    private val statusMap = mutableMapOf<String, String>()

    fun submitList(list: List<AppInfo>) {
        items = list
        statusMap.clear()
        notifyDataSetChanged()
    }

    fun setStatus(packageName: String, status: String?) {
        if (status == null) statusMap.remove(packageName) else statusMap[packageName] = status
        val index = items.indexOfFirst { it.packageName == packageName }
        if (index >= 0) notifyItemChanged(index)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app, parent, false)
        return ViewHolder(view, onClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], statusMap[items[position].packageName])
    }

    override fun getItemCount(): Int = items.size

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
            size.text = app.size?.let { formatSize(it) } ?: ""
            IconLoader.load(icon, app.icon, R.drawable.ic_placeholder)
            if (statusText.isNullOrBlank()) {
                status.visibility = View.GONE
            } else {
                status.visibility = View.VISIBLE
                status.text = statusText
            }
        }

        private fun formatSize(bytes: Long): String {
            return when {
                bytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
                bytes >= 1024 -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
                else -> "$bytes B"
            }
        }
    }
}
