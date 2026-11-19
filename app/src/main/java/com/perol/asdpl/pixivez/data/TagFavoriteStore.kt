package com.perol.asdpl.pixivez.data

import com.perol.asdpl.pixivez.networks.ServiceFactory.gson
import com.perol.asdpl.pixivez.services.PxEZApp
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.File

/** 收藏标签分组 */
@Serializable
data class TagGroup(
    val id: Int,
    var name: String,
    val tags: MutableList<String> = mutableListOf(),
)

@Serializable
private class TagFavData(
    val nextId: Int = 1,
    val groups: MutableList<TagGroup> = mutableListOf(),
)

/**
 * 收藏的标签:可在作品页把标签收进来,按分组管理,
 * 在搜索筛选里能一键加入"包含/排除"。默认全部进"未分类"(id = 0)。
 */
object TagFavoriteStore {
    const val DEFAULT_GROUP = 0
    const val DEFAULT_NAME = "未分类"

    private val lock = Any()
    private var cache: TagFavData? = null

    private val file: File
        get() = File(PxEZApp.instance.filesDir, "tag_favorites.json")

    private fun load(): TagFavData {
        cache?.let { return it }
        val data = try {
            if (file.exists()) gson.decodeFromString<TagFavData>(file.readText())
            else TagFavData()
        } catch (e: Exception) {
            TagFavData()
        }
        if (data.groups.none { it.id == DEFAULT_GROUP }) {
            data.groups.add(0, TagGroup(DEFAULT_GROUP, DEFAULT_NAME))
        }
        cache = data
        return data
    }

    private fun save(data: TagFavData) {
        try {
            file.writeText(gson.encodeToString(data))
        } catch (e: Exception) {
            // 写失败不影响本次使用
        }
        cache = data
    }

    fun groups(): List<TagGroup> = synchronized(lock) {
        load().groups.map { it.copy(tags = it.tags.toMutableList()) }
    }

    fun allTags(): List<String> = synchronized(lock) {
        load().groups.flatMap { it.tags }.distinct()
    }

    fun isFavorite(tag: String): Boolean = synchronized(lock) {
        load().groups.any { group -> group.tags.any { it.equals(tag, true) } }
    }

    /** 收藏标签;已存在则移动到目标分组(一个标签只在组里出现一次) */
    fun add(tag: String, groupId: Int = DEFAULT_GROUP): Boolean {
        val name = tag.trim()
        if (name.isEmpty()) return false
        synchronized(lock) {
            val data = load()
            val target = data.groups.firstOrNull { it.id == groupId } ?: data.groups[0]
            data.groups.forEach { group ->
                if (group.id != target.id) group.tags.removeAll { it.equals(name, true) }
            }
            if (target.tags.none { it.equals(name, true) }) target.tags.add(name)
            save(TagFavData(data.nextId, data.groups))
        }
        return true
    }

    fun remove(tag: String) = synchronized(lock) {
        val data = load()
        data.groups.forEach { group -> group.tags.removeAll { it.equals(tag, true) } }
        save(TagFavData(data.nextId, data.groups))
    }

    fun createGroup(name: String): TagGroup = synchronized(lock) {
        val data = load()
        val group = TagGroup(data.nextId, name.trim().ifBlank { "分组" })
        data.groups.add(group)
        save(TagFavData(data.nextId + 1, data.groups))
        group
    }

    fun renameGroup(id: Int, name: String) = synchronized(lock) {
        if (id == DEFAULT_GROUP) return@synchronized
        val data = load()
        data.groups.firstOrNull { it.id == id }?.let { it.name = name.trim() }
        save(TagFavData(data.nextId, data.groups))
    }

    /** 删组:组内标签回到未分类 */
    fun deleteGroup(id: Int) = synchronized(lock) {
        if (id == DEFAULT_GROUP) return@synchronized
        val data = load()
        val removed = data.groups.firstOrNull { it.id == id }?.tags.orEmpty()
        val fallback = data.groups.firstOrNull { it.id == DEFAULT_GROUP }
        removed.forEach { tag ->
            if (fallback != null && fallback.tags.none { it.equals(tag, true) }) fallback.tags.add(tag)
        }
        data.groups.removeAll { it.id == id }
        save(TagFavData(data.nextId, data.groups))
    }
}
