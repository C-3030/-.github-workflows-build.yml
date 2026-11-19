package com.perol.asdpl.pixivez.ui.search

import android.app.Dialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.widget.ArrayAdapter
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.perol.asdpl.pixivez.R
import com.perol.asdpl.pixivez.data.RetrofitRepository
import com.perol.asdpl.pixivez.data.SearchFilterStore
import com.perol.asdpl.pixivez.services.Event
import com.perol.asdpl.pixivez.services.FlowEventBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 搜索筛选:自定义「必须有的标签」和「不能有的标签」。
 * 输入时调 pixiv 标签联想(输入「白」能出「白上フブキ」等),选中即加成一枚可删的 chip。
 */
class SearchFilterDialog : DialogFragment() {
    private val include = mutableListOf<String>()
    private val exclude = mutableListOf<String>()

    private lateinit var includeChips: ChipGroup
    private lateinit var excludeChips: ChipGroup

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private val debounce = Handler(Looper.getMainLooper())

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        include.addAll(SearchFilterStore.includeTags())
        exclude.addAll(SearchFilterStore.excludeTags())
        val view = layoutInflater.inflate(R.layout.dialog_search_filter, null)
        includeChips = view.findViewById(R.id.include_chips)
        excludeChips = view.findViewById(R.id.exclude_chips)
        bindInput(view.findViewById(R.id.include_input), include, includeChips)
        bindInput(view.findViewById(R.id.exclude_input), exclude, excludeChips)
        renderPresets(view.findViewById(R.id.preset_chips))
        view.findViewById<android.view.View>(R.id.tag_favorites).setOnClickListener {
            TagFavoriteDialog.show(parentFragmentManager)
        }
        renderChips(include, includeChips)
        renderChips(exclude, excludeChips)
        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.search_filter)
            .setView(view)
            .setPositiveButton(R.string.ok) { _, _ -> commit() }
            .setNeutralButton(R.string.search_filter_clear) { _, _ ->
                include.clear()
                exclude.clear()
                commit()
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
    }

    // 常用标签:点一下选"加入包含"或"加入排除"
    private fun renderPresets(chips: ChipGroup) {
        chips.removeAllViews()
        presets.forEach { preset ->
            val chip = Chip(requireContext()).apply {
                text = if (preset.label == preset.tag) preset.label
                else "${preset.label}(${preset.tag})"
                isCheckable = false
                setOnClickListener {
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle("${preset.label} = ${preset.tag}")
                        .setItems(
                            arrayOf(
                                getString(R.string.search_filter_add_to_include),
                                getString(R.string.search_filter_add_to_exclude)
                            )
                        ) { d, which ->
                            d.dismiss()
                            if (which == 0) addTag(preset.tag, include, includeChips)
                            else addTag(preset.tag, exclude, excludeChips)
                        }
                        .setNegativeButton(R.string.cancel, null)
                        .show()
                }
            }
            chips.addView(chip)
        }
    }

    private fun commit() {
        SearchFilterStore.save(include, exclude)
        FlowEventBus.post(Event.SearchFilterChanged)
    }

    /** 输入框:300ms 防抖查联想词,回车或点选即添加 */
    private fun bindInput(
        input: MaterialAutoCompleteTextView,
        target: MutableList<String>,
        chips: ChipGroup
    ) {
        val adapter = ArrayAdapter<String>(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            mutableListOf()
        )
        input.setAdapter(adapter)
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val text = s?.toString()?.trim().orEmpty()
                debounce.removeCallbacksAndMessages(null)
                if (text.isEmpty()) return
                debounce.postDelayed({ loadSuggestions(text, adapter, input) }, 300)
            }
        })
        input.setOnItemClickListener { _, _, position, _ ->
            adapter.getItem(position)?.let { addTag(it, target, chips) }
            input.setText("")
        }
        input.setOnEditorActionListener { _, _, _ ->
            val text = input.text?.toString()?.trim().orEmpty()
            if (text.isEmpty()) {
                false
            } else {
                addTag(text, target, chips)
                input.setText("")
                true
            }
        }
    }

    private fun loadSuggestions(
        prefix: String,
        adapter: ArrayAdapter<String>,
        input: MaterialAutoCompleteTextView
    ) {
        scope.launch {
            try {
                val names = RetrofitRepository.getInstance().api
                    .getSearchAutoCompleteKeywords(prefix).tags
                    .map { it.name }
                    .distinct()
                adapter.clear()
                adapter.addAll(names)
                adapter.notifyDataSetChanged()
                if (names.isNotEmpty() && input.isFocused) input.showDropDown()
            } catch (e: Exception) {
                // 联想失败不影响手动输入标签
            }
        }
    }

    private fun addTag(word: String, target: MutableList<String>, chips: ChipGroup) {
        val name = word.trim()
        if (name.isEmpty() || target.any { it.equals(name, true) }) return
        target.add(name)
        renderChips(target, chips)
    }

    private fun renderChips(target: MutableList<String>, chips: ChipGroup) {
        chips.removeAllViews()
        target.forEach { word ->
            val chip = Chip(requireContext()).apply {
                text = word
                isCloseIconVisible = true
                setOnCloseIconClickListener {
                    target.remove(word)
                    renderChips(target, chips)
                }
            }
            chips.addView(chip)
        }
    }

    override fun onDestroyView() {
        debounce.removeCallbacksAndMessages(null)
        scope.cancel()
        super.onDestroyView()
    }

    // pixiv 上真实存在的标签名(中文只是给按钮看的说明)
    private data class Preset(val label: String, val tag: String)

    private val presets = listOf(
        Preset("百合", "百合"),
        Preset("R-18", "R-18"),
        Preset("R-18G", "R-18G"),
        Preset("扶她", "ふたなり"),
        Preset("伪娘", "男の娘"),
        Preset("虐杀", "リョナ"),
        Preset("重口", "グロ"),
        Preset("NTR", "NTR"),
        Preset("母女", "親子"),
        Preset("萝莉", "ロリ"),
        Preset("巨乳", "巨乳")
    )

    companion object {
        private const val TAG = "SearchFilterDialog"

        fun show(manager: FragmentManager) {
            if (!manager.isStateSaved) SearchFilterDialog().show(manager, TAG)
        }
    }
}
