package com.perol.asdpl.pixivez.ui.manager

import android.widget.ImageView
import android.widget.ProgressBar
import com.bumptech.glide.Glide
import com.chad.brvah.BaseQuickAdapter
import com.chad.brvah.viewholder.BaseViewHolder
import com.perol.asdpl.pixivez.R
import com.perol.asdpl.pixivez.data.NovelDownload
import com.perol.asdpl.pixivez.objects.FileOpenUtil
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 下载页里的"小说"行:封面 + 标题 + 格式/大小 + 进度条 */
class NovelDownloadAdapter :
    BaseQuickAdapter<NovelDownload, BaseViewHolder>(R.layout.item_download_row) {

    private val timeFormat = SimpleDateFormat("MM-dd HH:mm", Locale.ROOT)

    init {
        setOnItemClickListener { _, _, position ->
            val item = data[position]
            FileOpenUtil.open(context, File(item.path))
        }
        setOnItemLongClickListener { _, _, position ->
            val item = data[position]
            com.perol.asdpl.pixivez.base.MaterialDialogs(context).show {
                setTitle(item.path)
                confirmButton { _, _ ->
                    com.perol.asdpl.pixivez.data.NovelDownloadStore.remove(item)
                    this@NovelDownloadAdapter.setList(
                        com.perol.asdpl.pixivez.data.NovelDownloadStore.all()
                    )
                }
                cancelButton()
            }
            true
        }
    }

    override fun convert(holder: BaseViewHolder, item: NovelDownload) {
        holder.setText(R.id.row_title, item.title)
        val size = when {
            item.size > 1024 * 1024 -> String.format(Locale.ROOT, "%.1fMB", item.size / 1048576f)
            item.size > 1024 -> String.format(Locale.ROOT, "%.0fKB", item.size / 1024f)
            else -> "${item.size}B"
        }
        val missing = if (File(item.path).exists()) "" else " · 文件已删除"
        holder.setText(
            R.id.row_status,
            "${item.format} · $size · ${timeFormat.format(Date(item.time))}$missing"
        )
        holder.getView<ProgressBar>(R.id.row_progress).progress = 100
        val cover = holder.getView<ImageView>(R.id.row_cover)
        Glide.with(cover).load(item.thumb.ifBlank { null })
            .centerCrop()
            .placeholder(R.color.grayx)
            .into(cover)
    }
}
