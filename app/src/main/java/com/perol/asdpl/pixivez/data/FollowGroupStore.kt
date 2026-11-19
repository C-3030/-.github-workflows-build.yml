package com.perol.asdpl.pixivez.data

import com.perol.asdpl.pixivez.networks.ServiceFactory.gson
import com.perol.asdpl.pixivez.services.PxEZApp
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.File

/**
 * 关注用户的本地分组(类似收藏夹)。
 * 一人只属于一个分组:移动而不是复制,所以成员表是 userId -> groupId 的单值映射。
 * 数据存在 filesDir/follow_groups.json,pixiv 侧没有对应接口,纯本地。
 */
@Serializable
data class FollowGroup(val id: Int, val name: String)

@Serializable
private class FollowGroupData(
    val nextId: Int = 1,
    val groups: MutableList<FollowGroup> = mutableListOf(),
    // key = userId 的字符串形式,value = 分组 id
    val members: MutableMap<String, Int> = mutableMapOf(),
)

object FollowGroupStore {
    /** 未分组 / 全部 用这两个哨兵 id */
    const val NONE = -1
    const val ALL = -2

    private val lock = Any()
    private var cache: FollowGroupData? = null

    private val file: File
        get() = File(PxEZApp.instance.filesDir, "follow_groups.json")

    private fun load(): FollowGroupData {
        cache?.let { return it }
        val data = try {
            if (file.exists()) {
                gson.decodeFromString<FollowGroupData>(file.readText())
            } else {
                FollowGroupData()
            }
        } catch (e: Exception) {
            // 文件损坏时不要连累列表:重建空配置
            FollowGroupData()
        }
        cache = data
        return data
    }

    private fun save(data: FollowGroupData) {
        try {
            file.writeText(gson.encodeToString(data))
        } catch (e: Exception) {
            // 写失败只影响持久化,cache 仍然可用
        }
        cache = data
    }

    fun groups(): List<FollowGroup> = synchronized(lock) { load().groups.toList() }

    fun groupOf(userId: Int): Int = synchronized(lock) {
        load().members[userId.toString()] ?: NONE
    }

    fun countIn(groupId: Int): Int = synchronized(lock) {
        val data = load()
        data.members.values.count { it == groupId }
    }

    fun create(name: String): FollowGroup = synchronized(lock) {
        val data = load()
        val group = FollowGroup(data.nextId, name.trim().ifBlank { "分组" })
        val next = FollowGroupData(
            nextId = data.nextId + 1,
            groups = data.groups.toMutableList().apply { add(group) },
            members = data.members
        )
        save(next)
        group
    }

    fun rename(id: Int, name: String) = synchronized(lock) {
        val data = load()
        val idx = data.groups.indexOfFirst { it.id == id }
        if (idx < 0) return@synchronized
        val groups = data.groups.toMutableList()
        groups[idx] = groups[idx].copy(name = name.trim().ifBlank { groups[idx].name })
        save(FollowGroupData(data.nextId, groups, data.members))
    }

    /** 删除分组;组内成员回到未分组 */
    fun delete(id: Int) = synchronized(lock) {
        val data = load()
        val groups = data.groups.filterNot { it.id == id }.toMutableList()
        val members = data.members.filterValues { it != id }.toMutableMap()
        save(FollowGroupData(data.nextId, groups, members))
    }

    /** 批量移动(分组 id 传 [NONE] 表示移出分组) */
    fun move(userIds: Collection<Int>, groupId: Int) = synchronized(lock) {
        val data = load()
        val members = data.members.toMutableMap()
        for (uid in userIds) {
            if (groupId == NONE) members.remove(uid.toString())
            else members[uid.toString()] = groupId
        }
        save(FollowGroupData(data.nextId, data.groups, members))
    }

    fun clearCache() {
        synchronized(lock) { cache = null }
    }
}
