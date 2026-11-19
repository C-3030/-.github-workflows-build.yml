package com.perol.asdpl.pixivez.ui.search

import android.content.Context
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.perol.asdpl.pixivez.R
import com.perol.asdpl.pixivez.data.TagFavoriteStore
import com.perol.asdpl.pixivez.objects.Toasty

/**
 * 点击作品里的标签:不再直接搜索,而是给两个选择 —— 搜索此标签 / 收藏标签。
 */
object TagActions {
    fun show(context: Context, tag: String) {
        MaterialAlertDialogBuilder(context)
            .setTitle("#$tag")
            .setItems(
                arrayOf(context.getString(R.string.tag_fav_search), context.getString(R.string.tag_fav_favorite))
            ) { d, which ->
                d.dismiss()
                if (which == 0) {
                    SearchActivity.start(context, tag)
                } else {
                    favorite(context, tag)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun favorite(context: Context, tag: String) {
        val groups = TagFavoriteStore.groups()
        if (groups.size <= 1) {
            TagFavoriteStore.add(tag)
            Toasty.normal(context, context.getString(R.string.tag_fav_added, tag))
            return
        }
        val names = groups.map { it.name }.toMutableList()
        names.add(context.getString(R.string.tag_fav_new_group))
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.tag_fav_group)
            .setItems(names.toTypedArray()) { d, which ->
                d.dismiss()
                if (which < groups.size) {
                    TagFavoriteStore.add(tag, groups[which].id)
                    Toasty.normal(context, context.getString(R.string.tag_fav_added, tag))
                } else {
                    val group = TagFavoriteStore.createGroup(tag)
                    TagFavoriteStore.add(tag, group.id)
                    Toasty.normal(context, context.getString(R.string.tag_fav_added, tag))
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
