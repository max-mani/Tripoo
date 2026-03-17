package com.manikandan.tripoo.ui.expenses

import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.manikandan.tripoo.databinding.FragmentExpensesBinding

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

    private var adapter: ExpenseAdapter? = null

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

        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        // Trip header
        viewModel.trip.observe(viewLifecycleOwner) { trip ->
            trip ?: return@observe
            binding.tvTripName.text = trip.name

            val memberCount = trip.memberIds.size
            val dateText = if (trip.startDate > 0L && trip.endDate > 0L) {
                DateUtils.formatDateRange(
                    requireContext(),
                    trip.startDate,
                    trip.endDate,
                    DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_NO_YEAR
                )
            } else {
                ""
            }
            binding.tvTripMeta.text =
                if (dateText.isNotEmpty()) "$memberCount participants · $dateText"
                else "$memberCount participants"
        }

        viewModel.members.observe(viewLifecycleOwner) { members ->
            val memberNames = members.associate { it.userId to it.name }
            val currentUserId = viewModel.currentUserId
            val adapter = ExpenseAdapter(currentUserId, memberNames) {
                // click handler reserved for future detail screen
            }
            this.adapter = adapter
            binding.rvExpenses.layoutManager = LinearLayoutManager(requireContext())
            binding.rvExpenses.adapter = adapter

            viewModel.expenseItems.observe(viewLifecycleOwner) { items ->
                adapter.submitList(items)
            }
        }

        viewModel.youOwe.observe(viewLifecycleOwner) { amount ->
            binding.tvYouOwe.text = String.format("₹%.2f", amount)
        }

        viewModel.youAreOwed.observe(viewLifecycleOwner) { amount ->
            binding.tvYouAreOwed.text = String.format("₹%.2f", amount)
        }

        // Tabs filter
        fun selectTab(selected: View) {
            val orange = requireContext().getColor(com.manikandan.tripoo.R.color.tripoo_orange)
            val grey = requireContext().getColor(com.manikandan.tripoo.R.color.tripoo_text_hint)
            val allTabs = listOf(binding.tabAll, binding.tabMy, binding.tabSettled, binding.tabStats)
            allTabs.forEach { tab ->
                val isSelected = tab == selected
                if (tab is android.widget.TextView) {
                    tab.setTextColor(if (isSelected) orange else grey)
                }
            }
        }

        binding.tabAll.setOnClickListener {
            selectTab(binding.tabAll)
            viewModel.setFilter(ExpenseFilter.ALL)
        }
        binding.tabMy.setOnClickListener {
            selectTab(binding.tabMy)
            viewModel.setFilter(ExpenseFilter.MY_SPENDING)
        }
        binding.tabSettled.setOnClickListener {
            selectTab(binding.tabSettled)
            viewModel.setFilter(ExpenseFilter.SETTLED)
        }
        binding.tabStats.setOnClickListener {
            selectTab(binding.tabStats)
            viewModel.setFilter(ExpenseFilter.STATS)
        }

        binding.fabAddExpense.setOnClickListener {
            val members = viewModel.members.value.orEmpty()
            AddExpenseBottomSheet(members) { expense ->
                viewModel.addExpense(expense)
                Snackbar.make(binding.root, "Expense added!", Snackbar.LENGTH_SHORT).show()
            }.show(childFragmentManager, "AddExpense")
        }

        // Bottom nav navigation
        binding.navHome.setOnClickListener {
            findNavController().popBackStack()
        }
        binding.navExpenses.setOnClickListener {
            // already here
        }
        binding.navTasks.setOnClickListener {
            val action =
                ExpensesFragmentDirections.actionExpensesToTasks(args.tripId)
            findNavController().navigate(action)
        }
        binding.navGroups.setOnClickListener {
            val action =
                ExpensesFragmentDirections.actionExpensesToParticipants(args.tripId)
            findNavController().navigate(action)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        adapter = null
    }
}

