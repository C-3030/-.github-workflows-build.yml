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

    /** 包含标签不做服务端拼接(否则"萝莉"会被换成「ロリ」,而 pixiv 的长标签
     *  "萝莉/妹妹/..." 只认中文子段,拼进去反而把它排掉)。
     *  这里只回原词,真正的包含过滤在 isIncluded() 里按 / 拆分子标签本地做。 */
    fun applyIncludes(word: String): String = word

    /** 长标签常用 "/" 拼接多个子标签;vis() 还会带 "|" 分隔的译名,一起拆 */
    fun tagParts(tagName: String): List<String> =
        tagName.split('/', '／', ',', '|').map { it.trim() }.filter { it.isNotEmpty() }

    private fun matches(entry: String, tagName: String): Boolean {
        val parts = tagParts(tagName)
        return TagAliases.expand(entry).any { alias ->
            parts.any { part -> part.equals(alias, true) }
        }
    }

    /** 某个作品标签是否被"排除标签"命中(整组别名 + / 拆分子标签都算) */
    fun isExcluded(tagName: String): Boolean {
        val excluded = excludeTags()
        if (excluded.isEmpty()) return false
        return excluded.any { entry -> matches(entry, tagName) }
    }

    /** 作品是否满足"包含标签"(客户端再兜一次,长标签里带子标签也能命中) */
    fun isIncluded(tagName: String): Boolean {
        val includes = includeTags()
        if (includes.isEmpty()) return false
        return includes.any { entry -> matches(entry, tagName) }
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
