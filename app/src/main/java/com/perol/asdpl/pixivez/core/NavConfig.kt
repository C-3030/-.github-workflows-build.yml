package com.perol.asdpl.pixivez.core

import com.perol.asdpl.pixivez.R
import com.perol.asdpl.pixivez.services.PxEZApp

/** 底部导航可选的页面 */
enum class NavPage(val key: String, val title: String, val iconRes: Int) {
    Home("home", "首页", R.drawable.ic_action_home),
    Bookmark("bookmark", "收藏", R.drawable.ic_heart_outline),
    Search("search", "搜索", R.drawable.ic_action_search),
    Follow("follow", "关注", R.drawable.ic_star_outline),
    Rank("rank", "排行", R.drawable.ic_star);

    companion object {
        fun byKey(key: String?): NavPage? = entries.firstOrNull { it.key == key }
    }
}

/** 底部导航的顺序与显隐，保存在 SharedPreferences */
object NavConfig {
    private const val KEY_ORDER = "nav_order"
    private const val KEY_HIDDEN = "nav_hidden"

    val defaultOrder: List<String> = NavPage.entries.map { it.key }

    fun order(): List<String> {
        val raw = PxEZApp.instance.pre.getString(KEY_ORDER, null)
        val saved = raw?.split(",")?.mapNotNull { it.trim() }?.filter { NavPage.byKey(it) != null } ?: emptyList()
        val missing = defaultOrder.filterNot { it in saved }
        return if (saved.isEmpty()) defaultOrder else saved + missing
    }

    fun hidden(): Set<String> =
        PxEZApp.instance.pre.getStringSet(KEY_HIDDEN, emptySet())?.toSet() ?: emptySet()

    /** 实际显示的页面：按顺序、去掉隐藏项，至少保留一个 */
    fun visiblePages(): List<NavPage> {
        val hidden = hidden()
        val list = order().filterNot { it in hidden }.mapNotNull { NavPage.byKey(it) }
        return list.ifEmpty { listOf(NavPage.Home) }
    }

    fun saveOrder(list: List<String>) {
        PxEZApp.instance.pre.edit().putString(KEY_ORDER, list.joinToString(",")).apply()
    }

    fun saveHidden(set: Set<String>) {
        PxEZApp.instance.pre.edit().putStringSet(KEY_HIDDEN, set).apply()
    }
}
