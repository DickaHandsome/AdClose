package com.close.hook.ads.ui.fragment.app

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.close.hook.ads.R
import com.close.hook.ads.data.model.AppInfo
import com.close.hook.ads.data.model.ConfiguredBean
import com.close.hook.ads.databinding.BottomDialogAppInfoBinding
import com.close.hook.ads.databinding.BottomDialogSwitchesBinding
import com.close.hook.ads.databinding.FragmentAppsBinding
import com.close.hook.ads.preference.HookPrefs
import com.close.hook.ads.ui.activity.CustomHookActivity
import com.close.hook.ads.ui.activity.MainActivity
import com.close.hook.ads.ui.adapter.AppsAdapter
import com.close.hook.ads.ui.fragment.base.BaseFragment
import com.close.hook.ads.ui.viewmodel.AppsViewModel
import com.close.hook.ads.util.CacheDataManager.getFormatSize
import com.close.hook.ads.util.INavContainer
import com.close.hook.ads.util.IOnFabClickContainer
import com.close.hook.ads.util.IOnFabClickListener
import com.close.hook.ads.util.IOnTabClickContainer
import com.close.hook.ads.util.IOnTabClickListener
import com.close.hook.ads.util.LinearItemDecoration
import com.close.hook.ads.util.OnCLearCLickContainer
import com.close.hook.ads.util.OnClearClickListener
import com.close.hook.ads.util.dp
import com.close.hook.ads.util.FooterSpaceItemDecoration
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.zhanghai.android.fastscroll.FastScrollerBuilder
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AppsFragment : BaseFragment<FragmentAppsBinding>(), AppsAdapter.OnItemClickListener,
    IOnTabClickListener, OnClearClickListener, IOnFabClickListener {

    private val viewModel by viewModels<AppsViewModel>(ownerProducer = {
        if (arguments?.getString("type") == "configured") requireActivity() else this
    })

    private var fragmentType: String = "user"

    private lateinit var mAdapter: AppsAdapter
    private lateinit var footerSpaceDecoration: FooterSpaceItemDecoration
    private var appConfigDialog: BottomSheetDialog? = null
    private var appInfoDialog: BottomSheetDialog? = null
    private lateinit var configBinding: BottomDialogSwitchesBinding
    private lateinit var infoBinding: BottomDialogAppInfoBinding

    private val childrenCheckBoxes by lazy {
        listOf(
            configBinding.switchOne,
            configBinding.switchTwo,
            configBinding.switchThree,
            configBinding.switchFour,
            configBinding.switchFive,
            configBinding.switchSix,
            configBinding.switchSeven,
            configBinding.switchEight
        )
    }
    private val prefKeys = listOf(
        "switch_one_",
        "switch_two_",
        "switch_three_",
        "switch_four_",
        "switch_five_",
        "switch_six_",
        "switch_seven_",
        "switch_eight_"
    )

    private val prefsHelper by lazy {
        HookPrefs.getInstance(requireContext())
    }

    private var currentAppPackageName: String? = null

    companion object {
        @JvmStatic
        fun newInstance(type: String) =
            AppsFragment().apply {
                arguments = Bundle().apply {
                    putString("type", type)
                }
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fragmentType = arguments?.getString("type") ?: "user"
        viewModel.setAppType(fragmentType)

        if (fragmentType == "configured") {
            (parentFragment as? IOnFabClickContainer)?.fabController = this@AppsFragment
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initSheet()
        initView()
        initRefresh()
        initObserve()
    }

    private fun initSheet() {
        appConfigDialog = BottomSheetDialog(requireContext())
        configBinding = BottomDialogSwitchesBinding.inflate(layoutInflater, null, false)
        appConfigDialog?.setContentView(configBinding.root)
        initAppConfig()

        appInfoDialog = BottomSheetDialog(requireContext())
        infoBinding = BottomDialogAppInfoBinding.inflate(layoutInflater, null, false)
        appInfoDialog?.setContentView(infoBinding.root)
        initAppInfo()
    }

    private fun initAppInfo() {
        infoBinding.apply {
            close.setOnClickListener {
                appInfoDialog?.dismiss()
            }
            detail.setOnClickListener {
                packageName.value.text?.toString()?.let { pkgName ->
                    openAppDetails(pkgName)
                }
                appInfoDialog?.dismiss()
            }
            launch.setOnClickListener {
                packageName.value.text?.toString()?.let { pkgName ->
                    launchApp(pkgName)
                }
                appInfoDialog?.dismiss()
            }
        }
    }

    private fun initAppConfig() {
        configBinding.apply {
            buttonClose.setOnClickListener {
                appConfigDialog?.dismiss()
            }

            buttonCustomHook.setOnClickListener {
                currentAppPackageName?.let { pkgName ->
                    val intent = Intent(requireContext(), CustomHookActivity::class.java).apply {
                        putExtra("packageName", pkgName)
                    }
                    startActivity(intent)
                } ?: run {
                    Toast.makeText(requireContext(), "无法获取包名，请重试", Toast.LENGTH_SHORT).show()
                }
                appConfigDialog?.dismiss()
            }

            var isUpdatingChildren = false

            fun updateChildrenCheckBoxes(isChecked: Boolean) {
                isUpdatingChildren = true
                childrenCheckBoxes.forEach { childCheckBox ->
                    childCheckBox.isChecked = isChecked
                }
                isUpdatingChildren = false
            }

            val onParentCheckedChange: (MaterialCheckBox, Int) -> Unit = { _, state ->
                if (state != MaterialCheckBox.STATE_INDETERMINATE) {
                    updateChildrenCheckBoxes(selectAll.isChecked)
                }
            }

            selectAll.addOnCheckedStateChangedListener(onParentCheckedChange)

            fun updateParentCheckBoxState() {
                val checkedCount = childrenCheckBoxes.count { it.isChecked }
                val allChecked = checkedCount == childrenCheckBoxes.size
                val noneChecked = checkedCount == 0

                selectAll.removeOnCheckedStateChangedListener(onParentCheckedChange)
                selectAll.checkedState = when {
                    allChecked -> MaterialCheckBox.STATE_CHECKED
                    noneChecked -> MaterialCheckBox.STATE_UNCHECKED
                    else -> MaterialCheckBox.STATE_INDETERMINATE
                }
                selectAll.addOnCheckedStateChangedListener(onParentCheckedChange)
            }

            childrenCheckBoxes.forEach { childCheckBox ->
                childCheckBox.addOnCheckedStateChangedListener { _, _ ->
                    if (!isUpdatingChildren) {
                        updateParentCheckBoxState()
                    }
                }
            }
        }
    }

    private fun openAppDetails(packageName: String) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
            addCategory(Intent.CATEGORY_DEFAULT)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(requireContext(), getString(R.string.open_app_details_failed), Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchApp(packageName: String) {
        val intent = requireContext().packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            try {
                startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(requireContext(), getString(R.string.launch_app_failed), Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(requireContext(), getString(R.string.launch_app_failed), Toast.LENGTH_SHORT).show()
        }
    }

    private fun initRefresh() {
        binding.swipeRefresh.apply {
            setColorSchemeColors(
                MaterialColors.getColor(
                    requireContext(),
                    com.google.android.material.R.attr.colorPrimary,
                    -1
                )
            )
            setOnRefreshListener {
                viewModel.refreshApps()
                (activity as? INavContainer)?.showNavigation()
            }
        }
    }

    private fun initView() {
        mAdapter = AppsAdapter(this)
        footerSpaceDecoration = FooterSpaceItemDecoration(footerHeight = 96.dp)

        binding.recyclerView.apply {
            setHasFixedSize(true)
            setItemViewCacheSize(30)
            adapter = mAdapter
            layoutManager = LinearLayoutManager(requireContext())

            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                private var totalDy = 0
                private val scrollThreshold = 20.dp

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
            addItemDecoration(LinearItemDecoration(4.dp))
            FastScrollerBuilder(this).useMd2Style().build()
        }
    }

    private fun initObserve() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                val list = state.apps
                val isLoading = state.isLoading

                binding.apply {
                    vfContainer.displayedChild = if (list.isEmpty()) 0 else 1
                    updateSearchHint(list.size)
                    mAdapter.submitList(list)
                    progressBar.isVisible = isLoading && list.isEmpty()
                    swipeRefresh.isRefreshing = isLoading && list.isNotEmpty()
                }
            }
        }
    }

    private fun updateSearchHint(size: Int) {
        if (this.isResumed)
            (parentFragment as? AppsPagerFragment)?.setHint(size)
    }

    @SuppressLint("SetText18n")
    override fun onItemClick(appInfo: AppInfo, icon: Drawable?) {
        if (!MainActivity.isModuleActivated()) {
            Toast.makeText(requireContext(), getString(R.string.module_not_activated), Toast.LENGTH_SHORT).show()
            return
        }

        currentAppPackageName = appInfo.packageName

        configBinding.apply {
            sheetAppName.text = appInfo.appName
            version.text = appInfo.versionName
            this.icon.setImageDrawable(icon)

            childrenCheckBoxes.forEachIndexed { index, checkBox ->
                val key = prefKeys[index] + appInfo.packageName
                checkBox.isChecked = prefsHelper.getBoolean(key, false)
            }

            buttonUpdate.setOnClickListener {
                childrenCheckBoxes.forEachIndexed { index, checkBox ->
                    val key = prefKeys[index] + appInfo.packageName
                    prefsHelper.setBoolean(key, checkBox.isChecked)
                }
                Toast.makeText(requireContext(), getString(R.string.save_success), Toast.LENGTH_SHORT).show()
                appConfigDialog?.dismiss()
            }
        }
        appConfigDialog?.show()
    }

    @SuppressLint("SetText18n")
    override fun onItemLongClick(appInfo: AppInfo, icon: Drawable?) {
        infoBinding.apply {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

            this.icon.setImageDrawable(icon)

            appName.text = appInfo.appName
            packageName.apply {
                title.text = getString(R.string.apk_package_name)
                value.text = appInfo.packageName
            }
            appSize.apply {
                title.text = getString(R.string.apk_size)
                value.text = getFormatSize(appInfo.size)
            }
            versionName.apply {
                title.text = getString(R.string.version_name)
                value.text = appInfo.versionName
            }
            versionCode.apply {
                title.text = getString(R.string.version_code)
                value.text = appInfo.versionCode.toString()
            }
            targetSdk.apply {
                title.text = getString(R.string.target_sdk)
                value.text = appInfo.targetSdk.toString()
            }
            minSdk.apply {
                title.text = getString(R.string.min_sdk)
                value.text = appInfo.minSdk.toString()
            }
            installTime.apply {
                title.text = getString(R.string.install_time)
                value.text = dateFormat.format(Date(appInfo.firstInstallTime))
            }
            updateTime.apply {
                title.text = getString(R.string.update_time)
                value.text = dateFormat.format(Date(appInfo.lastUpdateTime))
            }
        }
        appInfoDialog?.show()
    }

    override fun onReturnTop() {
        binding.recyclerView.scrollToPosition(0)
        (activity as? MainActivity)?.showNavigation()
    }

    override fun onResume() {
        super.onResume()
        (parentFragment as? IOnTabClickContainer)?.tabController = this
        (parentFragment as? OnCLearCLickContainer)?.controller = this
        updateSearchHint(viewModel.uiState.value.apps.size)
    }

    override fun onPause() {
        super.onPause()
        (parentFragment as? IOnTabClickContainer)?.tabController = null
        (parentFragment as? OnCLearCLickContainer)?.controller = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        appConfigDialog?.dismiss()
        appInfoDialog?.dismiss()
        appConfigDialog = null
        appInfoDialog = null
        currentAppPackageName = null
    }

    override fun updateSortList(
        filterOrder: Int,
        keyWord: String,
        isReverse: Boolean,
        showConfigured: Boolean,
        showUpdated: Boolean,
        showDisabled: Boolean
    ) {
        viewModel.updateFilterAndSort(
            filterOrder,
            keyWord,
            isReverse,
            showConfigured,
            showUpdated,
            showDisabled
        )
    }

    override fun onExport() {
        lifecycleScope.launch(Dispatchers.IO) {
            val allPrefs = prefsHelper.getAll()

            val configuredList = viewModel.uiState.value.apps.filter { it.isEnable == 1 }.map { appInfo ->
                ConfiguredBean(
                    appInfo.packageName,
                    prefKeys.map { keyPrefix ->
                        (allPrefs[keyPrefix + appInfo.packageName] as? Boolean) ?: false
                    })
            }

            if (configuredList.isEmpty()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), getString(R.string.export_failed), Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            try {
                val content = GsonBuilder().setPrettyPrinting().create().toJson(configuredList)
                val tempFileSaved = saveTempFileForExport(content)
                if (tempFileSaved) {
                    withContext(Dispatchers.Main) {
                        backupSAFLauncher.launch("configured_list.json")
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), getString(R.string.export_failed), Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        requireContext(),
                        "${getString(R.string.export_failed)}: ${e.message ?: "Unknown error"}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun saveTempFileForExport(content: String): Boolean {
        return try {
            val dir = File(requireContext().cacheDir, "export_temp")
            if (!dir.exists()) {
                dir.mkdirs()
            }

            val file = File(dir, "configured_list.json")
            file.writeText(content)
            true
        } catch (e: IOException) {
            false
        }
    }

    private val backupSAFLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri == null) {
                return@registerForActivityResult
            }
            lifecycleScope.launch(Dispatchers.IO) {
                val sourceFile = File(requireContext().cacheDir, "export_temp/configured_list.json")

                if (!sourceFile.exists()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), getString(R.string.export_failed), Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                try {
                    sourceFile.inputStream().use { input ->
                        requireContext().contentResolver.openOutputStream(uri).use { output ->
                            if (output == null) {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(requireContext(), getString(R.string.export_failed), Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                input.copyTo(output)
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(requireContext(), getString(R.string.export_success), Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                } catch (e: IOException) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "${getString(R.string.export_failed)}: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                } finally {
                    if (sourceFile.exists()) {
                        sourceFile.delete()
                    }
                }
            }
        }

    override fun onRestore() {
        restoreSAFLauncher.launch(arrayOf("application/json"))
    }

    private val restoreSAFLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            uri?.let {
                lifecycleScope.launch(Dispatchers.IO) {
                    runCatching {
                        val string = requireContext().contentResolver
                            .openInputStream(uri)?.reader().use { it?.readText() }
                            ?: throw IOException("Backup file was damaged or empty.")
                        val dataList: List<ConfiguredBean> = Gson().fromJson(
                            string,
                            Array<ConfiguredBean>::class.java
                        ).toList()

                        dataList.forEach { bean ->
                            bean.switchStates.forEachIndexed { index, state ->
                                prefsHelper.setBoolean(prefKeys[index] + bean.packageName, state)
                            }
                        }
                        withContext(Dispatchers.Main) {
                            Toast.makeText(requireContext(), getString(R.string.import_success), Toast.LENGTH_SHORT).show()
                            if (fragmentType == "configured") {
                                viewModel.refreshApps()
                            }
                        }
                    }.onFailure { e ->
                        withContext(Dispatchers.Main) {
                            MaterialAlertDialogBuilder(requireContext())
                                .setTitle(getString(R.string.import_failed))
                                .setMessage(e.message ?: "Unknown error")
                                .setPositiveButton(android.R.string.ok, null)
                                .setNegativeButton(getString(R.string.crash_log)) { _, _ ->
                                    MaterialAlertDialogBuilder(requireContext())
                                        .setTitle(getString(R.string.crash_log))
                                        .setMessage(e.stackTraceToString())
                                        .setPositiveButton(android.R.string.ok, null)
                                        .show()
                                }
                                .show()
                        }
                    }
                }
            }
        }
}
