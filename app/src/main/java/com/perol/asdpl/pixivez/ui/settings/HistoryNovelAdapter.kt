package com.perol.asdpl.pixivez.ui.settings

import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade
import com.chad.brvah.viewholder.BaseViewHolder
import com.perol.asdpl.pixivez.R
import com.perol.asdpl.pixivez.base.LBaseQuickAdapter
import com.perol.asdpl.pixivez.data.entity.HistoryEntity
import com.perol.asdpl.pixivez.ui.novel.NovelActivity

/** 历史里的"小说"分栏:复用小说列表的卡片样式(封面/标题/作者/字数·收藏) */
class HistoryNovelAdapter :
    LBaseQuickAdapter<HistoryEntity, BaseViewHolder>(R.layout.view_novel_item) {

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        setOnItemClickListener { _, _, position ->
            NovelActivity.start(context, data[position].id)
        }
    }

    override fun convert(holder: BaseViewHolder, item: HistoryEntity) {
        holder.getView<TextView>(R.id.novel_title).text = item.title
        holder.getView<TextView>(R.id.novel_author).text = item.author
        holder.getView<TextView>(R.id.novel_meta).text = item.meta
        val cover = holder.getView<ImageView>(R.id.novel_cover)
        Glide.with(cover).load(item.thumb)
            .transition(withCrossFade()).into(cover)
    }
}
