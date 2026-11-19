package com.perol.asdpl.pixivez.data

import com.perol.asdpl.pixivez.services.PxEZApp

/**
 * 搜索筛选条件:要包含的标签 / 要排除的标签。
 * 包含:直接拼进 pixiv 的 word(空格分隔即 AND);
 * 排除:pixiv 没有反选语法,由结果页本地丢弃命中的作品。
 */
object SearchFilterStore {
    private const val KEY_INCLUDE = "search_filter_include"
    private const val KEY_EXCLUDE = "search_filter_exclude"

    private fun read(key: String): List<String> =
        PxEZApp.instance.pre.getString(key, null)
            ?.split('\n')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.distinct()
            ?: emptyList()

    fun includeTags(): List<String> = read(KEY_INCLUDE)

    fun excludeTags(): List<String> = read(KEY_EXCLUDE)

    fun save(include: List<String>, exclude: List<String>) {
        PxEZApp.instance.pre.edit()
            .putString(KEY_INCLUDE, include.map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString("\n"))
            .putString(KEY_EXCLUDE, exclude.map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString("\n"))
            .apply()
    }

    fun clear() = save(emptyList(), emptyList())

    fun isEmpty(): Boolean = includeTags().isEmpty() && excludeTags().isEmpty()

    /** 把包含标签拼到搜索词后面;pixiv 用空格分隔多个词做 AND 搜索。
     *  中文别名(如"虐杀")会换成组内真正的标签(「リョナ」)再搜。 */
    fun applyIncludes(word: String): String {
        val includes = includeTags().map { TagAliases.searchTag(it) }.distinct()
        if (includes.isEmpty()) return word
        return (listOf(word.trim()) + includes).filter { it.isNotEmpty() }.joinToString(" ")
    }

    /** 某个作品标签是否被"排除标签"命中(整组别名都算) */
    fun isExcluded(tagName: String): Boolean {
        val excluded = excludeTags()
        if (excluded.isEmpty()) return false
        return excluded.any { entry -> TagAliases.expand(entry).any { it.equals(tagName, true) } }
    }

    fun summary(): String {
        val inc = includeTags()
        val exc = excludeTags()
        if (inc.isEmpty() && exc.isEmpty()) return "未设置筛选"
        val sb = StringBuilder()
        if (inc.isNotEmpty()) sb.append("+").append(inc.joinToString(","))
        if (exc.isNotEmpty()) {
            if (sb.isNotEmpty()) sb.append("  ")
            sb.append("-").append(exc.joinToString(","))
        }
        return sb.toString()
    }
}
