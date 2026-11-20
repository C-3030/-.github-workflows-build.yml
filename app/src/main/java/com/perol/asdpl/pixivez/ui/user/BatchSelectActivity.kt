package com.perol.asdpl.pixivez.ui.user

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.chad.brvah.BaseQuickAdapter
import com.chad.brvah.viewholder.BaseViewHolder
import com.google.android.material.button.MaterialButton
import com.google.android.material.tabs.TabLayout
import com.perol.asdpl.pixivez.R
import com.perol.asdpl.pixivez.base.RinkActivity
import com.perol.asdpl.pixivez.data.RetrofitRepository
import com.perol.asdpl.pixivez.data.model.Novel
import com.perol.asdpl.pixivez.objects.Toasty
import com.perol.asdpl.pixivez.services.BatchDownloader
import com.perol.asdpl.pixivez.services.Works
import com.perol.asdpl.pixivez.ui.novel.NovelBatchDownloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 选择下载:像正常浏览一样列出这个作者的作品(插画/漫画/小说),勾选后批量下载。
 * 作品量可能很大,只加载前几页,并提供"再加载一页"。
 */
class BatchSelectActivity : RinkActivity() {

    /** 列表里的一个可选作品 */
    data class WorkItem(
        val id: Int,
        val title: String,
        val thumb: String,
        val isNovel: Boolean,
        var checked: Boolean = false,
    )

    private lateinit var binding: com.perol.asdpl.pixivez.databinding.ActivityBatchSelectBinding
    private lateinit var adapter: SelectAdapter
    private var userId = 0
    private var tab = 0
    private val illustItems = mutableListOf<WorkItem>()
    private val novelItems = mutableListOf<WorkItem>()
    private val bookmarkItems = mutableListOf<WorkItem>()
    private var illustNext: String? = null
    private var novelNext: String? = null
    private var bookmarkNext: String? = null
    private var loading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = com.perol.asdpl.pixivez.databinding.ActivityBatchSelectBinding
            .inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        userId = intent.getIntExtra(EXTRA_USER, 0)

        adapter = SelectAdapter()
        binding.recyclerview.layoutManager = GridLayoutManager(this, 1)
        binding.recyclerview.adapter = adapter
        adapter.setOnItemClickListener { _, _, position ->
            val item = adapter.data.getOrNull(position) ?: return@setOnItemClickListener
            item.checked = !item.checked
            adapter.notifyItemChanged(position)
            updateButton()
        }
        binding.tabs.addTab(binding.tabs.newTab().setText(R.string.works_illust))
        binding.tabs.addTab(binding.tabs.newTab().setText(R.string.works_novel))
        binding.tabs.addTab(binding.tabs.newTab().setText(R.string.download_bookmark))
        binding.tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(t: TabLayout.Tab) {
                tab = t.position
                render()
                loadMoreIfNeeded()
            }

            override fun onTabUnselected(t: TabLayout.Tab) {}
            override fun onTabReselected(t: TabLayout.Tab) {}
        })
        binding.btnDownload.setOnClickListener { downloadSelected() }
        binding.recyclerview.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (!rv.canScrollVertically(1)) loadMoreIfNeeded()
            }
        })
        loadFirst()
    }

    private fun loadFirst() {
        lifecycleScope.launch {
            loading = true
            try {
                withContext(Dispatchers.IO) {
                    val repo = RetrofitRepository.getInstance()
                    val illust = repo.api.getUserIllusts(userId, "illust")
                    illustItems.addAll(illust.illusts.map {
                        WorkItem(it.id, it.title, it.meta[0].square_medium, false)
                    })
                    illustNext = illust.next_url
                    val novels = repo.api.getUserNovels(userId)
                    novelItems.addAll(novels.novels.map {
                        WorkItem(it.id, it.title, it.image_urls.square_medium, true)
                    })
                    novelNext = novels.next_url
                    val bookmarks = repo.api.getLikeIllust(userId, "public", null)
                    bookmarkItems.addAll(bookmarks.illusts.map {
                        WorkItem(it.id, it.title, it.meta[0].square_medium, false)
                    })
                    bookmarkNext = bookmarks.next_url
                }
            } catch (e: Exception) {
                Toasty.error(this@BatchSelectActivity, getString(R.string.download_failed, e.message ?: ""))
            }
            loading = false
            render()
        }
    }

    private fun loadMoreIfNeeded() {
        if (loading) return
        val next = when (tab) {
            0 -> illustNext
            1 -> novelNext
            else -> bookmarkNext
        }
        if (next.isNullOrBlank()) return
        lifecycleScope.launch {
            loading = true
            try {
                withContext(Dispatchers.IO) {
                    val repo = RetrofitRepository.getInstance()
                    when (tab) {
                        0 -> {
                            val page = repo.getIllustNext(next)
                            illustItems.addAll(page.illusts.map {
                                WorkItem(it.id, it.title, it.meta[0].square_medium, false)
                            })
                            illustNext = page.next_url
                        }

                        1 -> {
                            val page = repo.getNextNovels(next)
                            novelItems.addAll(page.novels.map {
                                WorkItem(it.id, it.title, it.image_urls.square_medium, true)
                            })
                            novelNext = page.next_url
                        }

                        else -> {
                            val page = repo.getIllustNext(next)
                            bookmarkItems.addAll(page.illusts.map {
                                WorkItem(it.id, it.title, it.meta[0].square_medium, false)
                            })
                            bookmarkNext = page.next_url
                        }
                    }
                }
            } catch (e: Exception) {
                // 加载失败就停在当前页
            }
            loading = false
            render()
        }
    }

    private fun currentItems(): MutableList<WorkItem> = when (tab) {
        0 -> illustItems
        1 -> novelItems
        else -> bookmarkItems
    }

    private fun render() {
        adapter.setNewInstance(currentItems().toMutableList())
        updateButton()
    }

    private fun updateButton() {
        val count = currentItems().count { it.checked }
        binding.btnDownload.text = getString(R.string.download_selected) + " (" + count + ")"
    }

    private fun downloadSelected() {
        val source = currentItems()
        val selected = source.filter { it.checked }
        if (selected.isEmpty()) {
            Toasty.warning(this, R.string.download_empty)
            return
        }
        if (tab != 1) {
            lifecycleScope.launch {
                try {
                    val repo = RetrofitRepository.getInstance()
                    val illusts = withContext(Dispatchers.IO) {
                        selected.mapNotNull { item ->
                            runCatching { repo.api.getIllust(item.id).illust }.getOrNull()
                        }
                    }
                    if (illusts.isEmpty()) {
                        Toasty.warning(this@BatchSelectActivity, R.string.download_empty)
                    } else {
                        Works.downloadAll(illusts)
                        Toasty.success(
                            this@BatchSelectActivity,
                            getString(R.string.download_queued, illusts.size)
                        )
                    }
                } catch (e: Exception) {
                    Toasty.error(
                        this@BatchSelectActivity,
                        getString(R.string.download_failed, e.message ?: "")
                    )
                }
            }
        } else {
            lifecycleScope.launch {
                try {
                    val repo = RetrofitRepository.getInstance()
                    val novels = withContext(Dispatchers.IO) {
                        selected.mapNotNull { item ->
                            runCatching { repo.api.getNovelDetail(item.id).novel }.getOrNull()
                        }
                    }
                    val done = NovelBatchDownloader.exportAll(this@BatchSelectActivity, novels) { d, t ->
                        runOnUiThread {
                            Toasty.normal(
                                this@BatchSelectActivity,
                                getString(R.string.novel_exporting, d, t)
                            )
                        }
                    }
                    Toasty.success(
                        this@BatchSelectActivity,
                        getString(R.string.novel_export_done, done)
                    )
                } catch (e: Exception) {
                    Toasty.error(
                        this@BatchSelectActivity,
                        getString(R.string.download_failed, e.message ?: "")
                    )
                }
            }
        }
    }

    private inner class SelectAdapter :
        BaseQuickAdapter<WorkItem, BaseViewHolder>(R.layout.item_select_work) {
        override fun convert(holder: BaseViewHolder, item: WorkItem) {
            holder.getView<CheckBox>(R.id.item_check).isChecked = item.checked
            holder.getView<TextView>(R.id.item_title).text = item.title
            val thumb = holder.getView<ImageView>(R.id.item_thumb)
            Glide.with(thumb).load(item.thumb).centerCrop().into(thumb)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    companion object {
        private const val EXTRA_USER = "user_id"

        fun start(context: Context, userId: Int) {
            context.startActivity(
                Intent(context, BatchSelectActivity::class.java).putExtra(EXTRA_USER, userId)
            )
        }
    }
}
