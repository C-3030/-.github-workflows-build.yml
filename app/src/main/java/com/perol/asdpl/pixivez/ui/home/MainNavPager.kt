package com.perol.asdpl.pixivez.ui.home

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import com.perol.asdpl.pixivez.core.NavPage
import com.perol.asdpl.pixivez.core.PicListFragment
import com.perol.asdpl.pixivez.core.TAG_TYPE
import com.perol.asdpl.pixivez.data.AppDataRepo
import com.perol.asdpl.pixivez.ui.home.my.HelloMThFragment
import com.perol.asdpl.pixivez.ui.home.recom.HelloMRecomFragment
import com.perol.asdpl.pixivez.ui.home.trend.HelloTrendingFragment
import com.perol.asdpl.pixivez.ui.search.NavSearchFragment

/** 按 NavConfig 顺序装配的底部导航页面 */
class MainNavPager(
    fragmentManager: FragmentManager,
    private val pages: List<NavPage>,
) : FragmentPagerAdapter(fragmentManager, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {

    override fun getCount(): Int = pages.size

    override fun getItem(position: Int): Fragment {
        return when (pages[position]) {
            NavPage.Home -> HelloMRecomFragment.newInstance("r")
            NavPage.Bookmark -> {
                val uid = if (AppDataRepo.userInited()) AppDataRepo.currentUser.userid else 0
                PicListFragment.newInstance(
                    TAG_TYPE.UserBookmark.name,
                    0,
                    mutableMapOf("userid" to uid)
                )
            }
            NavPage.Search -> NavSearchFragment.newInstance()
            NavPage.Follow -> HelloMThFragment.newInstance("d")
            NavPage.Rank -> HelloTrendingFragment.newInstance("t")
        }
    }

    override fun getPageTitle(position: Int): CharSequence = pages[position].title
}
