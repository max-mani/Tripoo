package com.manikandan.tripoo.ui.expenses

import android.app.AlertDialog
import androidx.activity.OnBackPressedCallback
import android.view.ContextThemeWrapper
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.ads.AdRequest
import com.google.android.material.snackbar.Snackbar
import com.manikandan.tripoo.R
import com.manikandan.tripoo.ads.TripExitInterstitialHelper
import com.manikandan.tripoo.data.model.Expense
import com.manikandan.tripoo.data.model.TripMember
import com.manikandan.tripoo.databinding.FragmentExpensesBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ExpensesFragment : Fragment() {

    private var _binding: FragmentExpensesBinding? = null
    private val binding get() = _binding!!

    private val args: ExpensesFragmentArgs by navArgs()

    private val viewModel: ExpensesViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return ExpensesViewModel(
                    androidx.lifecycle.SavedStateHandle(
                        mapOf("tripId" to args.tripId)
                    )
                ) as T
            }
        }
    }

    private lateinit var expenseAdapter: ExpenseAdapter
    private var currentTab = 0
    private var currentSort = SortMode.LATEST
    private var searchQuery = ""
    private var activeTabLiveData: LiveData<List<ExpenseAdapter.ExpenseListItem>>? = null
    private var activeTabObserver: Observer<List<ExpenseAdapter.ExpenseListItem>>? = null
    private var baseItems: List<ExpenseAdapter.ExpenseListItem> = emptyList()
    private var isCurrentUserAdmin: Boolean = false

    private enum class SortMode { LATEST, OLDEST, HIGHEST_AMOUNT, LOWEST_AMOUNT }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentExpensesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecycler()
        setupTabs()
        setupActions()
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = navigateToTripDashboard()
            }
        )
        setupBottomNav()
        setActiveBottomNav("expenses")
        observeViewModel()
        selectTab(0)
        binding.adViewTripGroup.loadAd(AdRequest.Builder().build())
    }

    override fun onPause() {
        binding.adViewTripGroup.pause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        binding.adViewTripGroup.resume()
        if (args.tripId.isNotEmpty()) {
            TripExitInterstitialHelper.preload(requireContext())
        }
    }

    private fun setupRecycler() {
        expenseAdapter = ExpenseAdapter(
            currentUserId = viewModel.currentUserId,
            memberNames = emptyMap(),
            canMarkSettled = false,
            onClick = {},
            onEdit = { openEditExpense(it) },
            onDelete = { confirmDelete(it) },
            onSettle = { markAsSettled(it) }
        )
        binding.rvExpenses.layoutManager = LinearLayoutManager(requireContext())
        binding.rvExpenses.adapter = expenseAdapter
    }

    private fun setupTabs() {
        binding.tabAll.setOnClickListener { selectTab(0) }
        binding.tabMy.setOnClickListener { selectTab(1) }
        binding.tabSettled.setOnClickListener { selectTab(2) }
        binding.tabStats.setOnClickListener { selectTab(3) }
    }

    private fun navigateToTripDashboard() {
        val nav = findNavController()
        TripExitInterstitialHelper.navigateToTripDashboard(
            requireActivity(),
            args.tripId.takeIf { it.isNotEmpty() }
        ) {
            try {
                if (!nav.popBackStack(R.id.tripDashboardFragment, false)) {
                    nav.navigate(R.id.tripDashboardFragment)
                }
            } catch (_: Exception) {
                nav.navigate(R.id.tripDashboardFragment)
            }
        }
    }

    private fun setupActions() {
        binding.btnBack.setOnClickListener { navigateToTripDashboard() }
        binding.btnSearch.setOnClickListener {
            binding.etSearchExpenses.visibility =
                if (binding.etSearchExpenses.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            if (binding.etSearchExpenses.visibility == View.GONE) {
                searchQuery = ""
                binding.etSearchExpenses.setText("")
                renderCurrentList()
            }
        }
        binding.btnMore.setOnClickListener { showMoreMenu() }
        binding.swipeRefreshExpenses.setOnRefreshListener {
            viewModel.refresh()
            binding.swipeRefreshExpenses.postDelayed(
                { binding.swipeRefreshExpenses.isRefreshing = false },
                500
            )
        }
        binding.etSearchExpenses.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                searchQuery = s?.toString().orEmpty().trim()
                renderCurrentList()
            }
        })

        binding.fabAddExpense.setOnClickListener {
            val (members, currentUserMember) = membersForExpenseSheet()
            AddExpenseBottomSheet(members, currentUserMember = currentUserMember) { expense ->
                viewModel.addExpense(expense)
                Snackbar.make(binding.root, "Expense added!", Snackbar.LENGTH_SHORT)
                    .setBackgroundTint(Color.parseColor("#181411"))
                    .setTextColor(Color.WHITE)
                    .show()
            }.show(childFragmentManager, "AddExpense")
        }
    }

    private fun setupBottomNav() {
        binding.navHome.setOnClickListener { navigateToTripDashboard() }
        binding.navExpenses.setOnClickListener {
            setActiveBottomNav("expenses")
        }
        binding.navTasks.setOnClickListener {
            val action = ExpensesFragmentDirections.actionExpensesToTasks(args.tripId)
            findNavController().navigate(action)
        }
        binding.navGroups.setOnClickListener {
            val action = ExpensesFragmentDirections.actionExpensesToParticipants(args.tripId)
            findNavController().navigate(action)
        }
    }

    private fun setActiveBottomNav(tab: String) {
        val orange = ContextCompat.getColor(requireContext(), R.color.tripoo_orange)
        val grey = ContextCompat.getColor(requireContext(), R.color.tripoo_text_hint)

        binding.ivNavHome.isSelected = tab == "home"
        binding.ivNavExpenses.isSelected = tab == "expenses"
        binding.ivNavTasks.isSelected = tab == "tasks"
        binding.ivNavGroups.isSelected = tab == "groups"

        binding.tvNavHome.setTextColor(if (tab == "home") orange else grey)
        binding.tvNavExpenses.setTextColor(if (tab == "expenses") orange else grey)
        binding.tvNavTasks.setTextColor(if (tab == "tasks") orange else grey)
        binding.tvNavGroups.setTextColor(if (tab == "groups") orange else grey)
    }

    /** Co-organisers ([TripMember.isAdmin]) and the trip organiser can mark expenses settled. */
    private fun syncExpenseSettlePermission() {
        val members = viewModel.members.value.orEmpty()
        val trip = viewModel.trip.value
        val currentUid = viewModel.currentUserId
        isCurrentUserAdmin =
            members.firstOrNull { it.userId == currentUid }?.isAdmin == true ||
                trip?.adminId == currentUid
        if (::expenseAdapter.isInitialized) {
            expenseAdapter.setCanMarkSettled(isCurrentUserAdmin)
        }
    }

    private fun observeViewModel() {
        viewModel.trip.observe(viewLifecycleOwner) { trip ->
            binding.tvTripName.text = trip?.name.orEmpty()
            val sdf = SimpleDateFormat("MMM d", Locale.getDefault())
            val dateRange = if (trip != null) {
                "${sdf.format(Date(trip.startDate))}-${sdf.format(Date(trip.endDate))}"
            } else {
                ""
            }
            val memberCount = trip?.memberIds?.size ?: 0
            binding.tvTripSubtitle.text =
                if (dateRange.isEmpty()) "$memberCount participants"
                else "$memberCount participants · $dateRange"
            syncExpenseSettlePermission()
        }

        viewModel.members.observe(viewLifecycleOwner) { members ->
            val memberNames = members.associate { it.userId to it.name }
            val currentUid = viewModel.currentUserId
            isCurrentUserAdmin =
                members.firstOrNull { it.userId == currentUid }?.isAdmin == true ||
                viewModel.trip.value?.adminId == currentUid
            expenseAdapter = ExpenseAdapter(
                currentUserId = viewModel.currentUserId,
                memberNames = memberNames,
                canMarkSettled = isCurrentUserAdmin,
                onClick = {},
                onEdit = { openEditExpense(it) },
                onDelete = { confirmDelete(it) },
                onSettle = { markAsSettled(it) }
            )
            binding.rvExpenses.adapter = expenseAdapter
            observeCurrentTabData()
        }

        viewModel.youOwe.observe(viewLifecycleOwner) { amount ->
            binding.tvYouOwe.text = String.format("₹%.2f", amount)
        }
        viewModel.youAreOwed.observe(viewLifecycleOwner) { amount ->
            binding.tvYouAreOwed.text = String.format("₹%.2f", amount)
        }
        viewModel.oweTrendPct.observe(viewLifecycleOwner) { pct ->
            val arrow = if (pct >= 0) "▲" else "▼"
            binding.tvOweTrend.text = "$arrow ${kotlin.math.abs(pct)}% vs last week"
            binding.tvOweTrend.setTextColor(Color.parseColor("#DC2626"))
        }
        viewModel.owedTrendPct.observe(viewLifecycleOwner) { pct ->
            val arrow = if (pct >= 0) "▲" else "▼"
            binding.tvOwedTrend.text = "$arrow ${kotlin.math.abs(pct)}% vs last week"
            binding.tvOwedTrend.setTextColor(Color.parseColor("#16A34A"))
        }
        viewModel.statsData.observe(viewLifecycleOwner) {
            if (currentTab == 3) renderStats(it)
        }
    }

    private fun selectTab(index: Int) {
        currentTab = index
        val tabs = listOf(binding.tabAll, binding.tabMy, binding.tabSettled, binding.tabStats)
        val indicators = listOf(binding.indAll, binding.indMy, binding.indSettled, binding.indStats)
        tabs.forEachIndexed { i, tab ->
            tab.setTextColor(
                if (i == index) Color.parseColor("#F48C25")
                else Color.parseColor("#9CA3AF")
            )
            indicators[i].visibility = if (i == index) View.VISIBLE else View.INVISIBLE
        }
        if (index == 3) {
            binding.rvExpenses.visibility = View.GONE
            binding.emptyExpenses.visibility = View.GONE
            binding.statsContainer.visibility = View.VISIBLE
            viewModel.statsData.value?.let { renderStats(it) }
        } else {
            binding.statsContainer.visibility = View.GONE
            binding.rvExpenses.visibility = View.VISIBLE
            observeCurrentTabData()
        }
    }

    private fun observeCurrentTabData() {
        val newLiveData = viewModel.getItemsForTab(currentTab)
        activeTabLiveData?.let { old ->
            activeTabObserver?.let { old.removeObserver(it) }
        }
        val observer = Observer<List<ExpenseAdapter.ExpenseListItem>> { items ->
            baseItems = items
            renderCurrentList()
            updateEmptyState(items)
        }
        activeTabLiveData = newLiveData
        activeTabObserver = observer
        newLiveData.observe(viewLifecycleOwner, observer)
    }

    private fun renderCurrentList() {
        var items = baseItems
        if (searchQuery.isNotEmpty()) {
            val lowered = searchQuery.lowercase(Locale.getDefault())
            items = filterBySearch(items, lowered)
        }
        items = sortItems(items, currentSort)
        expenseAdapter.submitList(items)
        updateEmptyState(items)
    }

    private fun filterBySearch(
        source: List<ExpenseAdapter.ExpenseListItem>,
        query: String
    ): List<ExpenseAdapter.ExpenseListItem> {
        val onlyRows = source.filterIsInstance<ExpenseAdapter.ExpenseListItem.ExpenseRow>()
            .filter { it.expense.title.lowercase(Locale.getDefault()).contains(query) }
        val grouped = onlyRows.groupBy {
            SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date(it.expense.timestamp))
        }
        val orderedKeys = grouped.keys.sortedDescending()
        val out = mutableListOf<ExpenseAdapter.ExpenseListItem>()
        orderedKeys.forEach { key ->
            val dayRows = grouped[key].orEmpty().sortedByDescending { it.expense.timestamp }
            if (dayRows.isNotEmpty()) {
                out.add(ExpenseAdapter.ExpenseListItem.DateHeader(buildDateLabel(dayRows.first().expense.timestamp)))
                out.addAll(dayRows)
            }
        }
        return out
    }

    private fun sortItems(
        source: List<ExpenseAdapter.ExpenseListItem>,
        mode: SortMode
    ): List<ExpenseAdapter.ExpenseListItem> {
        val rows = source.filterIsInstance<ExpenseAdapter.ExpenseListItem.ExpenseRow>().toMutableList()
        when (mode) {
            SortMode.LATEST -> rows.sortByDescending { it.expense.timestamp }
            SortMode.OLDEST -> rows.sortBy { it.expense.timestamp }
            SortMode.HIGHEST_AMOUNT -> rows.sortByDescending { it.expense.amount }
            SortMode.LOWEST_AMOUNT -> rows.sortBy { it.expense.amount }
        }
        val grouped = rows.groupBy {
            SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date(it.expense.timestamp))
        }
        val keys = grouped.keys.sortedDescending()
        val rebuilt = mutableListOf<ExpenseAdapter.ExpenseListItem>()
        keys.forEach { key ->
            val dayRows = grouped[key].orEmpty()
            rebuilt.add(ExpenseAdapter.ExpenseListItem.DateHeader(buildDateLabel(dayRows.first().expense.timestamp)))
            rebuilt.addAll(dayRows)
        }
        return rebuilt
    }

    private fun buildDateLabel(timestamp: Long): String {
        val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        val startToday = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        val yesterday = startToday - 86_400_000L
        return when {
            timestamp >= startToday -> "TODAY"
            timestamp >= yesterday -> "YESTERDAY"
            else -> sdf.format(Date(timestamp)).uppercase()
        }
    }

    private fun updateEmptyState(items: List<ExpenseAdapter.ExpenseListItem>) {
        val isEmpty = items.none { it is ExpenseAdapter.ExpenseListItem.ExpenseRow }
        binding.emptyExpenses.visibility =
            if (currentTab != 3 && isEmpty) View.VISIBLE else View.GONE
        binding.emptyExpenses.text = when (currentTab) {
            1 -> "No expenses found for you"
            2 -> "All clear! No settled expenses yet."
            else -> "No expenses found"
        }
        if (currentTab == 2) {
            binding.emptyExpenses.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.ic_check_circle, 0, 0)
        } else {
            binding.emptyExpenses.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.ic_receipt, 0, 0)
        }
    }

    private fun showMoreMenu() {
        val wrapper = ContextThemeWrapper(requireContext(), androidx.appcompat.R.style.ThemeOverlay_AppCompat_Light)
        val popup = android.widget.PopupMenu(wrapper, binding.btnMore)
        popup.menu.add(0, 1, 0, "Latest first")
        popup.menu.add(0, 2, 1, "Oldest first")
        popup.menu.add(0, 3, 2, "Highest amount")
        popup.menu.add(0, 4, 3, "Lowest amount")
        popup.setOnMenuItemClickListener {
            currentSort = when (it.itemId) {
                2 -> SortMode.OLDEST
                3 -> SortMode.HIGHEST_AMOUNT
                4 -> SortMode.LOWEST_AMOUNT
                else -> SortMode.LATEST
            }
            renderCurrentList()
            true
        }
        popup.show()
    }

    /**
     * Trip members may not be in [ExpensesViewModel.members] yet on first paint;
     * the sheet still needs a non-empty list so "Paid by" and split chips work.
     */
    private fun membersForExpenseSheet(): Pair<List<TripMember>, TripMember> {
        val members = viewModel.members.value.orEmpty()
        val currentUserMember = members.firstOrNull { it.userId == viewModel.currentUserId }
            ?: TripMember(userId = viewModel.currentUserId, name = "You")
        val list = if (members.isNotEmpty()) members else listOf(currentUserMember)
        return list to currentUserMember
    }

    private fun openEditExpense(expense: Expense) {
        val (members, currentUserMember) = membersForExpenseSheet()
        AddExpenseBottomSheet(members, currentUserMember = currentUserMember, initialExpense = expense) { updated ->
            viewModel.updateExpense(updated)
            Snackbar.make(binding.root, "Expense updated", Snackbar.LENGTH_SHORT).show()
        }.show(childFragmentManager, "EditExpense")
    }

    private fun confirmDelete(expense: Expense) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete expense")
            .setMessage("Delete \"${expense.title}\"?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deleteExpense(expense.id)
                Snackbar.make(binding.root, "Expense deleted", Snackbar.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun markAsSettled(expense: Expense) {
        if (!isCurrentUserAdmin) return
        viewModel.markExpenseSettled(expense.id)
        Snackbar.make(binding.root, "Expense marked as settled", Snackbar.LENGTH_SHORT).show()
    }

    private fun renderStats(stats: ExpensesViewModel.ExpenseStats) {
        binding.viewStats.tvStatsTotal.text = "₹${"%.2f".format(stats.total)}"
        binding.viewStats.tvStatsAvg.text = "₹${"%.2f".format(stats.avgPerPerson)} per person"
        binding.viewStats.tvStatsTopSpender.text = stats.topSpender?.let { "${it.first} — ₹${"%.2f".format(it.second)}" } ?: "—"
        binding.viewStats.statsCategories.removeAllViews()
        val total = if (stats.total > 0.0) stats.total else 1.0
        stats.byCategory.toList().sortedByDescending { it.second }.forEach { (category, amount) ->
            if (amount <= 0.0) return@forEach
            val row = layoutInflater.inflate(R.layout.item_stats_category_row, binding.viewStats.statsCategories, false)
            val iconBox = row.findViewById<FrameLayout>(R.id.layoutCatIcon)
            val icon = row.findViewById<ImageView>(R.id.ivCatIcon)
            val tvName = row.findViewById<TextView>(R.id.tvCatName)
            val bar = row.findViewById<ProgressBar>(R.id.barCat)
            val tvAmount = row.findViewById<TextView>(R.id.tvCatAmount)
            val percent = ((amount / total) * 100).toInt().coerceIn(0, 100)
            val (iconRes, bgColor, tintColor) = categoryStyle(category)
            iconBox.backgroundTintList = android.content.res.ColorStateList.valueOf(bgColor)
            icon.setImageResource(iconRes)
            icon.imageTintList = android.content.res.ColorStateList.valueOf(tintColor)
            tvName.text = category.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            bar.progress = percent
            tvAmount.text = "₹${"%.0f".format(amount)} ($percent%)"
            binding.viewStats.statsCategories.addView(row)
        }
    }

    private fun categoryStyle(cat: String): Triple<Int, Int, Int> {
        return when (cat) {
            "accommodation" -> Triple(R.drawable.ic_home, Color.parseColor("#DBEAFE"), Color.parseColor("#2563EB"))
            "food" -> Triple(R.drawable.ic_restaurant, Color.parseColor("#FFEDD5"), Color.parseColor("#EA580C"))
            "transport" -> Triple(R.drawable.ic_car, Color.parseColor("#F3E8FF"), Color.parseColor("#9333EA"))
            "drinks" -> Triple(R.drawable.ic_local_bar, Color.parseColor("#DCFCE7"), Color.parseColor("#16A34A"))
            "activities" -> Triple(R.drawable.ic_surfing, Color.parseColor("#FEF9C3"), Color.parseColor("#CA8A04"))
            else -> Triple(R.drawable.ic_more_horiz, Color.parseColor("#F3F4F6"), Color.parseColor("#6B7280"))
        }
    }

    override fun onDestroyView() {
        binding.adViewTripGroup.destroy()
        super.onDestroyView()
        _binding = null
    }
}

