package com.perol.asdpl.pixivez.data

import com.perol.asdpl.pixivez.networks.ServiceFactory.gson
import com.perol.asdpl.pixivez.services.PxEZApp
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.File

/** 一条小说导出记录(下载进度页展示用) */
@Serializable
data class NovelDownload(
    val id: Int = 0,
    val title: String = "",
    val author: String = "",
    val format: String = "TXT",
    val path: String = "",
    // 封面地址(列表里显示缩略图用)
    val thumb: String = "",
    val size: Long = 0L,
    val time: Long = 0L,
)

/**
 * 小说导出的本地记录。小说是直接写文件(不是 ketch 队列),
 * 所以下载进度页靠这份记录把小说也列出来。
 */
object NovelDownloadStore {
    private const val KEY = "novel_downloads"

    fun all(): List<NovelDownload> {
        val raw = PxEZApp.instance.pre.getString(KEY, null) ?: return emptyList()
        return try {
            gson.decodeFromString<List<NovelDownload>>(raw)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun add(item: NovelDownload) {
        val list = all().filterNot { it.path == item.path }.toMutableList()
        list.add(0, item)
        save(list)
    }

    /** 补上封面(旧记录可能没有缩略图) */
    fun setThumb(path: String, thumb: String) {
        val list = all().map {
            if (it.path == path && it.thumb.isBlank()) it.copy(thumb = thumb) else it
        }
        save(list)
    }

    fun remove(item: NovelDownload) {
        save(all().filterNot { it.path == item.path })
    }

    fun clear() = save(emptyList())

    private fun save(list: List<NovelDownload>) {
        PxEZApp.instance.pre.edit()
            .putString(KEY, gson.encodeToString(list.take(200)))
            .apply()
    }

    fun exists(path: String): Boolean = File(path).exists()
}
