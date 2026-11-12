package com.ciweihaha.tvappstore.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.ciweihaha.tvappstore.R

class CategoryAdapter(
    private val onSelect: (String?) -> Unit
) : RecyclerView.Adapter<CategoryAdapter.ViewHolder>() {

    /** null 表示“全部” */
    private var categories: List<String?> = listOf(null)
    private var selected: String? = null

    fun submitList(categories: List<String>) {
        this.categories = listOf(null) + categories
        notifyDataSetChanged()
    }

    fun setSelected(category: String?) {
        val old = selected
        selected = category
        categories.indexOfFirst { it == old }.takeIf { it >= 0 }?.let { notifyItemChanged(it) }
        categories.indexOfFirst { it == category }.takeIf { it >= 0 }?.let { notifyItemChanged(it) }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_category, parent, false)
        return ViewHolder(view, onSelect)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(categories[position], categories[position] == selected)
    }

    override fun getItemCount(): Int = categories.size

    class ViewHolder(
        itemView: View,
        private val onSelect: (String?) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val text: TextView = itemView.findViewById(R.id.chipText)
        private var current: String? = null

        init {
            itemView.setOnClickListener { onSelect(current) }
        }

        fun bind(category: String?, isSelected: Boolean) {
            current = category
            text.text = category ?: itemView.context.getString(R.string.category_all)
            itemView.isSelected = isSelected
        }
    }
}
