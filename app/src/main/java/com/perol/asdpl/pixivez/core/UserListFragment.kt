/*
 * MIT License
 *
 * Copyright (c) 2020 ultranity
 * Copyright (c) 2019 Perol_Notsfsssf
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE
 */

package com.perol.asdpl.pixivez.core

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.perol.asdpl.pixivez.R
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.perol.asdpl.pixivez.base.BaseVBFragment
import com.perol.asdpl.pixivez.base.MaterialDialogs
import com.perol.asdpl.pixivez.base.getInputField
import com.perol.asdpl.pixivez.base.setInput
import com.perol.asdpl.pixivez.data.AppDataRepo
import com.perol.asdpl.pixivez.data.FollowGroupStore
import com.perol.asdpl.pixivez.data.model.UserPreviewsBean
import com.perol.asdpl.pixivez.databinding.FragmentListBinding
import com.perol.asdpl.pixivez.databinding.ViewUserGroupBarBinding
import com.perol.asdpl.pixivez.databinding.ViewUserWorksSwitchBinding
import com.perol.asdpl.pixivez.databinding.ViewRestrictButtonBinding
import com.perol.asdpl.pixivez.databinding.ViewUserListHeaderBinding
import com.perol.asdpl.pixivez.objects.InteractionUtil.visRestrictTag
import com.perol.asdpl.pixivez.objects.Toasty
import com.perol.asdpl.pixivez.objects.argument
import com.perol.asdpl.pixivez.objects.argumentNullable
import com.perol.asdpl.pixivez.objects.getMaxColumn
import com.perol.asdpl.pixivez.services.Event
import com.perol.asdpl.pixivez.services.FlowEventBus
import com.perol.asdpl.pixivez.ui.FragmentActivity
import com.perol.asdpl.pixivez.view.AverageGridItemDecoration
import kotlinx.coroutines.launch

/**
 * Fragment of userid following/followed users
 */
class UserListFragment : BaseVBFragment<FragmentListBinding>() {
    override fun loadData() {
        viewModel.onLoadFirst()
    }

    override fun onResume() {
        isLoaded = userListAdapter.data.isNotEmpty()
        super.onResume()
    }

    override var TAG: String by argument("UserListFragment")

    // -----------------
    private var keyword: String? by argumentNullable()

    // -----------------
    private var userid: Int? by argumentNullable()
    // -----------------

    private var _header: ViewUserListHeaderBinding? = null
    private var stripMode = UserListAdapter.StripMode.ALL
    private var _bindingHeader: ViewRestrictButtonBinding? = null
    private val bindingHeader: ViewRestrictButtonBinding
        get() = requireNotNull(_bindingHeader) { "The property of binding has been destroyed." }
    private lateinit var userListAdapter: UserListAdapter
    private val viewModel: UserListViewModel by viewModels()

    // ---- 关注用户分组 + 列表内多选 ----
    /** 只有自己的"关注用户"页有分组:粉丝/搜索/相关列表没有归属关系 */
    private val groupEnabled: Boolean
        get() = TAG == "Following" && AppDataRepo.isSelfPage(userid ?: 0)
    private var groupFilter = FollowGroupStore.ALL
    private var selecting = false
    private val selected = LinkedHashSet<Int>()
    private var fullData: MutableList<UserPreviewsBean> = mutableListOf()
    private var _groupBar: ViewUserGroupBarBinding? = null
    private val groupBar: ViewUserGroupBarBinding?
        get() = _groupBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (keyword != null) {
            viewModel.keyword = keyword!!
        } else if (userid != null) {
            viewModel.userid = userid!!
        }
        viewModel.setonLoadFirstRx(TAG)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        if (viewModel.needHeader && _header == null) {
            _header = ViewUserListHeaderBinding.inflate(inflater)
            _bindingHeader = _header!!.restrict
        }
        return super.onCreateView(inflater, container, savedInstanceState)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        userListAdapter = UserListAdapter(R.layout.view_usershow_item)

        initViewModel()

        if (viewModel.needHeader) {
            userListAdapter.addHeaderView(requireNotNull(_header) { "header missing" }.root)
            // 右侧按钮轮换:全部 → 插画 → 小说 → 全部
            renderWorksButton()
            _header!!.buttonWorksType.setOnClickListener {
                stripMode = when (stripMode) {
                    UserListAdapter.StripMode.ALL -> UserListAdapter.StripMode.ILLUST
                    UserListAdapter.StripMode.ILLUST -> UserListAdapter.StripMode.NOVEL
                    UserListAdapter.StripMode.NOVEL -> UserListAdapter.StripMode.ALL
                }
                userListAdapter.stripMode = stripMode
                renderWorksButton()
                applyFilter()
            }
            bindingHeader.buttonPublic.isChecked = true
            bindingHeader.buttonAll.visibility = View.GONE
            bindingHeader.toggleRestrict.addOnButtonCheckedListener { group, checkedId, isChecked ->
                if (isChecked) {
                    val checkedIndex = group.indexOfChild(group.findViewById(checkedId))
                    val value = visRestrictTag(checkedIndex == 2)
                    if (viewModel.restrict.value != value) {
                        viewModel.restrict.value = value
                        viewModel.onLoadFirst()
                    }
                }
            }
        }
        binding.recyclerview.apply {
            adapter = userListAdapter
            layoutManager =
                GridLayoutManager(requireContext(), getMaxColumn(UserListAdapter.ITEM_WIDTH))
            addItemDecoration(AverageGridItemDecoration(UserListAdapter.itemWidthPx))
            // FlexboxLayoutManager(context, FlexDirection.ROW, FlexWrap.WRAP)
            //    .apply { justifyContent = JustifyContent.SPACE_AROUND }
        }
        userListAdapter.setOnLoadMoreListener {
            viewModel.onLoadMore()
        }
        if (groupEnabled) {
            // 只有分组可用的列表才开放多选,否则选了也无处可移
            userListAdapter.onSelectionStart = { bean -> enterSelection(bean) }
            userListAdapter.onSelectionToggle = { bean -> toggleSelection(bean) }
            setHasOptionsMenu(true)
            activity?.invalidateOptionsMenu()
            _groupBar = ViewUserGroupBarBinding.inflate(layoutInflater)
            userListAdapter.addHeaderView(_groupBar!!.root)
            _groupBar!!.groupEdit.setOnClickListener { showGroupManager() }
            renderGroupChips()
        }
        binding.swipeRefreshLayout.setOnRefreshListener {
            viewModel.onLoadFirst()
        }
        lifecycleScope.launch {
            FlowEventBus.observe<Event.BlockUsersChanged>(viewLifecycleOwner) {
                userListAdapter.data.forEach { user ->
                    if (it.diff == user.user.id) {
                        user.user.is_blocked = it.add
                        userListAdapter.notifyItemChanged(userListAdapter.getItemPosition(user))
                    }
                }
            }
        }
    }

    private fun initViewModel() {
        viewModel.data.observe(viewLifecycleOwner) {
            if (it != null) {
                fullData = it
                autoLoadCount = 0
                applyFilter()
            } else {
                userListAdapter.loadMoreFail()
            }
        }
        viewModel.nextUrl.observe(viewLifecycleOwner) {
            if (it != null) {
                userListAdapter.loadMoreComplete()
            } else {
                userListAdapter.loadMoreEnd()
            }
        }
        viewModel.dataAdded.observe(viewLifecycleOwner) {
            autoLoading = false
            if (it != null) {
                fullData.addAll(it)
                val extra = visible(it)
                if (extra.isNotEmpty()) userListAdapter.addData(extra)
                if (groupEnabled) renderGroupChips()
                // 拉完一页可能还是不够一屏,继续自动拉
                maybeAutoLoad()
            } else {
                autoLoading = false
                userListAdapter.loadMoreFail()
            }
        }
        viewModel.isRefreshing.observe(viewLifecycleOwner) {
            binding.swipeRefreshLayout.isRefreshing = it
        }
    }

    override fun onDestroy() {
        _bindingHeader = null
        _header = null
        _groupBar = null
        super.onDestroy()
    }

    // ================= 分组 & 多选 =================

    /** 当前分组过滤下的可见数据 */
    private fun visible(list: List<UserPreviewsBean>): MutableList<UserPreviewsBean> {
        // 先按显示模式筛:只看插画时不显示没插画的人,只看小说同理
        val byMode = list.filter {
            when (stripMode) {
                UserListAdapter.StripMode.ILLUST -> it.illusts.isNotEmpty()
                UserListAdapter.StripMode.NOVEL -> it.novels.isNotEmpty()
                else -> true
            }
        }
        if (!groupEnabled || groupFilter == FollowGroupStore.ALL) return byMode.toMutableList()
        return byMode.filter {
            val g = FollowGroupStore.groupOf(it.user.id)
            if (groupFilter == FollowGroupStore.NONE) g == FollowGroupStore.NONE else g == groupFilter
        }.toMutableList()
    }

    private fun renderWorksButton() {
        _header?.buttonWorksType?.text = when (stripMode) {
            UserListAdapter.StripMode.ILLUST -> getString(R.string.works_illust)
            UserListAdapter.StripMode.NOVEL -> getString(R.string.works_novel)
            else -> getString(R.string.works_all)
        }
    }

    private var autoLoading = false
    private var autoLoadCount = 0

    private fun applyFilter() {
        userListAdapter.setList(visible(fullData))
        if (groupEnabled) renderGroupChips()
        maybeAutoLoad()
    }

    // 按模式过滤后第一屏可能很空(比如"小说"模式里只有个别人有小说),
    // 列表填不满屏幕时自动多拉一页,避免出现大片空白。
    private fun maybeAutoLoad() {
        if (autoLoading) return
        if (autoLoadCount >= 3) return
        if (userListAdapter.data.size >= 8) return
        if (viewModel.nextUrl.value.isNullOrBlank()) return
        autoLoading = true
        autoLoadCount++
        viewModel.onLoadMore()
    }

    /** 顶部分组 chip:全部 / 未分组 / 各分组(带人数) */
    private fun renderGroupChips() {
        val bar = groupBar ?: return
        bar.groupChips.removeAllViews()
        val modeFiltered = fullData.filter { matchesMode(it) }
        addGroupChip(bar, getString(R.string.user_group_all), FollowGroupStore.ALL, modeFiltered.size)
        val none = modeFiltered.count { FollowGroupStore.groupOf(it.user.id) == FollowGroupStore.NONE }
        addGroupChip(bar, getString(R.string.user_group_none), FollowGroupStore.NONE, none)
        FollowGroupStore.groups().forEach { group ->
            val count = modeFiltered.count { FollowGroupStore.groupOf(it.user.id) == group.id }
            addGroupChip(bar, group.name, group.id, count)
        }
    }

    /** 当前显示模式下该用户是否有作品可展示 */
    private fun matchesMode(bean: UserPreviewsBean): Boolean = when (stripMode) {
        UserListAdapter.StripMode.ILLUST -> bean.illusts.isNotEmpty()
        UserListAdapter.StripMode.NOVEL -> bean.novels.isNotEmpty()
        else -> true
    }

    private fun addGroupChip(
        bar: ViewUserGroupBarBinding,
        label: String,
        value: Int,
        count: Int
    ) {
        val chip = Chip(requireContext()).apply {
            text = "$label $count"
            isCheckable = true
            isChecked = groupFilter == value
            setOnClickListener {
                groupFilter = value
                applyFilter()
            }
        }
        bar.groupChips.addView(chip)
    }

    private fun showGroupManager() {
        val groups = FollowGroupStore.groups()
        val labels = groups.map {
            "${it.name} (${FollowGroupStore.countIn(it.id)})"
        } + getString(R.string.user_group_new)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.user_group_manage)
            .setItems(labels.toTypedArray()) { d, which ->
                d.dismiss()
                if (which >= groups.size) {
                    promptNewGroup()
                } else {
                    editGroup(groups[which].id, groups[which].name)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun editGroup(id: Int, name: String) {
        val actions = arrayOf(
            getString(R.string.user_group_rename),
            getString(R.string.user_group_delete)
        )
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(name)
            .setItems(actions) { d, which ->
                d.dismiss()
                if (which == 0) {
                    MaterialDialogs(requireContext()).show {
                        setTitle(R.string.user_group_rename)
                        setInput {
                            hint = getString(R.string.user_group_name_hint)
                            editText?.setText(name)
                        }
                        confirmButton { dialog, _ ->
                            val text = getInputField(dialog).text.toString()
                            if (text.isNotBlank()) FollowGroupStore.rename(id, text)
                            applyFilter()
                        }
                        cancelButton()
                    }
                } else {
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.user_group_delete)
                        .setMessage(name)
                        .setPositiveButton(R.string.ok) { _, _ ->
                            FollowGroupStore.delete(id)
                            if (groupFilter == id) groupFilter = FollowGroupStore.ALL
                            applyFilter()
                        }
                        .setNegativeButton(R.string.cancel, null)
                        .show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun promptNewGroup(onDone: ((Int) -> Unit)? = null) {
        MaterialDialogs(requireContext()).show {
            setTitle(R.string.user_group_new)
            setInput { hint = getString(R.string.user_group_name_hint) }
            confirmButton { dialog, _ ->
                val text = getInputField(dialog).text.toString().trim()
                if (text.isEmpty()) return@confirmButton
                val group = FollowGroupStore.create(text)
                applyFilter()
                onDone?.invoke(group.id)
            }
            cancelButton()
        }
    }

    // ---- 多选 ----

    private fun enterSelection(bean: UserPreviewsBean) {
        selecting = true
        selected.clear()
        selected.add(bean.user.id)
        syncSelection()
    }

    private fun startSelection() {
        selecting = true
        syncSelection()
    }

    private fun toggleSelection(bean: UserPreviewsBean) {
        if (!selected.remove(bean.user.id)) selected.add(bean.user.id)
        syncSelection()
        if (selected.isEmpty()) exitSelection()
    }

    private fun syncSelection() {
        userListAdapter.selectedIds = selected.toSet()
        userListAdapter.selectionEnabled = selecting
        activity?.invalidateOptionsMenu()
    }

    private fun exitSelection() {
        selecting = false
        selected.clear()
        syncSelection()
    }

    /** 批量移动:一人只在一个组里,所以是"移动"而不是"复制" */
    private fun showMoveDialog() {
        val groups = FollowGroupStore.groups()
        val labels = ArrayList<String>()
        labels.add(getString(R.string.user_group_none))
        groups.forEach { labels.add(it.name) }
        labels.add(getString(R.string.user_group_new))
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.user_move_to_group)
            .setItems(labels.toTypedArray()) { d, which ->
                d.dismiss()
                when {
                    which == 0 -> moveSelected(FollowGroupStore.NONE)
                    which <= groups.size -> moveSelected(groups[which - 1].id)
                    else -> promptNewGroup { id -> moveSelected(id) }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun moveSelected(groupId: Int) {
        if (selected.isEmpty()) return
        val ids = selected.toList()
        FollowGroupStore.move(ids, groupId)
        Toasty.info(requireContext(), getString(R.string.user_group_moved, ids.size))
        exitSelection()
        applyFilter()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        if (groupEnabled) inflater.inflate(R.menu.menu_user_group, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        if (groupEnabled) {
            menu.findItem(R.id.action_user_select)?.isVisible = !selecting
            menu.findItem(R.id.action_user_move)?.apply {
                isVisible = selecting
                title = if (selected.isEmpty()) getString(R.string.user_move_to_group)
                else getString(R.string.user_selected_count, selected.size)
            }
            menu.findItem(R.id.action_user_cancel_select)?.isVisible = selecting
            menu.findItem(R.id.action_user_group_manage)?.isVisible = !selecting
        }
        super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (groupEnabled) {
            when (item.itemId) {
                R.id.action_user_select -> {
                    startSelection()
                    return true
                }
                R.id.action_user_cancel_select -> {
                    exitSelection()
                    return true
                }
                R.id.action_user_move -> {
                    showMoveDialog()
                    return true
                }
                R.id.action_user_group_manage -> {
                    showGroupManager()
                    return true
                }
            }
        }
        return super.onOptionsItemSelected(item)
    }

    companion object {
        fun start(
            context: Context,
            userid: Int,
            getFollowing: Boolean? = null,
            getMyPixiv: Boolean = false
        ) {
            val title = when (getFollowing) {
                true -> R.string.followings
                false -> R.string.followers
                null -> if (getMyPixiv) R.string.goodpfriend else R.string.related
            }
            FragmentActivity.start(context, "UserList", title, Bundle().apply {
                putInt("userid", userid)
                putString(
                    "TAG", when (getFollowing) {
                        true -> "Following"
                        false -> "Follower"
                        null -> if (getMyPixiv) "MyPixiv" else "Related"
                    }
                )
            })
        }

        @JvmStatic
        fun newInstance(userid: Int, getFollowing: Boolean? = null) =
            UserListFragment().apply {
                this.userid = userid
                this.TAG = when (getFollowing) {
                    true -> "Following"
                    false -> "Follower"
                    null -> "Related"
                }
            }

        @JvmStatic
        fun searchUser(keyword: String) =
            UserListFragment().apply {
                this.TAG = "Search"
                this.keyword = keyword
            }

        @JvmStatic
        fun recommendUser() =
            UserListFragment().apply {
                this.TAG = "Recommend"
            }
    }
}
