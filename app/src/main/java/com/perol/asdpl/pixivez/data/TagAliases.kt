package com.perol.asdpl.pixivez.data

/**
 * 同一含义的标签别名组(中文/日文/罗马字一起算)。
 * 例如"虐杀"在 pixiv 上没有同名标签,真正能搜到的是「リョナ」;
 * 用户写任意一种写法,筛选时都按整组匹配。
 *
 * 约定:每组第一个是"拿去搜索"用的标签(实际存在、命中率高的那个)。
 */
object TagAliases {
    private val bundles: List<List<String>> = listOf(
        listOf("百合", "yuri", "ユリ", "ガールズラブ"),
        listOf("R-18", "R18", "r-18"),
        listOf("R-18G", "R18G", "r-18g"),
        listOf("ふたなり", "扶她", "futanari", "ふたなりっこ", "二形"),
        listOf("男の娘", "伪娘", "女装", "otokonoko", "男のコ"),
        listOf("リョナ", "虐杀", "虐殺", "ryona", "グロ", "猟奇"),
        listOf("グロ", "重口", "グロテスク", "guro", "リョナ", "猟奇"),
        listOf("NTR", "寝取られ", "ntr", "ネトラレ"),
        listOf("親子", "母女", "母娘", "親子丼", "母子"),
        listOf("ロリ", "萝莉", "loli", "ロリータ"),
        listOf("巨乳", "爆乳", "kyonyuu", "おっぱい"),
        listOf("ショタ", "正太", "shota"),
        listOf("猫耳", "猫耳っ娘", "nekomimi"),
        listOf("オリジナル", "原创", "oc"),
    )

    /** 该标签所属的别名组(没收录就是自己一个) */
    fun expand(tag: String): List<String> {
        val t = tag.trim()
        return bundles.firstOrNull { bundle -> bundle.any { it.equals(t, true) } } ?: listOf(t)
    }

    /** 真正拿去搜索的标签名(组内第一个) */
    fun searchTag(tag: String): String {
        val t = tag.trim()
        val bundle = bundles.firstOrNull { b -> b.any { it.equals(t, true) } }
        return bundle?.first() ?: t
    }
}
