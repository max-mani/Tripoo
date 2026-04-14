package com.manikandan.tripoo.ui.expenses

import android.content.Context
import android.content.res.ColorStateList
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.manikandan.tripoo.R
import com.manikandan.tripoo.data.model.Expense
import com.manikandan.tripoo.databinding.ItemExpenseBinding
import com.manikandan.tripoo.databinding.ItemExpenseDateHeaderBinding

class ExpenseAdapter(
    private val currentUserId: String,
    private val memberNames: Map<String, String>,
    canMarkSettled: Boolean,
    private val onClick: (Expense) -> Unit,
    private val onEdit: (Expense) -> Unit,
    private val onDelete: (Expense) -> Unit,
    private val onSettle: (Expense) -> Unit
) : ListAdapter<ExpenseAdapter.ExpenseListItem, RecyclerView.ViewHolder>(DIFF) {

    var canMarkSettled: Boolean = canMarkSettled
        private set

    fun setCanMarkSettled(value: Boolean) {
        if (canMarkSettled != value) {
            canMarkSettled = value
            notifyDataSetChanged()
        }
    }

    sealed class ExpenseListItem {
        data class DateHeader(val label: String) : ExpenseListItem()
        data class ExpenseRow(val expense: Expense) : ExpenseListItem()
    }

    inner class HeaderVH(val binding: ItemExpenseDateHeaderBinding) :
        RecyclerView.ViewHolder(binding.root)

    inner class RowVH(val binding: ItemExpenseBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is ExpenseListItem.DateHeader -> 0
        is ExpenseListItem.ExpenseRow -> 1
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == 0) {
            HeaderVH(
                ItemExpenseDateHeaderBinding.inflate(
                    inflater,
                    parent,
                    false
                )
            )
        } else {
            RowVH(
                ItemExpenseBinding.inflate(
                    inflater,
                    parent,
                    false
                )
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val ctx = holder.itemView.context
        when (val item = getItem(position)) {
            is ExpenseListItem.DateHeader -> {
                (holder as HeaderVH).binding.tvDate.text = item.label
            }

            is ExpenseListItem.ExpenseRow -> {
                val binding = (holder as RowVH).binding
                val e = item.expense

                binding.tvExpenseName.text = e.title

                val payerName = if (e.paidBy == currentUserId) {
                    "You"
                } else {
                    memberNames[e.paidBy] ?: "Someone"
                }

                val splitLabel = if (e.splitWith.size >= memberNames.size && memberNames.isNotEmpty()) {
                    "Everyone"
                } else {
                    e.splitWith.joinToString { memberId ->
                        if (memberId == currentUserId) "You" else (memberNames[memberId] ?: "?")
                    }
                }

                binding.tvExpenseSub.text = "Paid by $payerName · Split with $splitLabel"

                binding.tvExpenseTotal.text = "₹" + String.format("%.2f", e.amount)

                val splitCount = e.splitWith.size.coerceAtLeast(1)
                val share = e.amount / splitCount

                if (e.paidBy != currentUserId && e.splitWith.contains(currentUserId)) {
                    binding.tvExpenseShare.text =
                        "Your share: ₹" + String.format("%.2f", share)
                    binding.tvExpenseShare.setTextColor(
                        ContextCompat.getColor(
                            ctx,
                            R.color.tripoo_blue
                        )
                    )
                } else if (e.paidBy == currentUserId) {
                    val owed = e.splitWith.filter { it != currentUserId }.size * share
                    if (owed > 0) {
                        binding.tvExpenseShare.text =
                            "You're owed: ₹" + String.format("%.2f", owed)
                        binding.tvExpenseShare.setTextColor(
                            ContextCompat.getColor(
                                ctx,
                                R.color.tripoo_green
                            )
                        )
                    } else {
                        binding.tvExpenseShare.text = ""
                    }
                } else {
                    binding.tvExpenseShare.text = ""
                }

                val (iconRes, bgColor, iconColor) = categoryStyle(ctx, e.category)
                binding.ivCategory.setImageResource(iconRes)
                binding.ivCategory.imageTintList = ColorStateList.valueOf(iconColor)
                binding.flCategoryIcon.backgroundTintList = ColorStateList.valueOf(bgColor)

                binding.root.setOnClickListener { onClick(e) }
                binding.btnExpenseMore.setOnClickListener {
                    val wrapper = ContextThemeWrapper(ctx, androidx.appcompat.R.style.ThemeOverlay_AppCompat_Light)
                    val popup = android.widget.PopupMenu(wrapper, binding.btnExpenseMore)
                    popup.menu.add(0, 1, 0, "Edit")
                    popup.menu.add(0, 2, 1, "Delete")
                    if (canMarkSettled && !e.settled) {
                        popup.menu.add(0, 3, 2, "Mark as settled")
                    }
                    popup.setOnMenuItemClickListener { menuItem ->
                        when (menuItem.itemId) {
                            1 -> onEdit(e)
                            2 -> onDelete(e)
                            3 -> onSettle(e)
                        }
                        true
                    }
                    popup.show()
                }
            }
        }
    }

    private fun categoryStyle(ctx: Context, cat: String): Triple<Int, Int, Int> {
        return when (cat) {
            "accommodation" -> Triple(
                R.drawable.ic_home,
                ContextCompat.getColor(ctx, R.color.tripoo_blue_bg),
                ContextCompat.getColor(ctx, R.color.tripoo_blue)
            )

            "food" -> Triple(
                R.drawable.ic_restaurant,
                0xFFFFEDD5.toInt(),
                0xFFEA580C.toInt()
            )

            "transport" -> Triple(
                R.drawable.ic_car,
                ContextCompat.getColor(ctx, R.color.tripoo_purple_bg),
                ContextCompat.getColor(ctx, R.color.tripoo_purple_text)
            )

            "drinks" -> Triple(
                R.drawable.ic_local_bar,
                ContextCompat.getColor(ctx, R.color.tripoo_green_bg),
                ContextCompat.getColor(ctx, R.color.tripoo_green)
            )

            "activities" -> Triple(
                R.drawable.ic_surfing,
                ContextCompat.getColor(ctx, R.color.tripoo_yellow_bg),
                ContextCompat.getColor(ctx, R.color.tripoo_yellow_text)
            )

            else -> Triple(
                R.drawable.ic_more_horiz,
                ContextCompat.getColor(ctx, R.color.tripoo_status_past_bg),
                ContextCompat.getColor(ctx, R.color.tripoo_status_past_text)
            )
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<ExpenseListItem>() {
            override fun areItemsTheSame(a: ExpenseListItem, b: ExpenseListItem): Boolean {
                return if (a is ExpenseListItem.ExpenseRow && b is ExpenseListItem.ExpenseRow) {
                    a.expense.id == b.expense.id
                } else if (a is ExpenseListItem.DateHeader && b is ExpenseListItem.DateHeader) {
                    a.label == b.label
                } else {
                    false
                }
            }

            override fun areContentsTheSame(a: ExpenseListItem, b: ExpenseListItem): Boolean = a == b
        }
    }
}

