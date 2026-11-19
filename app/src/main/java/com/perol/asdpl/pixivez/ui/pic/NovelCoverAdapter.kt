package com.perol.asdpl.pixivez.ui.pic

import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade
import com.chad.brvah.viewholder.BaseViewHolder
import com.perol.asdpl.pixivez.R
import com.perol.asdpl.pixivez.base.LBaseQuickAdapter
import com.perol.asdpl.pixivez.data.model.Novel
import com.perol.asdpl.pixivez.ui.novel.NovelActivity

/** 只有封面的小说缩略条(关注用户卡片里用) */
class NovelCoverAdapter(layoutResId: Int) :
    LBaseQuickAdapter<Novel, BaseViewHolder>(layoutResId) {

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        setOnItemClickListener { _, _, position ->
            NovelActivity.start(context, data[position].id)
        }
    }

    override fun convert(holder: BaseViewHolder, item: Novel) {
        val imageView = holder.getView<ImageView>(R.id.imageview)
        Glide.with(imageView.context).load(item.image_urls.square_medium)
            .transition(withCrossFade()).centerCrop().into(imageView)
    }
}
