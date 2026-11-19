package com.perol.asdpl.pixivez.ui.manager

import com.chad.brvah.BaseQuickAdapter
import com.chad.brvah.viewholder.BaseViewHolder
import com.perol.asdpl.pixivez.R
import com.perol.asdpl.pixivez.data.NovelDownload
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 下载进度页里的"小说"列表:标题 + 格式/大小/时间 */
class NovelDownloadAdapter :
    BaseQuickAdapter<NovelDownload, BaseViewHolder>(R.layout.item_novel_download) {

    private val timeFormat = SimpleDateFormat("MM-dd HH:mm", Locale.ROOT)

    override fun convert(holder: BaseViewHolder, item: NovelDownload) {
        holder.setText(R.id.novel_title, item.title)
        val size = when {
            item.size > 1024 * 1024 -> String.format(Locale.ROOT, "%.1fMB", item.size / 1048576f)
            item.size > 1024 -> String.format(Locale.ROOT, "%.0fKB", item.size / 1024f)
            else -> "${item.size}B"
        }
        val missing = if (File(item.path).exists()) "" else " · 文件已删除"
        holder.setText(
            R.id.novel_meta,
            "${item.format} · $size · ${timeFormat.format(Date(item.time))} · ${item.author}$missing"
        )
    }
}
