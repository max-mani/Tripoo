package com.manikandan.tripoo.ui.tasks

import android.app.DatePickerDialog
import android.app.Dialog
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import com.manikandan.tripoo.R
import com.manikandan.tripoo.data.model.Task
import com.manikandan.tripoo.data.model.TripMember
import com.manikandan.tripoo.data.repository.TaskRepository
import com.manikandan.tripoo.data.repository.TripRepository
import com.manikandan.tripoo.databinding.BottomSheetAddTaskBinding
import com.manikandan.tripoo.utils.ImageUtils
import com.manikandan.tripoo.utils.UserAvatarIdentity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AddTaskBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetAddTaskBinding? = null
    private val binding get() = _binding!!

    private lateinit var tripId: String
    private var editTask: Task? = null

    private var selectedCategory = "general"
    private var selectedAssigneeId = "everyone"
    private var selectedPriority = "medium"
    private var selectedDueDate: Long? = null

    private var members: List<TripMember> = emptyList()
    private val memberChips = mutableListOf<Chip>()
    private var updatingChips = false

    companion object {
        fun newInstance(tripId: String, task: Task?): AddTaskBottomSheet {
            return AddTaskBottomSheet().apply {
                arguments = Bundle().apply { putString("tripId", tripId) }
                editTask = task
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener {
            val bs = dialog.findViewById<FrameLayout>(
                com.google.android.material.R.id.design_bottom_sheet
            )
            bs?.let {
                BottomSheetBehavior.from(it).apply {
                    state = BottomSheetBehavior.STATE_EXPANDED
                    skipCollapsed = true
                }
            }
        }
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetAddTaskBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        tripId = arguments?.getString("tripId") ?: ""

        binding.btnClose.setOnClickListener { dismiss() }
        setupCategoryDropdown()
        setupPriorityButtons()
        setupDueDatePicker()
        prefillIfEdit()

        // Load members then build assignee chips
        loadMembers()

        binding.btnSaveTask.setOnClickListener { submitTask() }
    }

    // ── Members ───────────────────────────────────────────────────────────────

    private fun loadMembers() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                members = withContext(Dispatchers.IO) {
                    TripRepository().getTripMembers(tripId)
                }
                setupAssigneeChips()
            } catch (_: Exception) {
                setupAssigneeChips() // show just "Everyone" on error
            }
        }
    }

    // ── Category dropdown ─────────────────────────────────────────────────────

    private fun setupCategoryDropdown() {
        val categories = listOf("General", "Bookings", "Packing", "Documents", "Other")
        val categoryKeys = listOf("general", "bookings", "packing", "documents", "other")

        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            categories
        )
        binding.actvCategory.setAdapter(adapter)
        binding.actvCategory.setText(categories[0], false)

        binding.actvCategory.setOnItemClickListener { _, _, pos, _ ->
            selectedCategory = categoryKeys[pos]
        }

        // Make the whole FrameLayout parent tappable to open the dropdown
        (binding.actvCategory.parent as? View)?.setOnClickListener {
            binding.actvCategory.showDropDown()
        }
        binding.actvCategory.setOnClickListener {
            binding.actvCategory.showDropDown()
        }
    }

    // ── Assignee chips ────────────────────────────────────────────────────────
    // "Everyone" chip: full-width, 10dp corners; member chips: pill (999px radius).
    // Single-select: tapping a member deselects Everyone and all other member chips.
    // Tapping Everyone deselects all member chips.
    // updatingChips flag prevents listener re-entry during batch state changes.

    private fun setupAssigneeChips() {
        binding.chipGroupAssignees.removeAllViews()
        memberChips.clear()

        styleEveryoneChip(selected = true)
        binding.chipEveryone.isChecked = true
        binding.chipEveryone.setOnCheckedChangeListener { _, checked ->
            if (updatingChips) return@setOnCheckedChangeListener
            if (checked) {
                selectEveryone()
            } else {
                // Prevent uncheck unless a member chip is already selected
                if (selectedAssigneeId == "everyone") {
                    updatingChips = true
                    binding.chipEveryone.isChecked = true
                    updatingChips = false
                }
            }
        }

        val chipIconPx = (24 * resources.displayMetrics.density).toInt()

        members.forEachIndexed { idx, member ->
            val (bgColor, txtColor) = UserAvatarIdentity.chipColors(member, idx)
            val letter = UserAvatarIdentity.displayLetter(member)

            val chip = Chip(requireContext()).apply {
                text = member.name.split(" ").firstOrNull() ?: member.name
                isCheckable = true
                isChecked = false
                chipCornerRadius = 999f
                chipBackgroundColor = buildChipBgColors()
                setTextColor(buildChipTextColors())
                chipStrokeColor = buildChipStrokeColors()
                chipStrokeWidth = 1.5f
                isChipIconVisible = true
                chipIconSize = chipIconPx.toFloat()
                chipIcon = BitmapDrawable(resources, makeAvatarBitmap(letter, bgColor, txtColor))
                isCheckedIconVisible = false
                tag = member.userId

                setOnCheckedChangeListener { _, checked ->
                    if (updatingChips) return@setOnCheckedChangeListener
                    if (checked) {
                        selectMember(member.userId)
                        // Deselect all other member chips
                        updatingChips = true
                        memberChips.forEach { other ->
                            if (other.tag != member.userId) other.isChecked = false
                        }
                        updatingChips = false
                    } else {
                        // If no member chip is selected, revert to Everyone
                        if (memberChips.none { it.isChecked }) {
                            selectEveryone()
                        }
                    }
                }
            }
            memberChips.add(chip)
            binding.chipGroupAssignees.addView(chip)

            // Async photo load
            loadChipPhoto(chip, member.photoUrl, chipIconPx)
        }

        // Prefill assignee if editing
        editTask?.let { task ->
            if (task.assignedTo != "everyone" && task.assignedTo.isNotEmpty()) {
                val target = memberChips.firstOrNull { it.tag == task.assignedTo }
                    ?: memberChips.firstOrNull {
                        val m = members.firstOrNull { m -> m.userId == it.tag }
                        m?.name == task.assignedTo || m?.name?.split(" ")?.first() == task.assignedTo
                    }
                target?.let {
                    updatingChips = true
                    it.isChecked = true
                    selectedAssigneeId = task.assignedTo
                    binding.chipEveryone.isChecked = false
                    styleEveryoneChip(selected = false)
                    updatingChips = false
                }
            }
        }
    }

    private fun selectEveryone() {
        selectedAssigneeId = "everyone"
        updatingChips = true
        memberChips.forEach { it.isChecked = false }
        binding.chipEveryone.isChecked = true
        updatingChips = false
        styleEveryoneChip(selected = true)
    }

    private fun selectMember(userId: String) {
        selectedAssigneeId = userId
        updatingChips = true
        binding.chipEveryone.isChecked = false
        updatingChips = false
        styleEveryoneChip(selected = false)
    }

    private fun loadChipPhoto(chip: Chip, photoUrl: String?, chipIconPx: Int) {
        if (photoUrl.isNullOrEmpty()) return
        if (ImageUtils.isBase64Image(photoUrl)) {
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val bmp = withContext(Dispatchers.IO) { ImageUtils.base64ToBitmap(photoUrl) }
                    if (bmp != null && isAdded) {
                        val circular = withContext(Dispatchers.IO) { makeCircularBitmap(bmp, chipIconPx) }
                        chip.chipIcon = BitmapDrawable(resources, circular)
                    }
                } catch (_: Exception) {}
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
            } catch (_: Exception) {}
        }
    }

    private fun styleEveryoneChip(selected: Boolean) {
        if (selected) {
            binding.chipEveryone.chipBackgroundColor =
                ColorStateList.valueOf(Color.parseColor("#1FF48C25"))
            binding.chipEveryone.chipStrokeColor =
                ColorStateList.valueOf(Color.parseColor("#F48C25"))
            binding.chipEveryone.setTextColor(Color.parseColor("#F48C25"))
            binding.chipEveryone.chipIconTint =
                ColorStateList.valueOf(Color.parseColor("#F48C25"))
        } else {
            binding.chipEveryone.chipBackgroundColor =
                ColorStateList.valueOf(Color.parseColor("#F8F7F5"))
            binding.chipEveryone.chipStrokeColor =
                ColorStateList.valueOf(Color.parseColor("#E6E0DB"))
            binding.chipEveryone.setTextColor(Color.parseColor("#8A7560"))
            binding.chipEveryone.chipIconTint =
                ColorStateList.valueOf(Color.parseColor("#8A7560"))
        }
    }

    // ── Priority buttons ──────────────────────────────────────────────────────

    private fun setupPriorityButtons() {
        binding.prioLow.setOnClickListener { selectPriority("low") }
        binding.prioMedium.setOnClickListener { selectPriority("medium") }
        binding.prioHigh.setOnClickListener { selectPriority("high") }
        selectPriority("medium")
    }

    private fun selectPriority(key: String) {
        selectedPriority = key

        data class PrioStyle(
            val container: FrameLayout,
            val label: TextView,
            val k: String,
            val bg: Int,
            val accent: Int,
            val dotColor: Int
        )

        val styles = listOf(
            PrioStyle(
                binding.prioLow, binding.tvPrioLow, "low",
                Color.parseColor("#DCFCE7"), Color.parseColor("#16A34A"), Color.parseColor("#16A34A")
            ),
            PrioStyle(
                binding.prioMedium, binding.tvPrioMedium, "medium",
                Color.parseColor("#FEF3C7"), Color.parseColor("#D97706"), Color.parseColor("#D97706")
            ),
            PrioStyle(
                binding.prioHigh, binding.tvPrioHigh, "high",
                Color.parseColor("#FEF2F2"), Color.parseColor("#DC2626"), Color.parseColor("#DC2626")
            )
        )

        styles.forEach { ps ->
            val selected = ps.k == key
            ps.container.background = if (selected) {
                GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 9 * resources.displayMetrics.density
                    setColor(ps.bg)
                    setStroke((1.5 * resources.displayMetrics.density).toInt(), ps.accent)
                }
            } else {
                ContextCompat.getDrawable(requireContext(), R.drawable.bg_input_outline)
            }
            ps.label.setTextColor(if (selected) ps.accent else Color.parseColor("#8A7560"))
        }
    }

    // ── Due date picker ───────────────────────────────────────────────────────

    private fun setupDueDatePicker() {
        val sdf = SimpleDateFormat("EEE, MMM d yyyy", Locale.getDefault())
        val clickListener = View.OnClickListener {
            val cal = Calendar.getInstance().apply {
                selectedDueDate?.let { timeInMillis = it }
            }
            DatePickerDialog(
                ContextThemeWrapper(requireContext(), R.style.ThemeOverlay_Tripoo_DatePickerDialog),
                { _, y, m, d ->
                    cal.set(y, m, d)
                    selectedDueDate = cal.timeInMillis
                    binding.etDueDate.text = sdf.format(cal.time)
                },
                cal[Calendar.YEAR],
                cal[Calendar.MONTH],
                cal[Calendar.DAY_OF_MONTH]
            ).show()
        }
        binding.dateContainer.setOnClickListener(clickListener)
        binding.etDueDate.setOnClickListener(clickListener)
    }

    // ── Prefill for edit mode ─────────────────────────────────────────────────

    private fun prefillIfEdit() {
        val task = editTask ?: return

        binding.btnSaveTask.text = getString(R.string.save_changes)
        binding.etTitle.setText(task.title)

        // Category
        val categoryMap = mapOf(
            "general" to "General",
            "bookings" to "Bookings",
            "booking" to "Bookings",
            "packing" to "Packing",
            "documents" to "Documents",
            "other" to "Other"
        )
        val catKey = (task.category as? String ?: "general").lowercase()
        selectedCategory = if (catKey == "booking") "bookings" else catKey
        binding.actvCategory.setText(categoryMap[catKey] ?: "General", false)

        // Priority
        selectPriority(task.priority)

        // Due date
        task.dueDate?.let { ms ->
            selectedDueDate = ms
            val sdf = SimpleDateFormat("EEE, MMM d yyyy", Locale.getDefault())
            binding.etDueDate.text = sdf.format(java.util.Date(ms))
        }

        // Notes
        task.notes?.let { binding.etNotes.setText(it) }
    }

    // ── Submit ────────────────────────────────────────────────────────────────

    private fun submitTask() {
        if (tripId.isEmpty()) {
            Toast.makeText(requireContext(), "No trip selected", Toast.LENGTH_SHORT).show()
            return
        }

        val title = binding.etTitle.text.toString().trim()
        if (title.isEmpty()) {
            binding.etTitle.error = "Task name is required"
            return
        }

        val notes = binding.etNotes.text.toString().trim().ifEmpty { null }

        val existing = editTask
        if (existing != null) {
            val updated = existing.copy(
                title = title,
                category = selectedCategory,
                assignedTo = selectedAssigneeId,
                priority = selectedPriority,
                dueDate = selectedDueDate,
                notes = notes
            )
            TaskRepository().updateTask(tripId, existing.id, updated) { err ->
                if (err != null) {
                    Toast.makeText(requireContext(), "Failed: ${err.message}", Toast.LENGTH_SHORT).show()
                } else {
                    dismiss()
                }
            }
        } else {
            val task = Task(
                title = title,
                category = selectedCategory,
                assignedTo = selectedAssigneeId,
                priority = selectedPriority,
                dueDate = selectedDueDate,
                notes = notes,
                completed = false
            )
            TaskRepository().addTask(tripId, task) { err ->
                if (err != null) {
                    Toast.makeText(requireContext(), "Failed: ${err.message}", Toast.LENGTH_SHORT).show()
                } else {
                    dismiss()
                }
            }
        }
    }

    // ── Avatar helpers ────────────────────────────────────────────────────────

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

    private fun makeCircularBitmap(src: Bitmap, size: Int): Bitmap {
        val square = ImageUtils.cropToCenterSquare(src)
        val scaled = Bitmap.createScaledBitmap(square, size, size, true)
        val result = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = BitmapShader(scaled, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        return result
    }

    // ── Chip colour helpers ───────────────────────────────────────────────────

    private fun buildChipBgColors() = ColorStateList(
        arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
        intArrayOf(Color.parseColor("#1FF48C25"), Color.parseColor("#F8F7F5"))
    )

    private fun buildChipTextColors() = ColorStateList(
        arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
        intArrayOf(Color.parseColor("#F48C25"), Color.parseColor("#8A7560"))
    )

    private fun buildChipStrokeColors() = ColorStateList(
        arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
        intArrayOf(Color.parseColor("#F48C25"), Color.parseColor("#E6E0DB"))
    )

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
