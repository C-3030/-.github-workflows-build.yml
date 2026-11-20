package com.perol.asdpl.pixivez.ui.novel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import com.perol.asdpl.pixivez.base.BaseViewModel
import com.perol.asdpl.pixivez.base.DMutableLiveData
import com.perol.asdpl.pixivez.data.model.Novel
import com.perol.asdpl.pixivez.data.model.NovelResponse

// 小说列表数据源类型:决定首屏拉取哪个端点
enum class NOVEL_TAG {
    Recommend, Rank, Follow, UserNovels, UserBookmark, Search
}

// 泛化小说列表数据源:按 NOVEL_TAG 选端点,next_url 分页,数据流照 UserListViewModel。
// Follow / UserBookmark 经 restrict 切换公开/非公开。
class NovelListViewModel : BaseViewModel() {
    val data = MutableLiveData<MutableList<Novel>?>()
    val dataAdded = MutableLiveData<MutableList<Novel>?>()
    val nextUrl = MutableLiveData<String?>()
    val isRefreshing = DMutableLiveData(false)
    val restrict = DMutableLiveData("public")

    lateinit var tag: NOVEL_TAG
    private var args: MutableMap<String, Any?> = mutableMapOf()

    fun setup(tag: NOVEL_TAG, args: MutableMap<String, Any?>?) {
        this.tag = tag
        if (args != null) this.args = args
    }

    private fun loadFirstRx(): suspend () -> NovelResponse = when (tag) {
        NOVEL_TAG.Recommend -> {
            { retrofit.api.getNovelRecommend() }
        }

        NOVEL_TAG.Rank -> {
            { retrofit.api.getNovelRanking(args["mode"] as String, args["date"] as String?) }
        }

        NOVEL_TAG.Follow -> {
            { retrofit.api.getNovelFollow(restrict.value) }
        }

        NOVEL_TAG.UserNovels -> {
            { retrofit.api.getUserNovels(args["userid"] as Int) }
        }

        NOVEL_TAG.UserBookmark -> {
            { retrofit.api.getUserBookmarkNovel(args["userid"] as Int, restrict.value) }
        }

        NOVEL_TAG.Search -> {
            {
                retrofit.api.getSearchNovel(
                    com.perol.asdpl.pixivez.data.SearchFilterStore
                        .applyIncludes(args["keyword"] as String),
                    serverSort(),
                    args["search_target"] as? String ?: "partial_match_for_tags",
                    args["start_date"] as? String,
                    args["end_date"] as? String,
                    null,
                    null
                )
            }
        }
    }

    // 字数排序是本地排序,pixiv 没有这个 sort 值,先按时间拉再重排
    private fun serverSort(): String =
        when (val sort = args["sort"] as? String) {
            null -> "date_desc"
            "length_desc" -> "date_desc"
            else -> sort
        }

    // 搜索时应用筛选的排除标签;字数排序在本地做
    private fun transform(novels: MutableList<Novel>): MutableList<Novel> {
        if (tag == NOVEL_TAG.Search) {
            if (com.perol.asdpl.pixivez.data.SearchFilterStore.excludeTags().isNotEmpty()) {
                novels.removeAll { novel ->
                    novel.tags.any { tag ->
                        com.perol.asdpl.pixivez.data.SearchFilterStore.isExcluded(tag.vis())
                    }
                }
            }
            if (com.perol.asdpl.pixivez.data.SearchFilterStore.includeTags().isNotEmpty()) {
                novels.removeAll { novel ->
                    novel.tags.none { tag ->
                        com.perol.asdpl.pixivez.data.SearchFilterStore.isIncluded(tag.vis())
                    }
                }
            }
            if (args["sort"] == "length_desc") novels.sortByDescending { it.text_length }
        }
        return novels
    }

    // 搜索结果里如果开了"排除标签",列表项可能不带标签,
    // 这时对当页每本补一次详情(只取元数据,不拉正文),按 id 缓存
    private val tagCache = java.util.concurrent.ConcurrentHashMap<Int, List<com.perol.asdpl.pixivez.data.model.Tag>>()

    private suspend fun enrichTags(list: MutableList<Novel>): MutableList<Novel> =
        kotlinx.coroutines.coroutineScope {
            val result = list.toMutableList()
            val targets = result.filter { it.tags.isEmpty() }
            if (targets.isEmpty()) return@coroutineScope result
            var fails = 0
            for (chunk in targets.chunked(4)) {
                // 详情连续失败就别硬拉了(多半是被限流),这一页保持原样
                if (fails >= 5) break
                val fetched = chunk.map { novel ->
                    async(kotlinx.coroutines.Dispatchers.IO) {
                        val cached = tagCache[novel.id]
                        if (cached != null) return@async novel.id to cached
                        val tags = runCatching {
                            retrofit.api.getNovelDetail(novel.id).novel.tags
                        }.getOrNull()
                        if (tags == null) {
                            fails++
                            novel.id to null
                        } else {
                            tagCache[novel.id] = tags
                            novel.id to tags
                        }
                    }
                }.awaitAll().toMap()
                for (i in result.indices) {
                    val novel = result[i]
                    if (novel.tags.isNotEmpty()) continue
                    val tags = fetched[novel.id]
                    if (!tags.isNullOrEmpty()) result[i] = novel.copy(tags = tags)
                }
            }
            result
        }

    /** 统一出口:搜索页按需补标签,再套排除/包含筛选与本地排序 */
    private suspend fun prepare(list: MutableList<Novel>): MutableList<Novel> {
        if (tag != NOVEL_TAG.Search) return transform(list)
        val store = com.perol.asdpl.pixivez.data.SearchFilterStore
        val anyFilter = store.excludeTags().isNotEmpty() || store.includeTags().isNotEmpty()
        val enriched = if (anyFilter && list.any { it.tags.isEmpty() }) enrichTags(list) else list
        val out = transform(enriched)
        android.util.Log.d("NovelFilter", "search page: ${list.size} -> ${out.size}, filters=${store.summary()}")
        return out
    }

    fun onLoadFirst() {
        isRefreshing.value = true
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
            try {
                var response = loadFirstRx().invoke()
                var list = prepare(response.data())
                // 整页都被筛掉时自动再拉一页,免得一进搜索页就是空白
                var guard = 0
                while (list.isEmpty() && response.next_url != null && guard < 2) {
                    response = retrofit.getNextNovels(response.next_url!!)
                    list = prepare(response.data())
                    guard++
                }
                data.value = list
                nextUrl.value = response.next_url
            } catch (e: Exception) {
                data.value = null
                com.perol.asdpl.pixivez.objects.CrashHandler.instance
                    .e("novel", "load first failed", e)
            } finally {
                isRefreshing.value = false
            }
        }
    }

    fun onLoadMore() {
        val url = nextUrl.value ?: return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
            try {
                var response = retrofit.getNextNovels(url)
                var list = prepare(response.data())
                var guard = 0
                while (list.isEmpty() && response.next_url != null && guard < 2) {
                    response = retrofit.getNextNovels(response.next_url!!)
                    list = prepare(response.data())
                    guard++
                }
                dataAdded.value = list
                nextUrl.value = response.next_url
            } catch (e: Exception) {
                dataAdded.value = null
                com.perol.asdpl.pixivez.objects.CrashHandler.instance
                    .e("novel", "load more failed", e)
            }
        }
    }
}
