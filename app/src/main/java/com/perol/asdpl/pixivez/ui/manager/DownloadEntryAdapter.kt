package com.perol.asdpl.pixivez.ui.manager

import android.widget.ImageView
import android.widget.ProgressBar
import com.bumptech.glide.Glide
import com.chad.brvah.BaseQuickAdapter
import com.chad.brvah.viewholder.BaseViewHolder
import com.perol.asdpl.pixivez.R

/** 下载页行:左封面 + 右上标题 + 右下进度 */
class DownloadEntryAdapter :
    BaseQuickAdapter<DownloadEntry, BaseViewHolder>(R.layout.item_download_row) {

    override fun convert(holder: BaseViewHolder, item: DownloadEntry) {
        holder.setText(R.id.row_title, item.title)
        holder.setText(R.id.row_status, item.subtitle)
        holder.getView<ProgressBar>(R.id.row_progress).progress = item.progress
        val cover = holder.getView<ImageView>(R.id.row_cover)
        val source: Any? = when {
            item.file != null && item.file.exists() -> item.file
            !item.coverUrl.isNullOrBlank() -> item.coverUrl
            else -> null
        }
        Glide.with(cover).load(source)
            .centerCrop()
            .placeholder(R.color.grayx)
            .into(cover)
    }
}
