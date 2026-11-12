package com.ciweihaha.tvappstore

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ciweihaha.tvappstore.model.AppInfo
import com.ciweihaha.tvappstore.ui.AppAdapter
import com.ciweihaha.tvappstore.ui.CategoryAdapter
import com.ciweihaha.tvappstore.util.CatalogClient
import com.ciweihaha.tvappstore.util.DownloadHelper
import java.util.concurrent.Executors

/**
 * TV 应用商店 1.0.0：启动后拉取服务端目录（已去重，110 个应用），
 * 按分类展示；点按应用下载 APK、校验 SHA-256 后调系统安装程序安装。
 * 电视遥控器方向键 + OK 键可用，平板触摸也可用。
 */
class MainActivity : AppCompatActivity(), DownloadHelper.Listener {

    private lateinit var categoryList: RecyclerView
    private lateinit var appGrid: RecyclerView
    private lateinit var statusBox: View
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var retryButton: Button
    private lateinit var countText: TextView

    private val categoryAdapter = CategoryAdapter { category -> onCategorySelected(category) }
    private val appAdapter = AppAdapter { app -> onAppClicked(app) }
    private val downloadHelper by lazy { DownloadHelper(this) }

    private val ioExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var allApps: List<AppInfo> = emptyList()
    private var selectedCategory: String? = null

    /** 进度轮询：界面可见时每 500ms 刷一次下载进度 */
    private val progressRunnable = object : Runnable {
        override fun run() {
            downloadHelper.pollProgress()
            mainHandler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        categoryList = findViewById(R.id.categoryList)
        appGrid = findViewById(R.id.appGrid)
        statusBox = findViewById(R.id.statusBox)
        progressBar = findViewById(R.id.progressBar)
        statusText = findViewById(R.id.statusText)
        retryButton = findViewById(R.id.retryButton)
        countText = findViewById(R.id.countText)

        categoryList.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        categoryList.adapter = categoryAdapter

        appGrid.layoutManager = GridLayoutManager(this, calculateSpanCount())
        appGrid.adapter = appAdapter

        retryButton.setOnClickListener { loadCatalog() }

        downloadHelper.listener = this
        downloadHelper.register()

        // 启动时清一次 7 天前的旧安装包（后台跑，不挡界面）
        ioExecutor.execute { downloadHelper.cleanupOldFiles() }

        loadCatalog()
    }

    override fun onResume() {
        super.onResume()
        mainHandler.post(progressRunnable)
    }

    override fun onPause() {
        super.onPause()
        mainHandler.removeCallbacks(progressRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        downloadHelper.unregister()
        downloadHelper.shutdown()
        ioExecutor.shutdown()
    }

    /** 按屏幕宽度算网格列数：电视横屏约 4~6 列，平板竖屏约 2~3 列 */
    private fun calculateSpanCount(): Int {
        val widthDp = resources.configuration.screenWidthDp
        return (widthDp / 200).coerceIn(2, 6)
    }

    private fun loadCatalog() {
        showLoading()
        ioExecutor.execute {
            try {
                val catalog = CatalogClient.fetch()
                // CatalogParser 已跳过缺 package/url 的坏条目，这里只排序
                val apps = (catalog.apps ?: emptyList()).sortedBy { it.displayName }
                val categories =
                    catalog.categories ?: apps.mapNotNull { it.category }.distinct()
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    allApps = apps
                    categoryAdapter.submitList(categories)
                    applyFilter()
                    countText.text = getString(R.string.app_count, apps.size)
                    showContent()
                    // 电视遥控器：默认把焦点给第一个分类，方便直接按方向键
                    categoryList.post { categoryList.getChildAt(0)?.requestFocus() }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    showError()
                }
            }
        }
    }

    private fun onCategorySelected(category: String?) {
        selectedCategory = category
        categoryAdapter.setSelected(category)
        applyFilter()
    }

    private fun applyFilter() {
        val filtered = if (selectedCategory == null) {
            allApps
        } else {
            allApps.filter { it.category == selectedCategory }
        }
        appAdapter.submitList(filtered)
        if (filtered.isEmpty() && allApps.isNotEmpty()) {
            Toast.makeText(this, R.string.empty_category, Toast.LENGTH_SHORT).show()
        }
    }

    private fun onAppClicked(app: AppInfo) {
        when (downloadHelper.download(app)) {
            DownloadHelper.StartResult.Started ->
                appAdapter.setStatus(app.packageName, getString(R.string.download_waiting))
            DownloadHelper.StartResult.AlreadyDownloading ->
                Toast.makeText(this, R.string.download_waiting, Toast.LENGTH_SHORT).show()
            DownloadHelper.StartResult.NoSpace ->
                Toast.makeText(this, R.string.error_no_space, Toast.LENGTH_LONG).show()
            DownloadHelper.StartResult.InvalidUrl ->
                Toast.makeText(this, R.string.error_bad_link, Toast.LENGTH_LONG).show()
            DownloadHelper.StartResult.Failed ->
                Toast.makeText(this, R.string.download_failed, Toast.LENGTH_SHORT).show()
        }
    }

    // ---- DownloadHelper.Listener ----

    override fun onInstallStarted(app: AppInfo) {
        runOnUiThread {
            appAdapter.setStatus(app.packageName, null)
            Toast.makeText(this, R.string.install_prompt, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onFailed(app: AppInfo, reason: String) {
        runOnUiThread {
            appAdapter.setStatus(app.packageName, null)
            Toast.makeText(this, reason, Toast.LENGTH_LONG).show()
        }
    }

    override fun onProgress(app: AppInfo, percent: Int) {
        runOnUiThread {
            val text = if (percent >= 0) {
                getString(R.string.downloading, percent)
            } else {
                getString(R.string.download_waiting)
            }
            appAdapter.setStatus(app.packageName, text)
        }
    }

    // ---- 界面状态 ----

    private fun showLoading() {
        appGrid.visibility = View.GONE
        statusBox.visibility = View.VISIBLE
        progressBar.visibility = View.VISIBLE
        retryButton.visibility = View.GONE
        statusText.setText(R.string.loading)
    }

    private fun showContent() {
        statusBox.visibility = View.GONE
        appGrid.visibility = View.VISIBLE
    }

    private fun showError() {
        appGrid.visibility = View.GONE
        statusBox.visibility = View.VISIBLE
        progressBar.visibility = View.GONE
        retryButton.visibility = View.VISIBLE
        statusText.setText(R.string.load_error)
    }
}
