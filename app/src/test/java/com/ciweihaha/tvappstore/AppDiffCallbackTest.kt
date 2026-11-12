package com.ciweihaha.tvappstore

import androidx.recyclerview.widget.DiffUtil
import com.ciweihaha.tvappstore.model.AppInfo
import com.ciweihaha.tvappstore.ui.AppAdapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DiffUtil 回调测试 + 2900 条列表的 diff 性能测试。
 * DiffUtil.calculateDiff 是纯 Java 逻辑，不需要 Android 运行时。
 */
class AppDiffCallbackTest {

    private fun app(pkg: String, version: String = "1.0"): AppInfo {
        return AppInfo(
            packageName = pkg,
            label = "应用 $pkg",
            versionName = version,
            versionCode = 1L,
            size = 1024L,
            sha256 = "abc",
            category = "工具",
            source = "test",
            url = "https://example.com/$pkg.apk",
            file = "$pkg.apk",
            icon = null
        )
    }

    @Test
    fun samePackageIsSameItem() {
        assertTrue(AppAdapter.AppDiffCallback.areItemsTheSame(app("com.a"), app("com.a", "2.0")))
        assertFalse(AppAdapter.AppDiffCallback.areItemsTheSame(app("com.a"), app("com.b")))
    }

    @Test
    fun sameContentCheck() {
        assertTrue(AppAdapter.AppDiffCallback.areContentsTheSame(app("com.a"), app("com.a")))
        assertFalse(AppAdapter.AppDiffCallback.areContentsTheSame(app("com.a", "1.0"), app("com.a", "2.0")))
    }

    /**
     * 压力测试：2900 条列表，改 100 条、删 50 条、增 50 条，算 diff 耗时。
     * 对应用户在“全部”和分类之间切换、或目录更新时的场景。
     */
    @Test
    fun diff2900Items_performance() {
        val oldList = (0 until 2900).map { app("com.example.app$it") }
        val newList = oldList.toMutableList()
        for (i in 0 until 100) newList[i] = app("com.example.app$i", "2.0")
        repeat(50) { newList.removeAt(newList.size - 1) }
        for (i in 0 until 50) newList.add(app("com.example.new$i"))

        val callback = object : DiffUtil.Callback() {
            override fun getOldListSize() = oldList.size
            override fun getNewListSize() = newList.size
            override fun areItemsTheSame(oldPos: Int, newPos: Int) =
                AppAdapter.AppDiffCallback.areItemsTheSame(oldList[oldPos], newList[newPos])
            override fun areContentsTheSame(oldPos: Int, newPos: Int) =
                AppAdapter.AppDiffCallback.areContentsTheSame(oldList[oldPos], newList[newPos])
        }
        val start = System.nanoTime()
        val result = DiffUtil.calculateDiff(callback)
        val elapsedMs = (System.nanoTime() - start) / 1_000_000
        println("2900 条列表 diff 耗时：${elapsedMs}ms")

        // 结果可用：能正常 dispatch（用空回调验证不抛异常）
        result.dispatchUpdatesTo(object : androidx.recyclerview.widget.ListUpdateCallback {
            override fun onInserted(position: Int, count: Int) {}
            override fun onRemoved(position: Int, count: Int) {}
            override fun onMoved(fromPosition: Int, toPosition: Int) {}
            override fun onChanged(position: Int, count: Int, payload: Any?) {}
        })
        assertTrue("2900 条 diff 不应超过 10 秒，实际 ${elapsedMs}ms", elapsedMs < 10_000)
    }
}
