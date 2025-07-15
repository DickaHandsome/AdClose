package com.close.hook.ads.ui.fragment.block

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.drawable.AnimatedVectorDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.view.ActionMode
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.selection.ItemDetailsLookup
import androidx.recyclerview.selection.ItemKeyProvider
import androidx.recyclerview.selection.Selection
import androidx.recyclerview.selection.SelectionPredicates
import androidx.recyclerview.selection.SelectionTracker
import androidx.recyclerview.selection.StorageStrategy
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.close.hook.ads.R
import com.close.hook.ads.data.model.Url
import com.close.hook.ads.databinding.FragmentBlockListBinding
import com.close.hook.ads.databinding.ItemBlockListAddBinding
import com.close.hook.ads.ui.activity.MainActivity
import com.close.hook.ads.ui.adapter.BlockListAdapter
import com.close.hook.ads.ui.fragment.base.BaseFragment
import com.close.hook.ads.ui.viewmodel.BlockListViewModel
import com.close.hook.ads.util.INavContainer
import com.close.hook.ads.util.OnBackPressContainer
import com.close.hook.ads.util.OnBackPressListener
import com.close.hook.ads.util.dp
import com.close.hook.ads.util.FooterSpaceItemDecoration
import com.google.android.material.behavior.HideBottomViewOnScrollBehavior
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collectLatest
import me.zhanghai.android.fastscroll.FastScrollerBuilder

class BlockListFragment : BaseFragment<FragmentBlockListBinding>(), OnBackPressListener {

    private val viewModel by viewModels<BlockListViewModel>()
    private lateinit var mAdapter: BlockListAdapter
    private lateinit var footerSpaceDecoration: FooterSpaceItemDecoration
    private var tracker: SelectionTracker<Url>? = null
    private var selectedItems: Selection<Url>? = null
    private var mActionMode: ActionMode? = null
    private val imm: InputMethodManager by lazy {
        requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initView()
        initEditText()
        initButton()
        initFab()
        initObserve()
        setUpTracker()
        addObserverToTracker()
    }
    
    private fun initView() {
        mAdapter = BlockListAdapter(requireContext(), viewModel::removeUrl, this::onEditUrl)
        footerSpaceDecoration = FooterSpaceItemDecoration(footerHeight = 96.dp)

        binding.recyclerView.apply {
            adapter = mAdapter
            layoutManager = LinearLayoutManager(requireContext())
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                private var totalDy = 0
                private val scrollThreshold = 20

                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    totalDy += dy
                    val navContainer = activity as? INavContainer
                    if (totalDy > scrollThreshold) {
                        navContainer?.hideNavigation()
                        totalDy = 0
                    } else if (totalDy < -scrollThreshold) {
                        navContainer?.showNavigation()
                        totalDy = 0
                    }
                }
            })

            addItemDecoration(footerSpaceDecoration)
            FastScrollerBuilder(this).useMd2Style().build()
        }
    }

    private fun initObserve() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.blackList.collectLatest { blackList ->
                mAdapter.submitList(blackList) {
                    binding.progressBar.visibility = View.GONE
                    updateViewFlipper(blackList.size)
                }
            }
        }
    }

    private fun updateViewFlipper(blackListSize: Int) {
        val targetChild = if (blackListSize == 0) 0 else 1
        if (binding.vfContainer.displayedChild != targetChild) {
            binding.vfContainer.displayedChild = targetChild
        }
    }

    private fun getFabLayoutParams(): CoordinatorLayout.LayoutParams {
        return CoordinatorLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            behavior = HideBottomViewOnScrollBehavior<FloatingActionButton>()
        }
    }

    private fun initFab() {
        binding.delete.apply {
            layoutParams = getFabLayoutParams()
            visibility = View.VISIBLE
            setOnClickListener { clearBlockList() }
        }

        binding.add.apply {
            layoutParams = getFabLayoutParams()
            visibility = View.VISIBLE
            setOnClickListener { addRule() }
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val navigationBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            binding.delete.updateLayoutParams<CoordinatorLayout.LayoutParams> {
                rightMargin = 25.dp
                bottomMargin = navigationBars.bottom + 105.dp
            }
            binding.add.updateLayoutParams<CoordinatorLayout.LayoutParams> {
                rightMargin = 25.dp
                bottomMargin = navigationBars.bottom + 186.dp
            }
            insets
        }
    }

    private fun addObserverToTracker() {
        tracker?.addObserver(object : SelectionTracker.SelectionObserver<Url>() {
            override fun onSelectionChanged() {
                super.onSelectionChanged()
                selectedItems = tracker?.selection
                handleActionMode()
            }
        })
    }

    private fun handleActionMode() {
        val size = selectedItems?.size() ?: 0
        if (size > 0) {
            if (mActionMode == null) {
                mActionMode = (activity as? MainActivity)?.startSupportActionMode(mActionModeCallback)
            }
            mActionMode?.title = "Selected $size"
        } else {
            mActionMode?.finish()
            mActionMode = null
        }
    }

    private val mActionModeCallback = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            mode?.menuInflater?.inflate(R.menu.menu_hosts, menu)
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean = false

        override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
            return when (item?.itemId) {
                R.id.clear -> {
                    onRemove()
                    true
                }
                R.id.action_copy -> {
                    onCopy()
                    true
                }
                else -> false
            }
        }

        override fun onDestroyActionMode(mode: ActionMode?) {
            mActionMode = null
            tracker?.clearSelection()
        }
    }

    private fun onRemove() {
        selectedItems?.let {
            if (it.size() != 0) {
                viewModel.removeList(it.toList())
                Toast.makeText(requireContext(), getString(R.string.batch_remove_success), Toast.LENGTH_SHORT).show()
                tracker?.clearSelection()
                (activity as? MainActivity)?.showNavigation()
            }
        }
    }

    private fun onCopy() {
        selectedItems?.let { selection ->
            val uniqueTypeUrls = selection
                .map { "${it.type}, ${it.url}" }
                .distinct()
                .joinToString(separator = "\n")

            if (uniqueTypeUrls.isNotEmpty()) {
                val clipboard =
                    requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("copied_type_urls", uniqueTypeUrls)
                clipboard.setPrimaryClip(clip)

                Toast.makeText(requireContext(), getString(R.string.batch_copy_to_clipboard), Toast.LENGTH_SHORT).show()
            }

            tracker?.clearSelection()
        }
    }

    private fun setUpTracker() {
        tracker = SelectionTracker.Builder(
            "selection_id",
            binding.recyclerView,
            CategoryItemKeyProvider(mAdapter),
            CategoryItemDetailsLookup(binding.recyclerView),
            StorageStrategy.createParcelableStorage(Url::class.java)
        ).withSelectionPredicate(
            SelectionPredicates.createSelectAnything()
        ).build()
        mAdapter.tracker = tracker
    }

    private val textWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
            viewModel.setBlackListSearchQuery(s.toString())
        }

        override fun afterTextChanged(s: Editable) {
            binding.clear.isVisible = s.isNotBlank()
        }
    }

    private fun initEditText() {
        binding.editText.onFocusChangeListener =
            View.OnFocusChangeListener { _, hasFocus ->
                setIconAndFocus(
                    if (hasFocus) R.drawable.ic_magnifier_to_back else R.drawable.ic_back_to_magnifier,
                    hasFocus
                )
            }
        binding.editText.addTextChangedListener(textWatcher)
    }

    private fun setIconAndFocus(drawableId: Int, focus: Boolean) {
        binding.searchIcon.setImageDrawable(ContextCompat.getDrawable(requireContext(), drawableId))
        (binding.searchIcon.drawable as? AnimatedVectorDrawable)?.start()
        if (focus) {
            binding.editText.requestFocus()
            imm.showSoftInput(binding.editText, 0)
        } else {
            binding.editText.clearFocus()
            imm.hideSoftInputFromWindow(binding.editText.windowToken, 0)
        }
    }

    private fun initButton() {
        binding.apply {
            searchIcon.setOnClickListener {
                if (editText.isFocused) {
                    editText.setText("")
                    setIconAndFocus(R.drawable.ic_back_to_magnifier, false)
                } else {
                    setIconAndFocus(R.drawable.ic_magnifier_to_back, true)
                }
            }

            export.setOnClickListener {
                backupSAFLauncher.launch("block_list.rule")
            }

            restore.setOnClickListener {
                restoreSAFLauncher.launch(arrayOf("application/octet-stream"))
            }

            clear.setOnClickListener {
                editText.text = null
            }
        }
    }

    private fun showRuleDialog(url: Url? = null) {
        val dialogBinding = ItemBlockListAddBinding.inflate(LayoutInflater.from(requireContext()))

        dialogBinding.editText.setText(url?.url ?: "")
        dialogBinding.type.setText(url?.type ?: "URL")

        dialogBinding.type.setAdapter(
            ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                arrayOf("Domain", "URL", "KeyWord")
            )
        )

        val title = if (url == null) getString(R.string.add_rule) else getString(R.string.edit_rule)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setView(dialogBinding.root)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val newType = dialogBinding.type.text.toString()
                val newUrl = dialogBinding.editText.text.toString().trim()

                if (newUrl.isEmpty()) {
                    Toast.makeText(requireContext(), getString(R.string.value_empty_error), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val newItem = Url(type = newType, url = newUrl).also { it.id = url?.id ?: 0L }

                lifecycleScope.launch {
                    val isExist = viewModel.dataSource.isExist(newType, newUrl)
                    if (url == null) {
                        if (!isExist) {
                            viewModel.addUrl(newItem)
                        } else {
                            Toast.makeText(requireContext(), getString(R.string.rule_exists), Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        viewModel.updateUrl(newItem)
                    }
                }
            }
            .create().apply {
                window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
                dialogBinding.editText.requestFocus()
            }.show()
    }

    private fun addRule() = showRuleDialog()

    private fun onEditUrl(url: Url) = showRuleDialog(url)

    private fun clearBlockList() {
        MaterialAlertDialogBuilder(requireContext()).setTitle(getString(R.string.clear_block_list_confirm))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                viewModel.removeAll()
            }
            .show()
    }

    private fun prepareDataForExport(): List<String> {
        return viewModel.blackList.value
            .map { "${it.type}, ${it.url}" }
            .distinct()
            .filter { it.contains(",") }
            .sorted()
    }

    private val restoreSAFLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            uri?.let { uri ->
                lifecycleScope.launch(Dispatchers.IO) {
                    runCatching {
                        val currentList: List<Url> = viewModel.blackList.value
                        val contentResolver = requireContext().contentResolver

                        val fileName = contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (cursor.moveToFirst() && nameIndex >= 0) cursor.getString(nameIndex) else null
                        }
                        if (fileName == null || !fileName.matches(Regex(".*\\.rule(\\s*\\(\\d+\\))?"))) {
                            throw IllegalArgumentException(getString(R.string.invalid_file_format))
                        }

                        val inputStream = contentResolver.openInputStream(uri)
                        val newList: List<Url> = inputStream?.bufferedReader()?.useLines { lines ->
                            lines.mapNotNull { line ->
                                val parts: List<String> = line.split(",\\s*".toRegex()).map { it.trim() }
                                if (parts.size == 2 && (
                                            parts[0].equals("Domain", ignoreCase = true) ||
                                            parts[0].equals("URL", ignoreCase = true) ||
                                            parts[0].equals("KeyWord", ignoreCase = true)
                                            )) {
                                    Url(parts[0], parts[1])
                                } else null
                            }.toList()
                        } ?: listOf()

                        val updateList = if (currentList.isEmpty()) newList else newList.filter { it !in currentList }

                        if (updateList.isNotEmpty()) {
                            viewModel.addListUrl(updateList)
                        }

                        withContext(Dispatchers.Main) {
                            Toast.makeText(requireContext(), getString(R.string.import_success), Toast.LENGTH_SHORT).show()
                        }
                    }.onFailure {
                        withContext(Dispatchers.Main) {
                            MaterialAlertDialogBuilder(requireContext())
                                .setTitle(getString(R.string.import_failed))
                                .setMessage(it.message)
                                .setPositiveButton(android.R.string.ok, null)
                                .setNegativeButton(getString(R.string.crash_log)) { _, _ ->
                                    MaterialAlertDialogBuilder(requireContext())
                                        .setTitle(getString(R.string.crash_log))
                                        .setMessage(it.stackTraceToString())
                                        .setPositiveButton(android.R.string.ok, null)
                                        .show()
                                }
                                .show()
                        }
                    }
                }
            }
        }

    private val backupSAFLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument()) { uri: Uri? ->
            uri?.let {
                lifecycleScope.launch(Dispatchers.IO) {
                    runCatching {
                        requireContext().contentResolver.openOutputStream(uri)?.bufferedWriter().use { writer ->
                            prepareDataForExport().forEach { line ->
                                writer?.write("$line\n")
                            }
                        }
                        withContext(Dispatchers.Main) {
                            Toast.makeText(requireContext(), getString(R.string.export_success), Toast.LENGTH_SHORT).show()
                        }
                    }.onFailure {
                        withContext(Dispatchers.Main) {
                            MaterialAlertDialogBuilder(requireContext())
                                .setTitle(getString(R.string.export_failed))
                                .setMessage(it.message)
                                .setPositiveButton(android.R.string.ok, null)
                                .setNegativeButton(getString(R.string.crash_log)) { _, _ ->
                                    MaterialAlertDialogBuilder(requireContext())
                                        .setTitle(getString(R.string.crash_log))
                                        .setMessage(it.stackTraceToString())
                                        .setPositiveButton(android.R.string.ok, null)
                                        .show()
                                }
                                .show()
                        }
                    }
                }
            }
        }

    class CategoryItemDetailsLookup(private val recyclerView: RecyclerView) :
        ItemDetailsLookup<Url>() {
        override fun getItemDetails(e: MotionEvent): ItemDetails<Url>? {
            val view = recyclerView.findChildViewUnder(e.x, e.y)
            if (view != null) {
                return (recyclerView.getChildViewHolder(view) as? BlockListAdapter.ViewHolder)?.getItemDetails()
            }
            return null
        }
    }

    class CategoryItemKeyProvider(private val adapter: BlockListAdapter) :
        ItemKeyProvider<Url>(SCOPE_CACHED) {
        override fun getKey(position: Int): Url? {
            return adapter.currentList.getOrNull(position)
        }

        override fun getPosition(key: Url): Int {
            val index = adapter.currentList.indexOfFirst { it == key }
            return if (index >= 0) index else RecyclerView.NO_POSITION
        }
    }

    override fun onBackPressed(): Boolean {
        return if (binding.editText.isFocused) {
            binding.editText.setText("")
            setIconAndFocus(R.drawable.ic_back_to_magnifier, false)
            true
        } else if (selectedItems?.isEmpty == false) {
            tracker?.clearSelection()
            true
        } else binding.recyclerView.closeMenus()
    }

    override fun onPause() {
        super.onPause()
        (activity as? OnBackPressContainer)?.backController = null
        tracker?.clearSelection()
    }

    override fun onResume() {
        super.onResume()
        (activity as? OnBackPressContainer)?.backController = this
    }

    override fun onDestroyView() {
        binding.editText.removeTextChangedListener(textWatcher)
        super.onDestroyView()
    }
}
