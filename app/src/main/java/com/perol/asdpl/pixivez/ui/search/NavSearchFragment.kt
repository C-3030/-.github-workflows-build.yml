package com.perol.asdpl.pixivez.ui.search

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.perol.asdpl.pixivez.R

/** 底部导航里的搜索页：输入关键词后进入搜索 */
class NavSearchFragment : Fragment() {
    companion object {
        @JvmStatic
        fun newInstance(): NavSearchFragment = NavSearchFragment()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_nav_search, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val input = view.findViewById<TextInputEditText>(R.id.navSearchInput)
        val button = view.findViewById<MaterialButton>(R.id.navSearchButton)
        fun doSearch() {
            val keyword = input.text?.toString()?.trim().orEmpty()
            if (keyword.isEmpty()) return
            SearchActivity.start(requireContext(), keyword)
        }
        button.setOnClickListener { doSearch() }
        view.findViewById<MaterialButton>(R.id.navSearchFilter)?.setOnClickListener {
            SearchFilterDialog.show(childFragmentManager)
        }
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) { doSearch(); true } else false
        }
    }
}
