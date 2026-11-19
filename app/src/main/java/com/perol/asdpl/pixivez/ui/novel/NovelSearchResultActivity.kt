package com.perol.asdpl.pixivez.ui.novel

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.MenuItem
import androidx.fragment.app.commit
import com.perol.asdpl.pixivez.R
import com.perol.asdpl.pixivez.base.RinkActivity
import com.perol.asdpl.pixivez.databinding.ActivityNovelSearchBinding

// 小说模式的轻量搜索结果页:直接承载泛化 NovelListFragment(Search),
// 不复用插画 SearchResult 的 tab 体系,保持清晰。
class NovelSearchResultActivity : RinkActivity() {
    private lateinit var binding: ActivityNovelSearchBinding

    private var word: String = ""
    private var sort: String = "date_desc"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNovelSearchBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        word = intent.getStringExtra(EXTRA_KEYWORD).orEmpty()
        supportActionBar?.title = word
        sort = savedInstanceState?.getString(KEY_SORT) ?: "date_desc"
        if (savedInstanceState == null) showResult()
    }

    private fun showResult() {
        supportFragmentManager.commit {
            replace(
                R.id.container,
                NovelListFragment.newInstance(
                    NOVEL_TAG.Search,
                    mutableMapOf("keyword" to word, "sort" to sort)
                )
            )
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_novel_search, menu)
        return super.onCreateOptionsMenu(menu)
    }

    private fun chooseSort() {
        val labels = arrayOf(
            getString(R.string.search_sort_date_desc),
            getString(R.string.search_sort_date_asc),
            getString(R.string.search_sort_length)
        )
        val values = arrayOf("date_desc", "date_asc", "length_desc")
        val current = values.indexOf(sort).coerceAtLeast(0)
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(R.string.search_sort)
            .setSingleChoiceItems(labels, current) { d, which ->
                d.dismiss()
                sort = values[which]
                showResult()
            }
            .show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_SORT, sort)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                finish()
                return true
            }

            R.id.action_search_filter -> {
                com.perol.asdpl.pixivez.ui.search.SearchFilterDialog.show(supportFragmentManager)
                return true
            }

            R.id.action_novel_sort -> {
                chooseSort()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    companion object {
        private const val EXTRA_KEYWORD = "keyword"
        private const val KEY_SORT = "novel_search_sort"

        fun start(context: Context, keyword: String) {
            context.startActivity(
                Intent(context, NovelSearchResultActivity::class.java)
                    .putExtra(EXTRA_KEYWORD, keyword)
            )
        }
    }
}
