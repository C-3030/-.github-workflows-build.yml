/*
 * MIT License
 *
 * Copyright (c) 2019 Perol_Notsfsssf
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE
 */

package com.perol.asdpl.pixivez.core

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityOptions
import android.util.Pair
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade
import com.chad.brvah.viewholder.BaseViewHolder
import com.google.android.material.card.MaterialCardView
import com.perol.asdpl.pixivez.R
import com.perol.asdpl.pixivez.base.LBaseQuickAdapter
import com.perol.asdpl.pixivez.base.SMutableLiveData
import com.perol.asdpl.pixivez.data.model.User
import com.perol.asdpl.pixivez.data.model.UserPreviewsBean
import com.perol.asdpl.pixivez.objects.DataHolder
import com.perol.asdpl.pixivez.objects.ThemeUtil
import com.perol.asdpl.pixivez.objects.dp
import com.perol.asdpl.pixivez.services.PxEZApp
import com.perol.asdpl.pixivez.ui.pic.NovelCoverAdapter
import com.perol.asdpl.pixivez.ui.pic.PictureActivity
import com.perol.asdpl.pixivez.ui.pic.SquareMediumAdapter
import com.perol.asdpl.pixivez.ui.user.UserMActivity
import com.perol.asdpl.pixivez.view.NiceImageView
import kotlin.properties.Delegates

// TODO: fling optimize
class UserListAdapter(layoutResId: Int) :
    LBaseQuickAdapter<UserPreviewsBean, BaseViewHolder>(layoutResId) {
    companion object {
        const val ITEM_WIDTH: Int = 400
        val itemWidthPx: Int = 400.dp
    }

    private var colorPrimary by Delegates.notNull<Int>()
    private var badgeTextColor by Delegates.notNull<Int>()

    private var mSharedPool = RecyclerView.RecycledViewPool()
    private val tempLiveData = SMutableLiveData(false)

    // ---- 多选(关注用户分组用) ----
    var selectionEnabled = false
        set(value) {
            field = value
            notifyDataSetChanged()
        }
    var selectedIds: Set<Int> = emptySet()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    /** 点击(选择模式)/ 长按(进入选择模式)回调,由宿主 Fragment 接管 */
    var onSelectionToggle: ((UserPreviewsBean) -> Unit)? = null
    var onSelectionStart: ((UserPreviewsBean) -> Unit)? = null

    // 卡片下方展示的作品类型:全部(有插画先插画,否则小说)/ 仅插画 / 仅小说
    enum class StripMode { ALL, ILLUST, NOVEL }

    var stripMode = StripMode.ALL
        set(value) {
            field = value
            notifyDataSetChanged()
        }
    private val novelPool = RecyclerView.RecycledViewPool()

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        colorPrimary = ThemeUtil.getColorPrimary(context)
        badgeTextColor = ThemeUtil.getColorHighlight(context)
        setOnItemClickListener { adapter, view, position ->
            val bean = this.data.getOrNull(position)
            if (bean != null && selectionEnabled) {
                onSelectionToggle?.invoke(bean)
                return@setOnItemClickListener
            }
            val user = this.data[position].user
            addUserBinder(view, user)
            val options = if (PxEZApp.animationEnable) {
                ActivityOptions.makeSceneTransitionAnimation(
                    context as Activity,
                    Pair(view, "shared_element_container")
                    //Pair(view.findViewById(R.id.imageview_usershow), "userimage")
                ).toBundle()
            } else null
            UserMActivity.start(context, user, options)
        }
        setOnItemLongClickListener { _, _, position ->
            val bean = this.data.getOrNull(position) ?: return@setOnItemLongClickListener false
            if (!selectionEnabled) {
                onSelectionStart?.invoke(bean)
                true
            } else {
                onSelectionToggle?.invoke(bean)
                true
            }
        }
    }
    // 只放封面,没有标题等文字;专用一个 RecyclerView,类型永远一致
    private fun bindNovelStrip(recyclerview: RecyclerView, item: UserPreviewsBean) {
        val adapter = recyclerview.adapter as? NovelCoverAdapter
        if (adapter == null) {
            val novelAdapter = NovelCoverAdapter(R.layout.view_user_illust_item)
            recyclerview.apply {
                this.adapter = novelAdapter
                layoutManager = LinearLayoutManager(
                    context,
                    LinearLayoutManager.HORIZONTAL,
                    false
                )
                setRecycledViewPool(novelPool)
                setHasFixedSize(true)
                isNestedScrollingEnabled = false
            }
            novelAdapter.setNewInstance(item.novels)
        } else {
            adapter.setNewInstance(item.novels)
        }
    }

    @SuppressLint("SetTextI18n")
    override fun convert(holder: BaseViewHolder, item: UserPreviewsBean) {
        if (item.user.is_blocked == true) {
            holder.itemView.visibility = View.GONE
            return
        }
        holder.itemView.visibility = View.VISIBLE
        // 先绑头像和名字,再决定下面展示什么作品条(以前有分支提前 return,漏了绑定)
        val userImage = holder.getView<NiceImageView>(R.id.imageview_usershow)
        userImage.setBorderColor(if (item.user.is_followed) badgeTextColor else colorPrimary)
        holder.getView<TextView>(R.id.textview_usershowname).text =
            "${item.user.name.ifBlank { item.user.account }} : ${item.user.account}"
        Glide.with(userImage.context).load(item.user.profile_image_urls.medium)
            .circleCrop().transition(withCrossFade())
            .into(userImage)
        // 多选:选中的卡片描一圈强调色,未选中恢复原样
        val selected = selectionEnabled && item.user.id in selectedIds
        val strokePx = (3 * holder.itemView.resources.displayMetrics.density).toInt()
        holder.getViewOrNull<MaterialCardView>(R.id.cardview)?.apply {
            strokeWidth = if (selected) strokePx else 0
            if (selected) strokeColor = colorPrimary
        }
        val illustRv = holder.getView<RecyclerView>(R.id.recyclerview_usershow)
        val novelRv = holder.getView<RecyclerView>(R.id.recyclerview_usershow_novel)
        val showIllust = stripMode != StripMode.NOVEL && item.illusts.isNotEmpty()
        val showNovel = !showIllust &&
            stripMode != StripMode.ILLUST && item.novels.isNotEmpty()
        when {
            showIllust -> {
                illustRv.visibility = View.VISIBLE
                novelRv.visibility = View.GONE
            }

            showNovel -> {
                illustRv.visibility = View.GONE
                novelRv.visibility = View.VISIBLE
                bindNovelStrip(novelRv, item)
                return
            }

            else -> {
                illustRv.visibility = View.GONE
                novelRv.visibility = View.GONE
                return
            }
        }
        run {
            val userShowIllustAdapter: SquareMediumAdapter
            if (illustRv.adapter == null) {
                userShowIllustAdapter =
                    SquareMediumAdapter(R.layout.view_user_illust_item, item.illusts)
                illustRv.apply {
                    adapter = userShowIllustAdapter
                    layoutManager = LinearLayoutManager(
                        holder.itemView.context,
                        LinearLayoutManager.HORIZONTAL,
                        false
                    )
                    setRecycledViewPool(mSharedPool)
                    setHasFixedSize(true)
                    isNestedScrollingEnabled = false
                }
            } else {
                val existing = illustRv.adapter as? SquareMediumAdapter
                if (existing == null) {
                    val fresh = SquareMediumAdapter(R.layout.view_user_illust_item, item.illusts)
                    illustRv.adapter = fresh
                    illustRv.setRecycledViewPool(mSharedPool)
                    userShowIllustAdapter = fresh
                } else {
                    val itemCount = existing.data.size
                    existing.setNewInstance(item.illusts)
                    existing.notifyItemRangeChanged(0, itemCount)
                    userShowIllustAdapter = existing
                }
            }

            userShowIllustAdapter.setOnItemClickListener { adapter, view, position ->
                val user = item.user
                addUserBinder(holder.itemView, user)
                DataHolder.setIllustList(userShowIllustAdapter.data)
                val options = if (PxEZApp.animationEnable) {
                    val mainimage = view.findViewById<ImageView>(R.id.imageview)
                    ActivityOptions.makeSceneTransitionAnimation(
                        context as Activity,
                        Pair(mainimage, "mainimage"),
                        //Pair(username, "username"),
                        //Pair(userImage, "userimage")
                    ).toBundle()
                } else null
                PictureActivity.start(
                    context,
                    userShowIllustAdapter.data[position].id,
                    position,
                    squareThumbHint = true,
                    options = options
                )
            }
        }
    }

    private fun addUserBinder(rootView: View, user: User) {
        tempLiveData.overrideValue(user.is_followed)
        tempLiveData.observeAfterSet(recyclerView.context as LifecycleOwner) {
            //if (it==tempLiveData.lastValue)
            //    return@observeAfterSet
            rootView.findViewById<NiceImageView>(R.id.imageview_usershow)
                .setBorderColor(if (user.is_followed) badgeTextColor else colorPrimary)
        }
        user.addBinder(user.account, tempLiveData)
    }
}
