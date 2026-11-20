package com.perol.asdpl.pixivez.ui.novel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
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
    private val tagCache = HashMap<Int, List<com.perol.asdpl.pixivez.data.model.Tag>>()

    private suspend fun enrichTags(list: MutableList<Novel>): MutableList<Novel> =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            list.map { novel ->
                if (novel.tags.isNotEmpty()) return@map novel
                val tags = tagCache[novel.id] ?: run {
                    val fetched = runCatching {
                        retrofit.api.getNovelDetail(novel.id).novel.tags
                    }.getOrNull().orEmpty()
                    tagCache[novel.id] = fetched
                    fetched
                }
                if (tags.isEmpty()) novel else novel.copy(tags = tags)
            }.toMutableList()
        }

    fun onLoadFirst() {
        isRefreshing.value = true
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
            try {
                val response = loadFirstRx().invoke()
                var list = response.data()
                if (tag == NOVEL_TAG.Search &&
                    com.perol.asdpl.pixivez.data.SearchFilterStore.excludeTags().isNotEmpty()
                ) {
                    list = enrichTags(list)
                }
                data.value = transform(list)
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
        if (nextUrl.value != null) {
            subscribeNext(
                { retrofit.getNextNovels(nextUrl.value!!) },
                dataAdded,
                nextUrl,
                ::transform
            )
        }
    }
}
