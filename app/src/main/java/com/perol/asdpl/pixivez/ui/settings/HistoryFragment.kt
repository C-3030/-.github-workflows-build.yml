package com.perol.asdpl.pixivez.ui.settings

import android.app.ActivityOptions
import android.os.Bundle
import android.util.Pair
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.perol.asdpl.pixivez.R
import com.perol.asdpl.pixivez.base.MaterialDialogs
import com.perol.asdpl.pixivez.data.entity.HistoryEntity
import com.perol.asdpl.pixivez.databinding.FragmentHistoryBinding
import com.perol.asdpl.pixivez.services.PxEZApp
import com.perol.asdpl.pixivez.ui.novel.NovelActivity
import com.perol.asdpl.pixivez.ui.pic.PictureActivity
import com.perol.asdpl.pixivez.ui.user.UserMActivity

class HistoryFragment : Fragment() {
    private lateinit var historyAdapter: HistoryAdapter
    private lateinit var novelHistoryAdapter: HistoryNovelAdapter
    private lateinit var binding: FragmentHistoryBinding
    private val historyMViewModel: HistoryViewModel by viewModels()

    // 两个分栏:插画(图片墙)/ 小说(卡片)
    private var allHistory: List<HistoryEntity> = listOf()
    private var tabIndex = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentHistoryBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        historyMViewModel.history.observe(requireActivity()) {
            allHistory = it ?: listOf()
            renderTab()
        }
        historyMViewModel.first()

        historyAdapter = HistoryAdapter()
        novelHistoryAdapter = HistoryNovelAdapter()
        binding.historyTabs.addTab(binding.historyTabs.newTab().setText(R.string.history_illust))
        binding.historyTabs.addTab(binding.historyTabs.newTab().setText(R.string.history_novel))
        binding.historyTabs.addOnTabSelectedListener(object :
            com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab) {
                tabIndex = tab.position
                renderTab()
            }

            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab) {}
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab) {}
        })
        historyAdapter.setOnItemClickListener { _, view, position ->
            val item = historyAdapter.data[position]
            val options = if (PxEZApp.animationEnable) {
                ActivityOptions.makeSceneTransitionAnimation(
                    requireActivity(),
                    Pair(view, "shared_element_container")
                ).toBundle()
            } else null
            if (item.isUser) UserMActivity.start(requireContext(), item.id, options)
            else PictureActivity.start(requireContext(), item.id, options = options)
        }
        historyAdapter.setOnItemLongClickListener { _, _, i ->
            confirmDelete(historyAdapter.data[i]) {
                novelHistoryAdapter.notifyDataSetChanged()
            }
            true
        }
        novelHistoryAdapter.setOnItemLongClickListener { _, _, i ->
            confirmDelete(novelHistoryAdapter.data[i]) {
                historyAdapter.notifyDataSetChanged()
            }
            true
        }
        renderTab()
        binding.fab.setOnClickListener {
            MaterialDialogs(requireContext()).show {
                setTitle(R.string.clearhistory)
                confirmButton { _, _ ->
                    historyMViewModel.clearHistory()
                }
            }
        }
    }

    // 按当前分栏切换数据与布局(插画=两列图墙,小说=单列卡片)
    private fun renderTab() {
        if (!this::historyAdapter.isInitialized) return
        val illust = allHistory.filter { !it.isNovel }
        val novels = allHistory.filter { it.isNovel }
        if (tabIndex == 0) {
            binding.recyclerview.layoutManager =
                GridLayoutManager(requireContext(), 2 * resources.configuration.orientation)
            binding.recyclerview.adapter = historyAdapter
            historyAdapter.setNewInstance(illust.toMutableList())
        } else {
            binding.recyclerview.layoutManager = LinearLayoutManager(requireContext())
            binding.recyclerview.adapter = novelHistoryAdapter
            novelHistoryAdapter.setNewInstance(novels.toMutableList())
        }
    }

    private fun confirmDelete(item: HistoryEntity, after: () -> Unit) {
        MaterialDialogs(requireContext()).show {
            setTitle(R.string.confirm_title)
            confirmButton { _, _ ->
                historyMViewModel.deleteItem(item) { after() }
            }
        }
    }
}
