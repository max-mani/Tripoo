package com.manikandan.tripoo.ui.tasks

import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.manikandan.tripoo.R
import com.manikandan.tripoo.data.model.Task
import com.manikandan.tripoo.data.model.TripMember
import com.manikandan.tripoo.databinding.ItemTaskBinding
import com.manikandan.tripoo.databinding.ItemTaskHeaderBinding

class TaskAdapter(
    private val membersById: Map<String, TripMember>,
    private val onToggle: (Task) -> Unit,
    private val onEdit: (Task) -> Unit = {},
    private val onDelete: (Task) -> Unit = {}
) : ListAdapter<TaskAdapter.TaskItem, RecyclerView.ViewHolder>(DIFF) {

    sealed class TaskItem {
        data class Header(val category: String, val count: Int) : TaskItem()
        data class TaskRow(
            val task: Task,
            val isFirst: Boolean = false,
            val isLast: Boolean = false
        ) : TaskItem()
    }

    companion object {
        const val TYPE_HEADER = 0
        const val TYPE_ROW = 1

        // Must match values stored by AddTaskBottomSheet; normalize legacy "booking" → "bookings"
        private val CATEGORY_ORDER = listOf("general", "bookings", "packing", "documents", "other")

        private fun normalizeCategoryKey(cat: String?): String {
            val c = (cat as? String ?: "general").lowercase()
            return if (c == "booking") "bookings" else c
        }

        val DIFF = object : DiffUtil.ItemCallback<TaskItem>() {
            override fun areItemsTheSame(a: TaskItem, b: TaskItem) = when {
                a is TaskItem.Header && b is TaskItem.Header -> a.category == b.category
                a is TaskItem.TaskRow && b is TaskItem.TaskRow -> a.task.id == b.task.id
                else -> false
            }
            override fun areContentsTheSame(a: TaskItem, b: TaskItem) = a == b
        }

        @JvmStatic
        fun buildItems(tasks: List<Task>): List<TaskItem> {
            return CATEGORY_ORDER.flatMap { cat ->
                // Safe cast required: Firestore sets Kotlin non-null fields to null via reflection.
                // Elvis on a non-null Kotlin type is optimized away by the compiler; `as? String` forces a nullable cast.
                val catTasks = tasks.filter { normalizeCategoryKey(it.category as? String) == cat }
                if (catTasks.isEmpty()) return@flatMap emptyList()
                val items = mutableListOf<TaskItem>(TaskItem.Header(cat, catTasks.size))
                catTasks.forEachIndexed { i, task ->
                    items.add(
                        TaskItem.TaskRow(
                            task = task,
                            isFirst = i == 0,
                            isLast = i == catTasks.lastIndex
                        )
                    )
                }
                items
            }
        }

        private fun categoryIcon(cat: String) = when (cat.lowercase()) {
            "bookings", "booking" -> R.drawable.ic_confirmation
            "packing" -> R.drawable.ic_luggage
            "documents" -> R.drawable.ic_description
            "other" -> R.drawable.ic_more_horiz
            else -> R.drawable.ic_list
        }

        private fun categoryLabel(cat: String) = when (cat.lowercase()) {
            "bookings", "booking" -> "Bookings"
            "packing" -> "Packing"
            "documents" -> "Documents"
            "other" -> "Other"
            else -> "General"
        }
    }

    override fun getItemViewType(pos: Int) =
        if (getItem(pos) is TaskItem.Header) TYPE_HEADER else TYPE_ROW

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER)
            HeaderVH(ItemTaskHeaderBinding.inflate(inflater, parent, false))
        else
            RowVH(ItemTaskBinding.inflate(inflater, parent, false))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, pos: Int) {
        when (val item = getItem(pos)) {
            is TaskItem.Header -> (holder as HeaderVH).bind(item)
            is TaskItem.TaskRow -> (holder as RowVH).bind(item)
        }
    }

    inner class HeaderVH(private val b: ItemTaskHeaderBinding) :
        RecyclerView.ViewHolder(b.root) {
        fun bind(item: TaskItem.Header) {
            b.tvCategoryName.text = categoryLabel(item.category)
            b.tvTaskCount.text = "${item.count} Tasks"
            b.ivCategoryIcon.setImageResource(categoryIcon(item.category))
            b.ivCategoryIcon.setColorFilter(Color.parseColor("#F48C25"))
        }
    }

    inner class RowVH(private val b: ItemTaskBinding) :
        RecyclerView.ViewHolder(b.root) {

        fun bind(item: TaskItem.TaskRow) {
            val task = item.task
            val ctx = b.root.context
            val radius = 12f * ctx.resources.displayMetrics.density

            // Card-style group background
            val bg = GradientDrawable().apply {
                setColor(Color.WHITE)
                setStroke(
                    (1 * ctx.resources.displayMetrics.density).toInt(),
                    Color.parseColor("#14F48C25")
                )
                val tl = if (item.isFirst) radius else 0f
                val tr = if (item.isFirst) radius else 0f
                val br = if (item.isLast) radius else 0f
                val bl = if (item.isLast) radius else 0f
                setCornerRadii(floatArrayOf(tl, tl, tr, tr, br, br, bl, bl))
            }
            b.root.background = bg

            // Horizontal margins + bottom margin for last row
            val lp = b.root.layoutParams as? ViewGroup.MarginLayoutParams
                ?: ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            val hMargin = (16 * ctx.resources.displayMetrics.density).toInt()
            val bMargin = if (item.isLast) (4 * ctx.resources.displayMetrics.density).toInt() else 0
            lp.marginStart = hMargin
            lp.marginEnd = hMargin
            lp.bottomMargin = bMargin
            b.root.layoutParams = lp

            // Title
            b.tvTaskTitle.text = task.title as? String ?: ""

            // Assignee text
            val rawAssigned = task.assignedTo as? String ?: "everyone"
            val assigneeName = when {
                rawAssigned.equals("everyone", ignoreCase = true) -> "Everyone"
                rawAssigned.isEmpty() -> "Everyone"
                membersById.containsKey(rawAssigned) -> membersById[rawAssigned]!!.name
                else -> rawAssigned
            }
            b.tvAssigned.text = "Assigned to $assigneeName"

            val noteText = (task.notes as? String)?.trim().orEmpty()
            if (noteText.isNotEmpty()) {
                b.tvTaskNotes.visibility = View.VISIBLE
                b.tvTaskNotes.text = noteText
            } else {
                b.tvTaskNotes.visibility = View.GONE
                b.tvTaskNotes.text = ""
            }

            // Completed state
            val checked = task.completed as? Boolean ?: false

            // Checkbox appearance
            b.ivCheck.visibility = if (checked) View.VISIBLE else View.GONE
            b.flCheckbox.background = ContextCompat.getDrawable(
                ctx,
                if (checked) R.drawable.bg_task_checkbox_checked else R.drawable.bg_task_checkbox
            )

            // Title strikethrough + color
            b.tvTaskTitle.apply {
                paintFlags = if (checked)
                    paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                else
                    paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                setTextColor(
                    if (checked) ContextCompat.getColor(ctx, R.color.tripoo_text_hint)
                    else ContextCompat.getColor(ctx, R.color.tripoo_text_primary)
                )
            }

            // Assignee line strikethrough + color
            b.tvAssigned.apply {
                paintFlags = if (checked)
                    paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                else
                    paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                setTextColor(
                    if (checked) ContextCompat.getColor(ctx, R.color.tripoo_text_hint)
                    else ContextCompat.getColor(ctx, R.color.tripoo_text_secondary)
                )
            }

            b.tvTaskNotes.apply {
                if (visibility == View.VISIBLE) {
                    paintFlags = if (checked)
                        paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                    else
                        paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                    setTextColor(
                        if (checked) ContextCompat.getColor(ctx, R.color.tripoo_text_hint)
                        else ContextCompat.getColor(ctx, R.color.tripoo_text_secondary)
                    )
                }
            }

            // List rows: show assignee text only (no avatar / icon badge in the row).
            b.flAssigneeBadge.visibility = View.GONE

            // Divider: hide on last row
            b.divider.visibility = if (item.isLast) View.GONE else View.VISIBLE

            // Toggle on checkbox tap
            b.flCheckbox.setOnClickListener { onToggle(task) }

            // More menu: Edit / Delete
            b.btnTaskMore.setOnClickListener { anchor ->
                val wrapper = ContextThemeWrapper(ctx, androidx.appcompat.R.style.ThemeOverlay_AppCompat_Light)
                val popup = PopupMenu(wrapper, anchor)
                popup.menu.add(0, 1, 0, "Edit")
                popup.menu.add(0, 2, 1, "Delete")
                popup.setOnMenuItemClickListener { menuItem ->
                    when (menuItem.itemId) {
                        1 -> onEdit(task)
                        2 -> onDelete(task)
                    }
                    true
                }
                popup.show()
            }
        }
    }
}
