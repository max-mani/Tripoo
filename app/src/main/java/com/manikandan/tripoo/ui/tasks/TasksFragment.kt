package com.manikandan.tripoo.ui.tasks

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.google.android.gms.ads.AdRequest
import com.google.firebase.auth.FirebaseAuth
import com.manikandan.tripoo.R
import com.manikandan.tripoo.ads.TripExitInterstitialHelper
import com.manikandan.tripoo.utils.ImageUtils
import com.manikandan.tripoo.utils.UserAvatarIdentity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.manikandan.tripoo.data.model.Task
import com.manikandan.tripoo.data.model.TripMember
import com.manikandan.tripoo.databinding.FragmentTasksBinding
import com.manikandan.tripoo.viewmodel.TasksViewModel

class TasksFragment : Fragment() {

    private var _binding: FragmentTasksBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TasksViewModel by lazy {
        val tripId = arguments?.getString("tripId") ?: ""
        ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return TasksViewModel(SavedStateHandle(mapOf("tripId" to tripId))) as T
            }
        })[TasksViewModel::class.java]
    }

    private lateinit var taskAdapter: TaskAdapter
    private var currentTab = 0
    private var membersById = emptyMap<String, TripMember>()
    private var searchQuery = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTasksBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupTabs()
        setupSearch()
        setupSwipeRefresh()
        setupBottomNav()
        setupFab()
        observeViewModel()

        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    navigateToTripDashboard()
                }
            }
        )
        binding.adViewTripGroup.loadAd(AdRequest.Builder().build())
    }

    override fun onPause() {
        binding.adViewTripGroup.pause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        binding.adViewTripGroup.resume()
        val tid = arguments?.getString("tripId").orEmpty()
        if (tid.isNotEmpty()) {
            TripExitInterstitialHelper.preload(requireContext())
        }
    }

    private fun navigateToTripDashboard() {
        val nav = findNavController()
        val tripId = arguments?.getString("tripId").orEmpty().takeIf { it.isNotEmpty() }
        TripExitInterstitialHelper.navigateToTripDashboard(requireActivity(), tripId) {
            try {
                if (!nav.popBackStack(R.id.tripDashboardFragment, false)) {
                    nav.navigate(R.id.tripDashboardFragment)
                }
            } catch (_: Exception) {
                nav.navigate(R.id.tripDashboardFragment)
            }
        }
    }

    // ── RecyclerView ─────────────────────────────────────────────────────────

    private fun setupRecyclerView() {
        taskAdapter = createAdapter()
        binding.rvTasks.layoutManager = LinearLayoutManager(requireContext())
        binding.rvTasks.adapter = taskAdapter
        binding.rvTasks.itemAnimator = null
    }

    private fun computeCanManageTripAsLeader(): Boolean {
        val trip = viewModel.trip.value
        val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        if (uid.isEmpty()) return false
        return trip?.adminId == uid || membersById[uid]?.isAdmin == true
    }

    private fun createAdapter() = TaskAdapter(
        membersById = membersById,
        currentUserId = FirebaseAuth.getInstance().currentUser?.uid.orEmpty(),
        canManageTripAsLeader = computeCanManageTripAsLeader(),
        onToggle = { task -> viewModel.toggleTask(task) },
        onEdit = { task -> onEditTask(task) },
        onDelete = { task -> onDeleteTask(task) }
    )

    // ── Tabs ─────────────────────────────────────────────────────────────────

    private fun setupTabs() {
        binding.taskTabAll.setOnClickListener { selectTab(0) }
        binding.taskTabProgress.setOnClickListener { selectTab(1) }
        binding.taskTabDone.setOnClickListener { selectTab(2) }
        selectTab(0)
    }

    private fun selectTab(index: Int) {
        currentTab = index
        listOf(binding.taskTabAll, binding.taskTabProgress, binding.taskTabDone)
            .forEachIndexed { i, tv ->
                tv.setTextColor(
                    if (i == index) Color.parseColor("#F48C25") else Color.parseColor("#9CA3AF")
                )
            }
        listOf(binding.indTaskAll, binding.indTaskProgress, binding.indTaskDone)
            .forEachIndexed { i, v ->
                v.visibility = if (i == index) View.VISIBLE else View.INVISIBLE
            }
        updateList()
    }

    // ── Search ────────────────────────────────────────────────────────────────

    private fun setupSearch() {
        binding.btnSearch.setOnClickListener {
            val isVisible = binding.etSearchTasks.visibility == View.VISIBLE
            binding.etSearchTasks.visibility = if (isVisible) View.GONE else View.VISIBLE
            if (isVisible) {
                // Collapsing: clear query and refresh list
                searchQuery = ""
                binding.etSearchTasks.setText("")
                updateList()
            } else {
                binding.etSearchTasks.requestFocus()
            }
        }

        binding.etSearchTasks.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                searchQuery = s?.toString() ?: ""
                updateList()
            }
        })
    }

    // ── Swipe refresh ─────────────────────────────────────────────────────────

    private fun setupSwipeRefresh() {
        binding.swipeRefreshTasks.setColorSchemeColors(
            ContextCompat.getColor(requireContext(), R.color.tripoo_orange)
        )
        binding.swipeRefreshTasks.setOnRefreshListener {
            viewModel.refresh()
            binding.swipeRefreshTasks.postDelayed(
                { binding.swipeRefreshTasks.isRefreshing = false },
                800
            )
        }
    }

    // ── List update ───────────────────────────────────────────────────────────

    private fun updateList() {
        val baseTasks = when (currentTab) {
            1 -> viewModel.inProgressTasks.value ?: emptyList()
            2 -> viewModel.completedTasks.value ?: emptyList()
            else -> viewModel.rawTasks.value ?: emptyList()
        }
        val filtered = if (searchQuery.isBlank()) baseTasks
        else baseTasks.filter {
            (it.title as? String ?: "").contains(searchQuery, ignoreCase = true)
        }
        val items = viewModel.buildItems(filtered)
        taskAdapter.submitList(items)
        binding.emptyTasks.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }

    // ── ViewModel observers ───────────────────────────────────────────────────

    private fun observeViewModel() {
        viewModel.trip.observe(viewLifecycleOwner) { trip ->
            binding.tvTasksTripName.text = trip?.name ?: ""
            taskAdapter = createAdapter()
            binding.rvTasks.adapter = taskAdapter
            updateList()
        }

        viewModel.members.observe(viewLifecycleOwner) { members ->
            membersById = members.associateBy { it.userId }
            taskAdapter = createAdapter()
            binding.rvTasks.adapter = taskAdapter
            buildAvatarStack(members)
            updateList()
        }

        viewModel.progressCompleted.observe(viewLifecycleOwner) { done ->
            refreshProgress(done, viewModel.progressTotal.value ?: 0)
        }
        viewModel.progressTotal.observe(viewLifecycleOwner) { total ->
            refreshProgress(viewModel.progressCompleted.value ?: 0, total)
        }

        // React to live task data changes
        viewModel.rawTasks.observe(viewLifecycleOwner) { updateList() }
        viewModel.inProgressTasks.observe(viewLifecycleOwner) { if (currentTab == 1) updateList() }
        viewModel.completedTasks.observe(viewLifecycleOwner) { if (currentTab == 2) updateList() }

        viewModel.errorMessage.observe(viewLifecycleOwner) { msg ->
            if (!msg.isNullOrEmpty()) Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun refreshProgress(done: Int, total: Int) {
        val pct = if (total > 0) done * 100 / total else 0
        binding.tvProgressPct.text = "$pct%"
        binding.progressTasks.progress = pct
        binding.tvProgressNote.text = "$done of $total tasks completed"
    }

    // ── Avatar stack ─────────────────────────────────────────────────────────

    private fun buildAvatarStack(members: List<TripMember>) {
        binding.llAvatarStack.removeAllViews()
        if (members.isEmpty()) return

        val dp = resources.displayMetrics.density
        val size = (26 * dp).toInt()
        val borderStroke = (2 * dp).toInt()
        val innerSize = size - 2 * borderStroke
        val overlap = (-5 * dp).toInt()

        members.take(2).forEachIndexed { index, member ->
            val (bgColor, textColor) = UserAvatarIdentity.chipColors(member, index)
            val initial = UserAvatarIdentity.displayLetter(member).toString()

            // Initial letter avatar — always shown immediately as placeholder
            val avatar = TextView(requireContext()).apply {
                text = initial
                gravity = Gravity.CENTER
                textSize = 9f
                setTypeface(null, Typeface.BOLD)
                setTextColor(textColor)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(bgColor)
                    setStroke(borderStroke, Color.WHITE)
                }
            }
            val lp = LinearLayout.LayoutParams(size, size)
            if (index > 0) lp.marginStart = overlap
            binding.llAvatarStack.addView(avatar, lp)

            // Replace with actual profile photo when available
            val photoUrl = member.photoUrl
            if (!photoUrl.isNullOrEmpty()) {
                loadMemberPhoto(avatar, photoUrl, size, borderStroke, innerSize)
            }
        }

        if (members.size > 2) {
            val remaining = members.size - 2
            val chip = TextView(requireContext()).apply {
                text = "+$remaining"
                gravity = Gravity.CENTER
                textSize = 9f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#F48C25"))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#1FF48C25"))
                    setStroke(borderStroke, Color.WHITE)
                }
            }
            val lp = LinearLayout.LayoutParams(size, size)
            lp.marginStart = overlap
            binding.llAvatarStack.addView(chip, lp)
        }
    }

    /** Loads a profile photo (base64 string or URL) and updates the avatar TextView once ready. */
    private fun loadMemberPhoto(avatar: TextView, photoUrl: String, size: Int, borderStroke: Int, innerSize: Int) {
        if (ImageUtils.isBase64Image(photoUrl)) {
            lifecycleScope.launch {
                val bmp = withContext(Dispatchers.IO) { ImageUtils.base64ToBitmap(photoUrl) }
                if (bmp != null && isAdded) applyCircularPhoto(avatar, bmp, size, borderStroke, innerSize)
            }
        } else {
            Glide.with(this)
                .asBitmap()
                .load(photoUrl)
                .override(innerSize, innerSize)
                .circleCrop()
                .into(object : CustomTarget<Bitmap>() {
                    override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                        if (isAdded) applyCircularPhoto(avatar, resource, size, borderStroke, innerSize)
                    }
                    override fun onLoadCleared(placeholder: Drawable?) {}
                })
        }
    }

    /**
     * Composites a circular photo (with a white border ring) onto the avatar TextView background.
     * Uses BitmapShader for clean anti-aliased edge. Center 1:1 crop first so non-square images are not stretched.
     */
    private fun applyCircularPhoto(avatar: TextView, src: Bitmap, size: Int, borderStroke: Int, innerSize: Int) {
        val result = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // White ring
        paint.color = Color.WHITE
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)

        val square = ImageUtils.cropToCenterSquare(src)
        val scaled = Bitmap.createScaledBitmap(square, innerSize, innerSize, true)
        val shader = BitmapShader(scaled, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        val matrix = Matrix().apply { setTranslate(borderStroke.toFloat(), borderStroke.toFloat()) }
        shader.setLocalMatrix(matrix)
        paint.shader = shader
        canvas.drawCircle(size / 2f, size / 2f, innerSize / 2f, paint)

        avatar.text = ""
        avatar.background = BitmapDrawable(resources, result)
    }

    // ── Edit / Delete ─────────────────────────────────────────────────────────

    private fun onEditTask(task: Task) {
        val tripId = arguments?.getString("tripId") ?: return
        AddTaskBottomSheet.newInstance(tripId, task)
            .show(childFragmentManager, "EditTask")
    }

    private fun onDeleteTask(task: Task) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Task")
            .setMessage("Delete \"${task.title as? String ?: "this task"}\"?")
            .setPositiveButton("Delete") { _, _ -> viewModel.deleteTask(task) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ── FAB ──────────────────────────────────────────────────────────────────

    private fun setupFab() {
        binding.fabAddTask.setOnClickListener {
            val tripId = arguments?.getString("tripId") ?: return@setOnClickListener
            AddTaskBottomSheet.newInstance(tripId, null)
                .show(childFragmentManager, "AddTask")
        }
    }

    // ── Bottom nav ───────────────────────────────────────────────────────────

    private fun setupBottomNav() {
        val tripId = arguments?.getString("tripId") ?: ""
        setActiveNavItem("tasks")

        binding.navHome.setOnClickListener {
            val bundle = Bundle().apply { putString("tripId", tripId) }
            findNavController().navigate(R.id.homeFragment, bundle)
        }
        binding.navExpenses.setOnClickListener {
            if (tripId.isNotEmpty()) {
                val bundle = Bundle().apply { putString("tripId", tripId) }
                findNavController().navigate(R.id.expensesFragment, bundle)
            }
        }
        binding.navTasks.setOnClickListener { /* already here */ }
        binding.navGroups.setOnClickListener {
            if (tripId.isNotEmpty()) {
                val bundle = Bundle().apply { putString("tripId", tripId) }
                findNavController().navigate(R.id.participantsFragment, bundle)
            }
        }
    }

    private fun setActiveNavItem(active: String) {
        val orange = ContextCompat.getColor(requireContext(), R.color.tripoo_orange)
        val grey = ContextCompat.getColor(requireContext(), R.color.tripoo_text_hint)
        binding.ivNavHome.isSelected = active == "home"
        binding.ivNavExpenses.isSelected = active == "expenses"
        binding.ivNavTasks.isSelected = active == "tasks"
        binding.ivNavGroups.isSelected = active == "groups"
        binding.tvNavHome.setTextColor(if (active == "home") orange else grey)
        binding.tvNavExpenses.setTextColor(if (active == "expenses") orange else grey)
        binding.tvNavTasks.setTextColor(if (active == "tasks") orange else grey)
        binding.tvNavGroups.setTextColor(if (active == "groups") orange else grey)
    }

    override fun onDestroyView() {
        binding.adViewTripGroup.destroy()
        super.onDestroyView()
        _binding = null
    }
}
