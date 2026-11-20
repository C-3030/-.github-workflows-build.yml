package com.perol.asdpl.pixivez.ui.manager

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.files.fileChooser
import com.afollestad.materialdialogs.files.folderChooser
import com.afollestad.materialdialogs.list.listItems
import com.ketch.DownloadModel
import com.ketch.Status
import com.perol.asdpl.pixivez.R
import com.perol.asdpl.pixivez.base.MaterialDialogs
import com.perol.asdpl.pixivez.base.RinkActivity
import com.perol.asdpl.pixivez.databinding.ActivityDownloadManagerBinding
import com.perol.asdpl.pixivez.databinding.DialogDownloadConfigBinding
import com.perol.asdpl.pixivez.networks.ServiceFactory.gson
import com.perol.asdpl.pixivez.data.NovelDownloadStore
import com.perol.asdpl.pixivez.objects.DownloadFiles
import com.perol.asdpl.pixivez.objects.FileOpenUtil
import com.perol.asdpl.pixivez.objects.Toasty
import com.perol.asdpl.pixivez.services.IllustD
import com.perol.asdpl.pixivez.services.PxEZApp
import com.perol.asdpl.pixivez.services.Works
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import java.io.File

class DownloadManagerActivity : RinkActivity() {

    private lateinit var binding: ActivityDownloadManagerBinding
    private lateinit var entryAdapter: DownloadEntryAdapter
    // 顶部计数:本次进页面时还在队列里的任务 + 这次完成的数量
    private var pendingAtOpen = 0
    private var doneThisSession = 0
    private var knownDone = mutableSetOf<Int>()
    private var lastModels = listOf<com.ketch.DownloadModel>()
    private var currentTab = 0
    private val coverFetched = mutableSetOf<Int>()
    private val ketch by lazy { PxEZApp.instance.ketch }

    internal class KetchDiffCallback : DiffUtil.ItemCallback<DownloadModel>() {
        override fun areItemsTheSame(oldItem: DownloadModel, newItem: DownloadModel): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: DownloadModel, newItem: DownloadModel): Boolean {
            return (oldItem == newItem)
        }

    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDownloadManagerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        entryAdapter = DownloadEntryAdapter()
        entryAdapter.onToggle = { entry ->
            val model = entry.model
            if (model != null) {
                when (model.status) {
                    Status.FAILED, Status.CANCELLED -> ketch.retry(model.id)
                    Status.PAUSED -> ketch.resume(model.id)
                    else -> ketch.pause(model.id)
                }
            }
        }
        binding.progressList.apply {
            adapter = entryAdapter
            layoutManager = LinearLayoutManager(context)
        }
        setupTabs()
        binding.progressList.itemAnimator = null
        entryAdapter.setOnItemClickListener { _, _, position ->
            val entry = entryAdapter.data.getOrNull(position) ?: return@setOnItemClickListener
            showEntryDialog(entry)
        }
        entryAdapter.setOnItemLongClickListener { _, _, position ->
            val entry = entryAdapter.data.getOrNull(position) ?: return@setOnItemLongClickListener false
            showEntryActions(entry)
            true
        }
        binding.downloadlistrefreshlayout.setOnRefreshListener {
            // refreshList() //TODO: fix list refresh
            lifecycleScope.launch {
                ketch.getDownloadModelByStatus(Status.FAILED).forEach {
                    ketch.resume(it.id)
                }
                binding.downloadlistrefreshlayout.isRefreshing = false
            }
        }
        binding.progress.text = getString(R.string.fractional, 0, 0)
        refreshList()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                ketch.observeDownloads().flowOn(Dispatchers.IO).collect {
                    withContext(Dispatchers.Main) {
                        onModelsChanged(it)
                    }
                }
            }
        }
    }

    // 顶部 插画/小说 分栏;列表来自 ketch 队列 + 目录扫描
    private fun setupTabs() {
        binding.downloadTabs.removeAllTabs()
        binding.downloadTabs.addTab(binding.downloadTabs.newTab().setText(R.string.works_illust))
        binding.downloadTabs.addTab(binding.downloadTabs.newTab().setText(R.string.works_novel))
        binding.downloadTabs.addOnTabSelectedListener(object :
            com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab) {
                currentTab = tab.position
                // 换栏目时重新计数(各栏目自己的进度)
                pendingAtOpen = pendingForTab(lastModels, currentTab)
                doneThisSession = 0
                render()
            }

            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab) {}
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab) {}
        })
        currentTab = 0
    }

    private fun onModelsChanged(models: List<com.ketch.DownloadModel>) {
        val first = lastModels.isEmpty()
        lastModels = models
        if (first) {
            pendingAtOpen = pendingForTab(models, currentTab)
            doneThisSession = 0
            knownDone = models.filter { it.status == Status.SUCCESS }
                .map { it.id }.toMutableSet()
        } else {
            // 本次页面里完成的任务数
            models.filter { it.status == Status.SUCCESS && it.id !in knownDone }.forEach {
                knownDone.add(it.id)
                doneThisSession++
            }
        }
        render()
    }

    private fun pendingForTab(models: List<com.ketch.DownloadModel>, tab: Int): Int =
        if (tab == 0) models.count { it.status != Status.SUCCESS } else 0

    private fun render() {
        val store = File(PxEZApp.storepath)
        val entries = sortEntries(buildEntries(store, currentTab == 1))
        entryAdapter.setNewInstance(entries.toMutableList())
        fetchMissingNovelCovers(entries)
        // 前面的 n = 本次完成数量;后面的 n = 进入页面时这一批队列总量
        binding.progress.text = getString(R.string.fractional, doneThisSession, pendingAtOpen)
    }

    /** 建当前栏目的行:插画=ketch 任务 + 目录里扫到的图片;小说=导出记录 + Novel 目录 */
    private fun buildEntries(store: File, novelTab: Boolean): List<DownloadEntry> {
        val result = mutableListOf<DownloadEntry>()
        if (!novelTab) {
            lastModels.forEach { model ->
                val file = File(model.path, model.fileName)
                var title = model.fileName
                var illustId: Int? = null
                try {
                    val illustD = gson.decodeFromString<IllustD>(model.metaData)
                    title = "${illustD.title}${illustD.pString("_p")}"
                    illustId = illustD.id
                } catch (e: Exception) {
                    // 保持文件名
                }
                val subtitle = if (model.status == Status.SUCCESS) {
                    getString(R.string.download_done)
                } else {
                    "${model.progress}% · ${statusText(model.status)}"
                }
                result.add(
                    DownloadEntry(
                        key = "task-${model.id}",
                        title = title,
                        subtitle = subtitle,
                        progress = model.progress,
                        isNovel = false,
                        file = file,
                        coverUrl = model.url,
                        illustId = illustId,
                        model = model
                    )
                )
            }
            // 目录里已有但不在队列里的图片(旧下载)
            val taskFiles = lastModels.map { File(it.path, it.fileName).name }.toSet()
            val imageDir = File(store, "")
            DownloadFiles.list(imageDir)
                .filter { DownloadFiles.isImage(it) && it.name !in taskFiles }
                .forEach { file ->
                    val pid = file.nameWithoutExtension.substringBefore("(")
                        .toIntOrNull()
                    result.add(
                        DownloadEntry(
                            key = "file-${file.absolutePath}",
                            title = file.nameWithoutExtension,
                            subtitle = "${sizeText(file.length())} · ${dateText(file)}",
                            progress = 100,
                            isNovel = false,
                            file = file,
                            illustId = pid
                        )
                    )
                }
        } else {
            val records = NovelDownloadStore.all()
            val recordPaths = records.map { it.path }.toSet()
            records.forEach { item ->
                result.add(
                    DownloadEntry(
                        key = "novel-${item.path}",
                        title = item.title,
                        subtitle = "${item.format} · ${sizeText(item.size)} · ${dateText(File(item.path))}",
                        progress = 100,
                        isNovel = true,
                        file = File(item.path),
                        coverUrl = item.thumb,
                        novelId = item.id
                    )
                )
            }
            DownloadFiles.list(File(store, "Novel"))
                .filter { DownloadFiles.isNovel(it) && it.absolutePath !in recordPaths }
                .forEach { file ->
                    val nid = file.nameWithoutExtension.substringAfterLast('_')
                        .toIntOrNull()
                    result.add(
                        DownloadEntry(
                            key = "novel-file-${file.absolutePath}",
                            title = file.nameWithoutExtension.substringBeforeLast('_'),
                            subtitle = "${file.extension} · ${sizeText(file.length())}",
                            progress = 100,
                            isNovel = true,
                            file = file,
                            novelId = nid
                        )
                    )
                }
        }
        return result
    }

    // ---- 排序:待下载的置顶,其余按时间/名字 ----
    private fun sortEntries(list: List<DownloadEntry>): List<DownloadEntry> {
        val mode = PxEZApp.instance.pre.getString(SORT_KEY, "date_desc") ?: "date_desc"
        val pending = list.filter { !it.isDone && it.model != null }
        val rest = list.filterNot { !it.isDone && it.model != null }
        val sorted = when (mode) {
            "date_asc" -> rest.sortedBy { it.file?.lastModified() ?: 0L }
            "name_asc" -> rest.sortedBy { it.title.lowercase() }
            "name_desc" -> rest.sortedByDescending { it.title.lowercase() }
            else -> rest.sortedByDescending { it.file?.lastModified() ?: 0L }
        }
        return pending + sorted
    }

    private fun chooseSort() {
        val keys = arrayOf("date_desc", "date_asc", "name_asc", "name_desc")
        val labels = arrayOf(
            getString(R.string.sort_time_desc),
            getString(R.string.sort_time_asc),
            getString(R.string.sort_name_asc),
            getString(R.string.sort_name_desc)
        )
        val cur = keys.indexOf(PxEZApp.instance.pre.getString(SORT_KEY, "date_desc"))
            .coerceAtLeast(0)
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(R.string.search_sort)
            .setSingleChoiceItems(labels, cur) { d, which ->
                PxEZApp.instance.pre.edit().putString(SORT_KEY, keys[which]).apply()
                d.dismiss()
                render()
            }
            .show()
    }

    // 旧的小说记录没有缩略图,用作品 id 拉一次详情补上
    private fun fetchMissingNovelCovers(entries: List<DownloadEntry>) {
        entries.filter {
            it.isNovel && it.coverUrl.isNullOrBlank() && it.novelId != null &&
                it.novelId !in coverFetched
        }.forEach { entry ->
            val id = entry.novelId ?: return@forEach
            coverFetched.add(id)
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val novel = com.perol.asdpl.pixivez.data.RetrofitRepository.getInstance()
                        .api.getNovelDetail(id).novel
                    NovelDownloadStore.setThumb(entry.file?.absolutePath.orEmpty(), novel.image_urls.medium)
                    withContext(Dispatchers.Main) { render() }
                } catch (e: Exception) {
                    // 拉不到就保持占位图
                }
            }
        }
    }

    private fun statusText(status: Status): String = when (status) {
        Status.QUEUED -> getString(R.string.status_queued)
        Status.STARTED -> getString(R.string.status_started)
        Status.PROGRESS -> getString(R.string.status_progress)
        Status.SUCCESS -> getString(R.string.download_done)
        Status.CANCELLED -> getString(R.string.status_cancelled)
        Status.FAILED -> getString(R.string.status_failed)
        Status.PAUSED -> getString(R.string.status_paused)
        else -> status.name
    }

    private fun sizeText(bytes: Long): String = when {
        bytes >= 1024 * 1024 -> String.format(java.util.Locale.ROOT, "%.1fMB", bytes / 1048576f)
        bytes >= 1024 -> String.format(java.util.Locale.ROOT, "%.0fKB", bytes / 1024f)
        else -> "${bytes}B"
    }

    private fun dateText(file: File): String =
        android.text.format.DateFormat.format("MM-dd HH:mm", file.lastModified()).toString()

    // 点一行:进作品页 或 用第三方应用打开文件
    private fun showEntryDialog(entry: DownloadEntry) {
        val labels = mutableListOf<String>()
        val actions = mutableListOf<() -> Unit>()
        if (entry.illustId != null) {
            labels.add(getString(R.string.open_work_page))
            actions.add {
                entry.illustId?.let { com.perol.asdpl.pixivez.ui.pic.PictureActivity.start(this, it) }
            }
        }
        if (entry.novelId != null) {
            labels.add(getString(R.string.open_work_page))
            actions.add {
                entry.novelId?.let { com.perol.asdpl.pixivez.ui.novel.NovelActivity.start(this, it) }
            }
        }
        // 只有下完的文件才能"打开文件",进行中的不显示这个选项
        if (entry.file != null && entry.isDone) {
            labels.add(getString(R.string.open_file))
            actions.add { entry.file?.let { FileOpenUtil.open(this, it) } }
        }
        if (labels.isEmpty()) return
        MaterialDialogs(this)
            .setTitle(entry.title)
            .setItems(labels.toTypedArray()) { d, which ->
                d.dismiss()
                actions.getOrNull(which)?.invoke()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showEntryActions(entry: DownloadEntry) {
        MaterialDialogs(this).show {
            setTitle(entry.title)
            setItems(R.array.download_task_choice) { _, index ->
                when (index) {
                    0 -> MaterialDialogs(this@DownloadManagerActivity).show {
                        setMessage(entry.subtitle)
                        setPositiveButton(R.string.ok) { _, _ -> }
                    }
                    1 -> entry.model?.let { ketch.retry(it.id) }
                    2 -> entry.model?.let { ketch.pause(it.id) }
                    3 -> entry.model?.let { ketch.resume(it.id) }
                    else -> deleteEntry(entry)
                }
            }
            cancelButton()
        }
    }

    private fun deleteEntry(entry: DownloadEntry) {
        MaterialDialogs(this).show {
            setMessage(R.string.confirm_title)
            confirmButton { _, _ ->
                entry.model?.let { ketch.clearDb(it.id, it.status != Status.SUCCESS) }
                if (entry.isNovel && entry.file != null) {
                    NovelDownloadStore.remove(
                        com.perol.asdpl.pixivez.data.NovelDownload(0, "", "", "", entry.file!!.absolutePath)
                    )
                }
                entry.file?.delete()
                render()
            }
            cancelButton()
        }
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_download_manager, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_settings -> {
                val configDialog = DialogDownloadConfigBinding.inflate(layoutInflater)
                configDialog.spinnerMaxTaskNum.setSelection(
                    PxEZApp.instance.pre.getString(
                        "max_task_num",
                        "2"
                    )!!.toInt() - 1
                )
                configDialog.spinnerThreadNum.setSelection(
                    PxEZApp.instance.pre.getString(
                        "thread_num",
                        "2"
                    )!!.toInt() - 1
                )
                MaterialDialogs(this).show {
                    setTitle(R.string.task_setting)
                    setView(configDialog.root)
                    cancelButton()
                    confirmButton { _, _ ->
                        //TODO: set ketch download thread config
                    }
                }
            }

            R.id.action_resume -> {
                ketch.resumeAll()
            }

            R.id.action_cancel -> {
                MaterialDialogs(this).show {
                    setMessage(R.string.all_cancel)
                    confirmButton { _, _ ->
                        ketch.clearAllDb() //TODO: check if cache is cleared
                    }
                }
            }

            R.id.action_finished_cancel -> {
                PxEZApp.instance.applicationScope.launch {
                    ketch.getDownloadModelByStatus(Status.SUCCESS).forEach {
                        ketch.clearDb(it.id, false)
                    }
                }
            }

            R.id.action_stop -> {
                ketch.pauseAll()
            }

            R.id.action_restart -> {
                PxEZApp.ActivityCollector.recreate()
            }

            R.id.action_export -> {
                showExportDialog()
            }

            R.id.action_sort -> {
                chooseSort()
            }

            R.id.action_import -> {
                showImportDialog()
            }
        }
        return true
    }

    @SuppressLint("CheckResult")
    private fun showExportDialog() = MaterialDialog(this).show {
        folderChooser(
            initialDirectory = File(PxEZApp.storepath),
            allowFolderCreation = true,
            context = context
        ) { _, folder ->
            MaterialDialog(context).show {
                listItems(items = listOf("URL", "FILE", "INFO")) { _, index, _ ->
                    CoroutineScope(Dispatchers.IO).launch {
                        File(folder.absolutePath + File.separatorChar + "download.log")
                            .writer().let { writer ->
                                ketch.getAllDownloads().forEach {
                                    when (index) {
                                        0 -> {
                                            writer.appendLine(it.fileName)
                                            writer.appendLine(it.url)
                                            writer.appendLine(it.metaData)
                                        }

                                        1 -> {
                                            writer.appendLine(it.url.substringAfterLast("/"))
                                        }

                                        2 -> {
                                            writer.appendLine(gson.encodeToString(it))
                                        }
                                    }
                                }
                                writer.flush()
                                writer.close()
                            }
                    }
                }
            }
        }
    }

    @SuppressLint("CheckResult")
    private fun showImportDialog() = MaterialDialog(this).show {
        fileChooser(
            initialDirectory = File(PxEZApp.storepath),
            filter = { it.isDirectory || it.extension == "log" },
            context = context
        ) { _, file ->
            MaterialDialog(context).show {
                listItems(items = listOf("URL", "FILE", "INFO")) { _, index, _ ->
                    if (index == 0) {
                        file.readLines().chunked(3).forEach {
                            val illust = gson.decodeFromString<IllustD>(it[2])
                            //  val filename = it[0]
                            //  val url = it[1]
                            //  val path = Works.getDownloadPath(illust)
                            //  ketch.download(
                            //      url,
                            //      path,
                            //      filename,
                            //      tag = illust.id.toString(),
                            //      metaData = it[2]
                            //  )
                            Works.imgD(illust.id, illust.part)
                        }
                    } else file.readLines().forEach {
                        when (index) {
                            1 -> {
                                val pid = (
                                        Regex("(?<=(pid)?_?)(\\d{7,9})").find(it)?.value ?: ""
                                        ).toIntOrNull()
                                val dot = it.lastIndexOf(".")
                                val part = (
                                        Regex("""(?<=_p?)([0-9]{1,2})(?=\.)""")
                                            .find(
                                                it, //TODO: if necessary?
                                                if (dot - 4 > 0) dot - 4 else 0
                                            )?.value ?: "0"
                                        ).toIntOrNull()
                                if (pid != null && part != null) {
                                    Works.imgD(pid, part)
                                    // Thread.sleep(300)
                                }
                            }

                            2 -> gson.decodeFromString<DownloadModel>(it).let { it ->
                                ketch.download(
                                    it.url,
                                    it.path,
                                    it.fileName,
                                    it.tag,
                                    it.metaData,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun refreshList() {
        lifecycleScope.launch(Dispatchers.IO) {
            val list = ketch.getAllDownloads().asReversed()
            // 注意:.temp 是暂停/失败后继续下载的断点,不能自动搬走
            withContext(Dispatchers.Main) {
                lastModels = list
                pendingAtOpen = pendingForTab(list, currentTab)
                knownDone = list.filter { it.status == Status.SUCCESS }.map { it.id }.toMutableSet()
                    .toMutableSet()
                doneThisSession = 0
                render()
            }
        }
    }

    companion object {
        private const val SORT_KEY = "download_sort"

        fun start(context: Context) {
            context.startActivity(
                Intent(
                    context,
                    DownloadManagerActivity::class.java
                ).setAction("DownMgr.start")
            )
        }
    }
}
