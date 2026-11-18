package com.perol.asdpl.pixivez.services

import com.perol.asdpl.pixivez.data.RetrofitRepository
import com.perol.asdpl.pixivez.data.model.Illust
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 批量抓取某用户的全部作品/收藏，并交给下载队列 */
object BatchDownloader {
    private val repo get() = RetrofitRepository.getInstance()

    /** type: illust / manga */
    suspend fun fetchUserWorks(uid: Int, type: String = "illust"): List<Illust> =
        withContext(Dispatchers.IO) {
            val result = mutableListOf<Illust>()
            val first = repo.api.getUserIllusts(uid, type)
            result.addAll(first.illusts)
            var next = first.next_url
            while (!next.isNullOrBlank()) {
                val page = repo.getIllustNext(next)
                result.addAll(page.illusts)
                next = page.next_url
            }
            result
        }

    /** 收藏作品：restrict = public / private */
    suspend fun fetchUserBookmarks(uid: Int, restrict: String = "public"): List<Illust> =
        withContext(Dispatchers.IO) {
            val result = mutableListOf<Illust>()
            val first = repo.api.getLikeIllust(uid, restrict, null)
            result.addAll(first.illusts)
            var next = first.next_url
            while (!next.isNullOrBlank()) {
                val page = repo.getIllustNext(next)
                result.addAll(page.illusts)
                next = page.next_url
            }
            result
        }

    fun download(list: List<Illust>) = Works.downloadAll(list)
}
