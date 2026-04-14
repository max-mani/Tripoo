package com.manikandan.tripoo.ui.expenses

import android.app.Dialog
import android.app.DatePickerDialog
import android.content.res.ColorStateList
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.manikandan.tripoo.R
import com.manikandan.tripoo.data.model.Expense
import com.manikandan.tripoo.data.model.TripMember
import com.manikandan.tripoo.utils.UserAvatarIdentity
import com.manikandan.tripoo.databinding.BottomSheetAddExpenseBinding
import com.manikandan.tripoo.utils.ImageUtils
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class AddExpenseBottomSheet(
    private val members: List<TripMember>,
    private val currentUserMember: TripMember,
    private val initialExpense: Expense? = null,
    private val onSave: (Expense) -> Unit
) : BottomSheetDialogFragment() {

    private var _binding: BottomSheetAddExpenseBinding? = null
    private val binding get() = _binding!!

    private var selectedCategory = "accommodation"
    private var selectedDate = initialExpense?.timestamp ?: System.currentTimeMillis()
    private val selectedMemberIds = mutableSetOf<String>()
    private var selectedPaidByUid = ""

    private var splitMode = "equally"
    private val memberChips = mutableListOf<Chip>()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener {
            val bs = dialog.findViewById<FrameLayout>(
                com.google.android.material.R.id.design_bottom_sheet
            )
            bs?.let {
                val behavior = BottomSheetBehavior.from(it)
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
                behavior.peekHeight = Resources.getSystem().displayMetrics.heightPixels
            }
        }
        return dialog
    }

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

        binding.btnClose.setOnClickListener { dismiss() }

        setupCategoryGrid()
        setupMemberChips()
        setupSplitToggle()
        setupPaidByDropdown()
        setupDatePicker()
        prefillIfEdit()




















































        setupAmountFieldFocusStyle()
        autoFocusAmount()

        binding.btnAddExpense.setOnClickListener { handleSubmit() }
    }

    // ── Submit ───────────────────────────────────────────────────────────────

    private fun handleSubmit() {
        val amountStr = binding.etAmount.text.toString().trim()
        val amount = amountStr.toDoubleOrNull()
        if (amount == null || amount <= 0.0) {
            (binding.etAmount.parent as? View)?.background =
                ContextCompat.getDrawable(requireContext(), R.drawable.bg_input_outline_error)
            Toast.makeText(requireContext(), "Enter a valid amount greater than 0", Toast.LENGTH_SHORT).show()
            return
        }
        resetAmountOutline()

        val description = binding.etDescription.text?.toString()?.trim().orEmpty()
        if (description.isEmpty()) {
            binding.tilDescription.error = "Description is required"
            return
        }
        binding.tilDescription.error = null

        val paidBy = selectedPaidByUid.ifBlank { currentUserMember.userId }

        val splitWith = when (splitMode) {
            "justme" -> listOf(currentUserMember.userId)
            else -> selectedMemberIds.toList().ifEmpty { members.map { it.userId } }
        }

        val expense = Expense(
            id = initialExpense?.id.orEmpty(),
            title = description,
            amount = amount,
            category = selectedCategory,
            paidBy = paidBy,
            splitWith = splitWith,
            timestamp = selectedDate,
            settled = initialExpense?.settled ?: false
        )
        onSave(expense)
        dismiss()
    }

    // ── Category grid ────────────────────────────────────────────────────────

    private fun setupCategoryGrid() {
        data class Cat(val key: String, val iconRes: Int, val label: String, val tint: Int, val bg: Int)

        val categories = listOf(
            Cat("accommodation", R.drawable.ic_home,       "Accommodation", Color.parseColor("#2563EB"), Color.parseColor("#DBEAFE")),
            Cat("food",          R.drawable.ic_restaurant, "Food",          Color.parseColor("#EA580C"), Color.parseColor("#FFEDD5")),
            Cat("transport",     R.drawable.ic_car,        "Transport",     Color.parseColor("#9333EA"), Color.parseColor("#F3E8FF")),
            Cat("drinks",        R.drawable.ic_local_bar,  "Drinks",        Color.parseColor("#16A34A"), Color.parseColor("#DCFCE7")),
            Cat("activities",    R.drawable.ic_surfing,    "Activities",    Color.parseColor("#CA8A04"), Color.parseColor("#FEF9C3")),
            Cat("other",         R.drawable.ic_more_horiz, "Other",         Color.parseColor("#6B7280"), Color.parseColor("#F3F4F6"))
        )

        categories.forEach { cat ->
            val cell = layoutInflater.inflate(R.layout.item_category_cell, binding.gridCategories, false)
            cell.tag = cat.key
            cell.findViewById<ImageView>(R.id.ivCatIcon).apply {
                setImageResource(cat.iconRes)
                imageTintList = ColorStateList.valueOf(cat.tint)
            }
            cell.findViewById<TextView>(R.id.tvCatLabel).text = cat.label
            cell.findViewById<FrameLayout>(R.id.flCatIconBg).background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(cat.bg)
            }
            cell.setOnClickListener { selectCategory(cat.key) }
            binding.gridCategories.addView(cell)
        }
        selectCategory(selectedCategory)
    }

    private fun selectCategory(key: String) {
        selectedCategory = key
        for (i in 0 until binding.gridCategories.childCount) {
            val child = binding.gridCategories.getChildAt(i)
            val sel = child.tag == key
            child.background = ContextCompat.getDrawable(
                requireContext(),
                if (sel) R.drawable.bg_category_cell_selected else R.drawable.bg_category_cell
            )
        }
    }

    // ── Member chips (Split With) ────────────────────────────────────────────
    // Each chip has a coloured letter-avatar circle (like HTML .a-av) + first name.
    // Selected: orange border + orange-tint bg. Unselected: grey border + white bg.

    private fun setupMemberChips() {
        val chipBg = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(Color.parseColor("#1FF48C25"), Color.WHITE)
        )
        val chipText = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(Color.parseColor("#F48C25"), Color.parseColor("#181411"))
        )
        val chipStroke = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(Color.parseColor("#F48C25"), Color.parseColor("#E6E0DB"))
        )

        memberChips.clear()
        binding.chipGroupMembers.removeAllViews()
        selectedMemberIds.clear()

        members.forEachIndexed { idx, member ->
            selectedMemberIds.add(member.userId)
            val (bgColor, txtColor) = UserAvatarIdentity.chipColors(member, idx)
            val letter = UserAvatarIdentity.displayLetter(member)
            val chipIconPx = (24 * resources.displayMetrics.density).toInt()

            val chip = Chip(requireContext()).apply {
                text = member.name.split(" ").firstOrNull() ?: member.name
                isCheckable = true
                isChecked = true
                chipCornerRadius = 999f
                chipBackgroundColor = chipBg
                setTextColor(chipText)
                chipStrokeColor = chipStroke
                chipStrokeWidth = 1.5f
                isChipIconVisible = true
                chipIconSize = chipIconPx.toFloat()
                // Default to initial avatar; real photo loaded below if available
                chipIcon = BitmapDrawable(resources, makeAvatarBitmap(letter, bgColor, txtColor))
                isCheckedIconVisible = false
                tag = member.userId
                setOnCheckedChangeListener { _, checked ->
                    if (splitMode != "custom") return@setOnCheckedChangeListener
                    if (checked) selectedMemberIds.add(member.userId)
                    else selectedMemberIds.remove(member.userId)
                }
            }
            memberChips.add(chip)
            binding.chipGroupMembers.addView(chip)

            // Load profile photo asynchronously — chip with initial avatar is already visible above
            val photoUrl = member.photoUrl
            if (!photoUrl.isNullOrEmpty()) {
                if (ImageUtils.isBase64Image(photoUrl)) {
                    // Decode base64 on IO thread, update chip on main thread
                    viewLifecycleOwner.lifecycleScope.launch {
                        try {
                            val bmp = withContext(Dispatchers.IO) {
                                ImageUtils.base64ToBitmap(photoUrl)
                            }
                            if (bmp != null && isAdded) {
                                val circular = withContext(Dispatchers.IO) {
                                    makeCircularPhotoBitmap(bmp, chipIconPx)
                                }
                                chip.chipIcon = BitmapDrawable(resources, circular)
                            }
                        } catch (_: Exception) { /* keep initial avatar */ }
                    }
                } else {
                    try {
                        Glide.with(this)
                            .asBitmap()
                            .load(photoUrl)
                            .circleCrop()
                            .override(chipIconPx, chipIconPx)
                            .into(object : CustomTarget<Bitmap>() {
                                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                                    if (isAdded) chip.chipIcon = BitmapDrawable(resources, resource)
                                }
                                override fun onLoadCleared(placeholder: Drawable?) {}
                            })
                    } catch (_: Exception) { /* keep initial avatar */ }
                }
            }
        }
    }

    /** Draws a coloured filled circle with the given letter centred inside it. */
    private fun makeAvatarBitmap(letter: Char, bgColor: Int, textColor: Int): Bitmap {
        val dp = resources.displayMetrics.density
        val size = (24 * dp).toInt()
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        paint.color = bgColor
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)

        paint.color = textColor
        paint.textSize = size * 0.45f
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.DEFAULT_BOLD
        val fm = paint.fontMetrics
        val textY = size / 2f - (fm.ascent + fm.descent) / 2f
        canvas.drawText(letter.toString(), size / 2f, textY, paint)

        return bmp
    }

    /** Crops a source bitmap into a circle of the given pixel size using BitmapShader. */
    private fun makeCircularPhotoBitmap(src: Bitmap, size: Int): Bitmap {
        val scaled = Bitmap.createScaledBitmap(src, size, size, true)
        val result = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = android.graphics.BitmapShader(scaled, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP)
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        return result
    }

    // ── Split toggle ─────────────────────────────────────────────────────────
    // Uses Material button APIs (backgroundTintList / strokeColor / iconTint)
    // so the orange highlight works correctly without overriding shadows.

    private fun setupSplitToggle() {
        fun applyStyle(btn: MaterialButton, selected: Boolean) {
            val fgColor = if (selected) Color.parseColor("#F48C25") else Color.parseColor("#8A7560")
            btn.backgroundTintList = ColorStateList.valueOf(
                if (selected) Color.parseColor("#1FF48C25") else Color.WHITE
            )
            btn.strokeColor = ColorStateList.valueOf(
                if (selected) Color.parseColor("#F48C25") else Color.parseColor("#E6E0DB")
            )
            btn.setTextColor(fgColor)
            btn.iconTint = ColorStateList.valueOf(fgColor)
        }

        fun selectSplit(mode: String) {
            splitMode = mode
            applyStyle(binding.btnSplitEqually, mode == "equally")
            applyStyle(binding.btnSplitCustom,  mode == "custom")
            applyStyle(binding.btnSplitJustMe,  mode == "justme")

            when (mode) {
                "equally" -> {
                    selectedMemberIds.clear()
                    selectedMemberIds.addAll(members.map { it.userId })
                    memberChips.forEach { it.isChecked = true; it.isEnabled = false }
                }
                "custom" -> {
                    memberChips.forEach { it.isEnabled = true }
                }
                "justme" -> {
                    selectedMemberIds.clear()
                    selectedMemberIds.add(currentUserMember.userId)
                    memberChips.forEach { chip ->
                        val uid = chip.tag as? String
                        chip.isChecked = uid == currentUserMember.userId
                        chip.isEnabled = false
                    }
                }
            }
        }

        binding.btnSplitEqually.setOnClickListener { selectSplit("equally") }
        binding.btnSplitCustom.setOnClickListener  { selectSplit("custom")  }
        binding.btnSplitJustMe.setOnClickListener  { selectSplit("justme")  }

        selectSplit(if (initialExpense != null) "custom" else "equally")
    }

    // ── Paid By dropdown ─────────────────────────────────────────────────────
    // Shows a PopupMenu anchored to the row — same pattern as the expense-list
    // "more" button so the popup appears right below the field.

    private fun setupPaidByDropdown() {
        selectedPaidByUid = currentUserMember.userId
        val paidByChoices = if (members.isNotEmpty()) members else listOf(currentUserMember)
        val defaultName = paidByChoices.firstOrNull { it.userId == currentUserMember.userId }?.name
            ?.takeIf { it.isNotBlank() }
            ?: currentUserMember.name.takeIf { it.isNotBlank() }
            ?: paidByChoices.firstOrNull()?.name.orEmpty()
        binding.tvPaidBySelected.text = defaultName

        binding.containerPaidBy.setOnClickListener { anchor ->
            val wrapper = ContextThemeWrapper(
                requireContext(),
                androidx.appcompat.R.style.ThemeOverlay_AppCompat_Light
            )
            val popup = PopupMenu(wrapper, anchor)
            paidByChoices.forEachIndexed { i, m -> popup.menu.add(0, i, i, m.name) }
            popup.setOnMenuItemClickListener { item ->
                val picked = paidByChoices.getOrNull(item.itemId) ?: return@setOnMenuItemClickListener false
                selectedPaidByUid = picked.userId
                binding.tvPaidBySelected.text = picked.name
                true
            }
            popup.show()
        }
    }

    // ── Date picker ───────────────────────────────────────────────────────────

    private fun setupDatePicker() {
        val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        binding.etDate.setText(sdf.format(Date(selectedDate)))
        binding.etDate.setOnClickListener {
            val cal = Calendar.getInstance().apply { timeInMillis = selectedDate }
            DatePickerDialog(
                ContextThemeWrapper(requireContext(), R.style.ThemeOverlay_Tripoo_DatePickerDialog),
                { _, year, month, day ->
                    cal.set(year, month, day)
                    selectedDate = cal.timeInMillis
                    binding.etDate.setText(sdf.format(Date(selectedDate)))
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            ).show()
        }
    }

    // ── Edit prefill ─────────────────────────────────────────────────────────

    private fun prefillIfEdit() {
        val existing = initialExpense ?: return
        binding.etAmount.setText(existing.amount.toString())
        binding.etDescription.setText(existing.title)
        selectedCategory = existing.category
        selectedDate = existing.timestamp
        selectCategory(selectedCategory)

        val paidName = members.firstOrNull { it.userId == existing.paidBy }?.name.orEmpty()
        if (paidName.isNotEmpty()) {
            selectedPaidByUid = existing.paidBy
            binding.tvPaidBySelected.text = paidName
        }

        selectedMemberIds.clear()
        selectedMemberIds.addAll(existing.splitWith)
        memberChips.forEach { chip ->
            val uid = chip.tag as? String
            chip.isChecked = uid != null && selectedMemberIds.contains(uid)
        }

        binding.btnAddExpense.text = getString(R.string.save_changes)
    }

    // ── Amount field focus style ─────────────────────────────────────────────

    private fun setupAmountFieldFocusStyle() {
        binding.etAmount.setOnFocusChangeListener { _, hasFocus ->
            (binding.etAmount.parent as? View)?.background = ContextCompat.getDrawable(
                requireContext(),
                if (hasFocus) R.drawable.bg_input_outline_focused else R.drawable.bg_input_outline
            )
        }
    }

    private fun resetAmountOutline() {
        (binding.etAmount.parent as? View)?.background =
            ContextCompat.getDrawable(requireContext(), R.drawable.bg_input_outline)
    }

    private fun autoFocusAmount() {
        Handler(Looper.getMainLooper()).postDelayed({
            if (!isAdded || _binding == null) return@postDelayed
            binding.etAmount.requestFocus()
            val imm = requireContext().getSystemService(InputMethodManager::class.java)
            imm?.showSoftInput(binding.etAmount, InputMethodManager.SHOW_IMPLICIT)
        }, 120)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
