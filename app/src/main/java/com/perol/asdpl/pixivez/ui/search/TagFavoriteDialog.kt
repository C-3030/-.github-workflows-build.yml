package com.perol.asdpl.pixivez.ui.search

import android.app.Dialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.perol.asdpl.pixivez.R
import com.perol.asdpl.pixivez.data.RetrofitRepository
import com.perol.asdpl.pixivez.data.TagFavoriteStore
import com.perol.asdpl.pixivez.data.TagGroup
import com.perol.asdpl.pixivez.objects.Toasty
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 收藏标签管理:按分组列出已收藏标签。
 * 点标签 → 加入包含 / 加入排除 / 取消收藏;长按 → 取消收藏。
 * 顶部可输入标签(带联想)并选分组后添加;也能新建分组。
 */
class TagFavoriteDialog : DialogFragment() {
    private lateinit var container: LinearLayout
    private lateinit var input: MaterialAutoCompleteTextView
    private lateinit var groupPicker: MaterialAutoCompleteTextView
    private var groups: List<TagGroup> = emptyList()
    private var selectedGroup = TagFavoriteStore.DEFAULT_GROUP
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = layoutInflater.inflate(R.layout.dialog_tag_favorite, null)
        container = view.findViewById(R.id.tag_group_container)
        input = view.findViewById(R.id.tag_input)
        groupPicker = view.findViewById(R.id.group_input)
        bindInput()
        bindGroupPicker()
        view.findViewById<View>(R.id.group_new).setOnClickListener { promptNewGroup() }
        renderGroups()
        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.tag_favorites)
            .setView(view)
            .setPositiveButton(R.string.ok, null)
            .setNegativeButton(R.string.cancel, null)
            .create()
    }

    private fun bindInput() {
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
                if (text.isEmpty()) return
                scope.launch {
                    try {
                        val names = RetrofitRepository.getInstance().api
                            .getSearchAutoCompleteKeywords(text).tags
                            .map { it.name }.distinct()
                        adapter.clear()
                        adapter.addAll(names)
                        adapter.notifyDataSetChanged()
                        if (names.isNotEmpty() && input.isFocused) input.showDropDown()
                    } catch (e: Exception) {
                        // 联想失败不影响手动输入
                    }
                }
            }
        })
        input.setOnItemClickListener { _, _, position, _ ->
            val name = adapter.getItem(position) ?: return@setOnItemClickListener
            addTag(name)
        }
        input.setOnEditorActionListener { _, _, _ ->
            val text = input.text?.toString()?.trim().orEmpty()
            if (text.isEmpty()) false else {
                addTag(text)
                true
            }
        }
    }

    private fun bindGroupPicker() {
        refreshGroupPicker()
        groupPicker.setOnItemClickListener { _, _, position, _ ->
            val group = groups.getOrNull(position) ?: return@setOnItemClickListener
            selectedGroup = group.id
            groupPicker.setText(group.name, false)
        }
    }

    private fun refreshGroupPicker() {
        groups = TagFavoriteStore.groups()
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            groups.map { it.name }
        )
        groupPicker.setAdapter(adapter)
        val current = groups.firstOrNull { it.id == selectedGroup } ?: groups.first()
        selectedGroup = current.id
        groupPicker.setText(current.name, false)
    }

    private fun addTag(name: String) {
        TagFavoriteStore.add(name, selectedGroup)
        input.setText("")
        renderGroups()
    }

    private fun promptNewGroup() {
        val edit = android.widget.EditText(requireContext()).apply {
            hint = getString(R.string.user_group_name_hint)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.tag_fav_new_group)
            .setView(edit)
            .setPositiveButton(R.string.ok) { _, _ ->
                val name = edit.text?.toString()?.trim().orEmpty()
                if (name.isNotEmpty()) {
                    val group = TagFavoriteStore.createGroup(name)
                    selectedGroup = group.id
                    refreshGroupPicker()
                    renderGroups()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun renderGroups() {
        groups = TagFavoriteStore.groups()
        container.removeAllViews()
        groups.forEach { group ->
            container.addView(TextView(requireContext()).apply {
                text = "${group.name} (${group.tags.size})"
                setPadding(0, 16, 0, 4)
                textSize = 14f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            val chips = ChipGroup(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            group.tags.forEach { tag ->
                chips.addView(Chip(requireContext()).apply {
                    text = tag
                    isCloseIconVisible = true
                    setOnCloseIconClickListener {
                        TagFavoriteStore.remove(tag)
                        renderGroups()
                    }
                    setOnClickListener { tagMenu(tag) }
                })
            }
            container.addView(chips)
        }
    }

    private fun tagMenu(tag: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("#$tag")
            .setItems(
                arrayOf(
                    getString(R.string.search_filter_add_to_include),
                    getString(R.string.search_filter_add_to_exclude),
                    getString(R.string.tag_fav_remove)
                )
            ) { d, which ->
                d.dismiss()
                when (which) {
                    0 -> {
                        val include = SearchFilterStoreAccess.include() + tag
                        SearchFilterStoreAccess.save(include.distinct(), SearchFilterStoreAccess.exclude())
                        Toasty.normal(requireContext(), getString(R.string.tag_fav_added_include))
                        notifyChanged()
                    }

                    1 -> {
                        val exclude = SearchFilterStoreAccess.exclude() + tag
                        SearchFilterStoreAccess.save(SearchFilterStoreAccess.include(), exclude.distinct())
                        Toasty.normal(requireContext(), getString(R.string.tag_fav_added_exclude))
                        notifyChanged()
                    }

                    else -> {
                        TagFavoriteStore.remove(tag)
                        renderGroups()
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun notifyChanged() {
        parentFragmentManager.setFragmentResult(RESULT_KEY, Bundle())
    }

    override fun onDestroyView() {
        scope.cancel()
        super.onDestroyView()
    }

    override fun onDestroy() {
        // 关闭时通知结果页按新的筛选条件重搜
        com.perol.asdpl.pixivez.services.FlowEventBus.post(
            com.perol.asdpl.pixivez.services.Event.SearchFilterChanged
        )
        super.onDestroy()
    }

    companion object {
        const val RESULT_KEY = "tag_favorite_changed"
        private const val TAG = "TagFavoriteDialog"

        fun show(manager: FragmentManager) {
            if (!manager.isStateSaved) TagFavoriteDialog().show(manager, TAG)
        }
    }
}

/** 让对话框里能直接改筛选条件 */
internal object SearchFilterStoreAccess {
    fun include(): List<String> =
        com.perol.asdpl.pixivez.data.SearchFilterStore.includeTags()

    fun exclude(): List<String> =
        com.perol.asdpl.pixivez.data.SearchFilterStore.excludeTags()

    fun save(include: List<String>, exclude: List<String>) =
        com.perol.asdpl.pixivez.data.SearchFilterStore.save(include, exclude)
}
