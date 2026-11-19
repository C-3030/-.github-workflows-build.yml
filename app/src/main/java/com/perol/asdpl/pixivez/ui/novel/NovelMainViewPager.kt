package com.perol.asdpl.pixivez.ui.novel

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import com.perol.asdpl.pixivez.R

// 小说模式的三个 tab:标题/图标集中在这里,MainActivity 用它渲染底部导航,
// 以前底部导航按插画模式的 NavConfig 渲染,导致"写收藏其实是排行"的错位
enum class NovelPage(val title: String, val iconRes: Int) {
    Recommend("推荐", R.drawable.ic_nv_home),
    Rank("排行", R.drawable.ic_nv_rank),
    Follow("动态", R.drawable.ic_nv_star)
}

// 小说独立模式首页三件套,与 HelloMainViewPager 三 tab 一一对应:
// 推荐(novel/recommended)/ 排行(mode 可切)/ 动态(novel/follow)
class NovelMainViewPager(fragmentManager: FragmentManager) :
    FragmentPagerAdapter(fragmentManager, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {
    override fun getCount(): Int = NovelPage.entries.size

    override fun getPageTitle(position: Int): CharSequence = NovelPage.entries[position].title

    override fun getItem(position: Int): Fragment = when (position) {
        0 -> NovelListFragment.newInstance(NOVEL_TAG.Recommend)
        1 -> NovelRankFragment.newInstance()
        else -> NovelListFragment.newInstance(NOVEL_TAG.Follow)
    }
}
