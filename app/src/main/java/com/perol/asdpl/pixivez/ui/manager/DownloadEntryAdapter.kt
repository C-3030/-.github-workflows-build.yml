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

/** 下载页行:左封面 + 右上标题 + 右下进度 + 右侧暂停/继续/重试 */
class DownloadEntryAdapter :
    BaseQuickAdapter<DownloadEntry, BaseViewHolder>(R.layout.item_download_row) {

    /** 点暂停/继续/重试按钮时回调 */
    var onToggle: ((DownloadEntry) -> Unit)? = null

    override fun convert(holder: BaseViewHolder, item: DownloadEntry) {
        holder.setText(R.id.row_title, item.title)
        holder.setText(R.id.row_status, item.subtitle)
        val progress = holder.getView<ProgressBar>(R.id.row_progress)
        progress.progress = item.progress
        // 已完成/文件类不显示进度条
        progress.visibility = if (item.isDone) View.GONE else View.VISIBLE

        // 封面:小说优先用缩略图;插画下完的直接读本地文件,避免网络请求失败
        val cover = holder.getView<ImageView>(R.id.row_cover)
        val localOk = item.file != null && item.file.exists() && item.isDone
        val source: Any? = when {
            !item.isNovel && localOk -> item.file
            !item.coverUrl.isNullOrBlank() -> item.coverUrl
            !item.isNovel && item.file != null && item.file.exists() -> item.file
            else -> null
        }
        Glide.with(cover).load(source).centerCrop().placeholder(R.color.grayx).into(cover)

        val toggle = holder.getView<ImageButton>(R.id.row_toggle)
        val status = item.model?.status
        when {
            // 失败/取消 → 重试
            status == Status.FAILED || status == Status.CANCELLED -> {
                toggle.visibility = View.VISIBLE
                toggle.setImageResource(R.drawable.ic_nv_refresh)
                toggle.contentDescription = context.getString(R.string.download_retry)
                toggle.setOnClickListener { onToggle?.invoke(item) }
            }
            // 进行中 / 已暂停 → 暂停 / 继续
            status == Status.QUEUED || status == Status.STARTED ||
                status == Status.PROGRESS || status == Status.PAUSED -> {
                toggle.visibility = View.VISIBLE
                toggle.setImageResource(
                    if (status == Status.PAUSED) R.drawable.ic_nv_play else R.drawable.ic_nv_pause
                )
                toggle.contentDescription = context.getString(R.string.download_pause_resume)
                toggle.setOnClickListener { onToggle?.invoke(item) }
            }

            else -> toggle.visibility = View.GONE
        }
    }
}
