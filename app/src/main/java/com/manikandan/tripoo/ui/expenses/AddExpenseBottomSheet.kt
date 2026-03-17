package com.manikandan.tripoo.ui.expenses

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import com.manikandan.tripoo.R
import com.manikandan.tripoo.data.model.Expense
import com.manikandan.tripoo.data.model.TripMember
import com.manikandan.tripoo.databinding.BottomSheetAddExpenseBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class AddExpenseBottomSheet(
    private val members: List<TripMember>,
    private val onSave: (Expense) -> Unit
) : BottomSheetDialogFragment() {

    private var _binding: BottomSheetAddExpenseBinding? = null
    private val binding get() = _binding!!

    private var selectedCategory = "accommodation"
    private var selectedDate = System.currentTimeMillis()
    private val selectedMemberIds = mutableSetOf<String>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetAddExpenseBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (dialog as? BottomSheetDialog)?.behavior?.apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
        }

        binding.btnClose.setOnClickListener { dismiss() }

        setupCategoryGrid()
        setupMemberChips()
        setupSplitToggle()
        setupPaidByDropdown()
        setupDatePicker()

        binding.btnAddExpense.setOnClickListener {
            val amount = binding.etAmount.text.toString().toDoubleOrNull()
            if (amount == null || amount <= 0.0) {
                Toast.makeText(requireContext(), "Enter a valid amount", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val description = binding.etDescription.text?.toString()?.trim().orEmpty()
            if (description.isEmpty()) {
                Toast.makeText(requireContext(), "Enter a description", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val paidIndex = binding.spinnerPaidBy.listSelection
            val paidBy = members.getOrNull(paidIndex)?.userId
                ?: members.firstOrNull()?.userId.orEmpty()

            val splitWith = if (selectedMemberIds.isEmpty()) {
                members.map { it.userId }
            } else {
                selectedMemberIds.toList()
            }

            val expense = Expense(
                title = description,
                amount = amount,
                category = selectedCategory,
                paidBy = paidBy,
                splitWith = splitWith,
                timestamp = selectedDate
            )

            onSave(expense)
            dismiss()
        }
    }

    private fun setupCategoryGrid() {
        val categories = listOf(
            Triple("accommodation", R.drawable.ic_luggage, "Accommodation"),
            Triple("food", R.drawable.ic_budget, "Food"),
            Triple("transport", R.drawable.ic_schedule, "Transport"),
            Triple("drinks", R.drawable.ic_wallet, "Drinks"),
            Triple("activities", R.drawable.ic_location, "Activities"),
            Triple("other", R.drawable.ic_more_horiz, "Other")
        )

        categories.forEach { (key, icon, label) ->
            val cell = layoutInflater.inflate(
                R.layout.item_category_cell,
                binding.gridCategories,
                false
            )
            cell.tag = key
            val iconView = cell.findViewById<ImageView>(R.id.ivCatIcon)
            val labelView = cell.findViewById<TextView>(R.id.tvCatLabel)
            val iconContainer = cell.findViewById<FrameLayout>(R.id.layoutCategoryIcon)

            iconView.setImageResource(icon)
            labelView.text = label

            // Match HTML prototype category colors
            when (key) {
                "accommodation" -> {
                    iconContainer.setBackgroundResource(R.drawable.bg_cat_accommodation)
                }
                "food" -> {
                    iconContainer.setBackgroundResource(R.drawable.bg_cat_food)
                }
                "transport" -> {
                    iconContainer.setBackgroundResource(R.drawable.bg_cat_transport)
                }
                "drinks" -> {
                    iconContainer.setBackgroundResource(R.drawable.bg_cat_drinks)
                }
                "activities" -> {
                    iconContainer.setBackgroundResource(R.drawable.bg_cat_activities)
                }
                "other" -> {
                    iconContainer.setBackgroundResource(R.drawable.bg_cat_other)
                }
            }

            cell.setOnClickListener { selectCategory(key) }
            binding.gridCategories.addView(cell)
        }

        selectCategory("accommodation")
    }

    private fun selectCategory(key: String) {
        selectedCategory = key
        for (i in 0 until binding.gridCategories.childCount) {
            val child = binding.gridCategories.getChildAt(i)
            val isSelected = child.tag == key
            child.isSelected = isSelected
            val iconContainer = child.findViewById<FrameLayout>(R.id.layoutCategoryIcon)
            val labelView = child.findViewById<TextView>(R.id.tvCatLabel)

            // Tile selection state (rounded rect behind the whole cell)
            if (isSelected) {
                child.setBackgroundResource(R.drawable.bg_category_tile_selected)
                labelView.setTextColor(requireContext().getColor(R.color.tripoo_orange))
            } else {
                child.setBackgroundResource(android.R.color.transparent)
                labelView.setTextColor(requireContext().getColor(R.color.tripoo_text_primary))
            }

            // Keep icon circle backgrounds as configured in setupCategoryGrid()
            iconContainer.isSelected = isSelected
        }
    }

    private fun setupMemberChips() {
        val ctx = requireContext()
        members.forEach { member ->
            selectedMemberIds.add(member.userId)
            val chip = Chip(ctx).apply {
                text = member.name.split(" ").firstOrNull() ?: member.name
                isCheckable = true
                isChecked = true
                setOnCheckedChangeListener { _, checked ->
                    if (checked) {
                        selectedMemberIds.add(member.userId)
                    } else {
                        selectedMemberIds.remove(member.userId)
                    }
                }
            }
            binding.chipGroupMembers.addView(chip)
        }
    }

    private fun setupSplitToggle() {
        val buttons = listOf(
            binding.btnSplitEqually,
            binding.btnSplitCustom,
            binding.btnSplitJustMe
        )

        buttons.forEach { button ->
            button.setOnClickListener {
                buttons.forEach { b ->
                    b.setBackgroundResource(R.drawable.bg_split_btn_unselected)
                }
                button.setBackgroundResource(R.drawable.bg_split_btn_selected)
            }
        }
    }

    private fun setupPaidByDropdown() {
        val ctx = requireContext()
        val names = members.map { it.name }
        val adapter = ArrayAdapter(ctx, android.R.layout.simple_dropdown_item_1line, names)
        binding.spinnerPaidBy.setAdapter(adapter)
        binding.spinnerPaidBy.setText(names.firstOrNull().orEmpty(), false)
    }

    private fun setupDatePicker() {
        val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        binding.etDate.setText(sdf.format(Date(selectedDate)))

        binding.etDate.setOnClickListener {
            val cal = Calendar.getInstance().apply { timeInMillis = selectedDate }
            DatePickerDialog(
                requireContext(),
                { _, year, month, dayOfMonth ->
                    cal.set(year, month, dayOfMonth)
                    selectedDate = cal.timeInMillis
                    binding.etDate.setText(sdf.format(Date(selectedDate)))
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            ).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

