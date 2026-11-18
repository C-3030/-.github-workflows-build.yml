package com.perol.asdpl.pixivez.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.perol.asdpl.pixivez.R
import com.perol.asdpl.pixivez.base.RinkActivity
import com.perol.asdpl.pixivez.core.NavConfig
import com.perol.asdpl.pixivez.core.NavPage

/** 编辑底部导航：勾选显示哪些页面，拖动排序 */
class NavEditActivity : RinkActivity() {

    private val items = mutableListOf<Pair<NavPage, Boolean>>()
    private lateinit var adapter: Adapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_nav_edit)

        val hidden = NavConfig.hidden()
        items.clear()
        NavConfig.order().mapNotNull { NavPage.byKey(it) }.forEach {
            items.add(it to (it.key !in hidden))
        }

        val toolbar = findViewById<MaterialToolbar>(R.id.navEditToolbar)
        toolbar.setTitle(R.string.nav_bar_edit)
        toolbar.setNavigationOnClickListener { save(); finish() }

        val recycler = findViewById<RecyclerView>(R.id.navEditList)
        recycler.layoutManager = LinearLayoutManager(this)
        adapter = Adapter()
        recycler.adapter = adapter

        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(
                rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder
            ): Boolean {
                val from = vh.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                if (from < 0 || to < 0 || from >= items.size || to >= items.size) return false
                val moved = items.removeAt(from)
                items.add(to, moved)
                adapter.notifyItemMoved(from, to)
                return true
            }

            override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) = Unit
        }).attachToRecyclerView(recycler)
    }

    private fun save() {
        NavConfig.saveOrder(items.map { it.first.key })
        NavConfig.saveHidden(items.filterNot { it.second }.map { it.first.key }.toSet())
        setResult(RESULT_OK)
    }

    override fun onBackPressed() {
        save()
        super.onBackPressed()
    }

    private inner class Adapter : RecyclerView.Adapter<Adapter.Holder>() {
        inner class Holder(v: View) : RecyclerView.ViewHolder(v) {
            val check: CheckBox = v.findViewById(R.id.navEditCheck)
            val title: TextView = v.findViewById(R.id.navEditTitle)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
            Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_nav_edit, parent, false))

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val (page, visible) = items[position]
            holder.title.text = page.title
            holder.check.isChecked = visible
            holder.check.setOnCheckedChangeListener { _, checked ->
                val idx = holder.bindingAdapterPosition
                if (idx in items.indices) items[idx] = items[idx].first to checked
            }
        }
    }
}
