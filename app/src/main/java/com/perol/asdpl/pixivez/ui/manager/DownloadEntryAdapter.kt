package com.perol.asdpl.pixivez.ui.manager

import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import com.bumptech.glide.Glide
import com.chad.brvah.BaseQuickAdapter
import com.chad.brvah.viewholder.BaseViewHolder
import com.ketch.Status
import com.perol.asdpl.pixivez.R

/** 下载页行:左封面 + 右上标题 + 右下进度 + 右侧暂停/继续 */
class DownloadEntryAdapter :
    BaseQuickAdapter<DownloadEntry, BaseViewHolder>(R.layout.item_download_row) {

    /** 点暂停/继续按钮时回调 */
    var onToggle: ((DownloadEntry) -> Unit)? = null

    override fun convert(holder: BaseViewHolder, item: DownloadEntry) {
        holder.setText(R.id.row_title, item.title)
        holder.setText(R.id.row_status, item.subtitle)
        val progress = holder.getView<ProgressBar>(R.id.row_progress)
        progress.progress = item.progress
        // 已完成/文件类不显示进度条
        progress.visibility = if (item.isDone) View.GONE else View.VISIBLE

        // 封面:小说优先用缩略图 URL;插画用本地文件或图片地址
        val cover = holder.getView<ImageView>(R.id.row_cover)
        val source: Any? = when {
            !item.coverUrl.isNullOrBlank() -> item.coverUrl
            item.file != null && item.file.exists() && !item.isNovel -> item.file
            else -> null
        }
        Glide.with(cover).load(source).centerCrop().placeholder(R.color.grayx).into(cover)

        // 只有进行中/暂停的下载才显示暂停/继续按钮
        val toggle = holder.getView<ImageButton>(R.id.row_toggle)
        val active = item.model != null && item.model.status in setOf(Status.QUEUED, Status.STARTED, Status.PROGRESS, Status.PAUSED)
        toggle.visibility = if (active) View.VISIBLE else View.GONE
        if (active) {
            val paused = item.model.status == Status.PAUSED
            toggle.setImageResource(if (paused) R.drawable.ic_nv_play else R.drawable.ic_nv_pause)
            toggle.setOnClickListener { onToggle?.invoke(item) }
        }
    }
}
