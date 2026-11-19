package com.perol.asdpl.pixivez.ui.novel

import android.content.Context
import com.perol.asdpl.pixivez.data.RetrofitRepository
import com.perol.asdpl.pixivez.data.model.Novel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 小说批量导出:作者页"全部小说"用。
 * 小说的正文要逐篇拿 webview JSON,没有插图路径时也能导出纯文字 TXT。
 */
object NovelBatchDownloader {
    private val repo get() = RetrofitRepository.getInstance()

    /** 某个作者的全部小说(翻页取全) */
    suspend fun fetchUserNovels(uid: Int): List<Novel> = withContext(Dispatchers.IO) {
        val result = mutableListOf<Novel>()
        val first = repo.api.getUserNovels(uid)
        result.addAll(first.novels)
        var next = first.next_url
        while (!next.isNullOrBlank()) {
            val page = repo.getNextNovels(next)
            result.addAll(page.novels.filter { n -> result.none { it.id == n.id } })
            next = page.next_url
        }
        result
    }

    /**
     * 逐篇导出,返回成功数量。onProgress(done, total) 每完成一篇回调一次。
     * 单篇失败(下架/空正文)跳过,不中断整批。
     */
    suspend fun exportAll(
        context: Context,
        novels: List<Novel>,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
    ): Int = withContext(Dispatchers.IO) {
        var done = 0
        for ((index, novel) in novels.withIndex()) {
            try {
                val html = repo.api.getNovelText(novel.id).use { it.string() }
                val web = parseWebNovel(html)
                if (web != null && web.text.isNotBlank()) {
                    val chunks = chunkNovel(
                        web.text,
                        resolvePixiv = { pid ->
                            web.illusts?.get(pid.toString())?.illust?.images
                                ?.let { it.medium ?: it.original }
                        },
                        resolveUploaded = { iid ->
                            web.images?.get(iid)?.urls?.let { it.mw480 ?: it.original }
                        },
                    )
                    NovelExporter.export(context, novel, chunks, NovelExporter.Format.TXT)
                    done++
                }
            } catch (e: Exception) {
                // 跳过失败的篇目
            }
            onProgress(index + 1, novels.size)
        }
        done
    }
}
