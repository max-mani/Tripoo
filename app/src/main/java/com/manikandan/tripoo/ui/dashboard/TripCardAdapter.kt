package com.manikandan.tripoo.ui.dashboard

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.manikandan.tripoo.R
import com.manikandan.tripoo.databinding.ItemTripCardBinding
import com.manikandan.tripoo.data.model.TripWithMeta
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class TripCardAdapter(
    private val onClick: (TripWithMeta) -> Unit
) : ListAdapter<TripWithMeta, TripCardAdapter.VH>(DIFF) {

    inner class VH(val binding: ItemTripCardBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemTripCardBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        val trip = item.trip
        val ctx = holder.binding.root.context
        val sdf = SimpleDateFormat("MMM d", Locale.getDefault())

        holder.binding.tvTripName.text = trip.name.ifEmpty { trip.destination }.ifEmpty {
            if (trip.isOuting()) "Outing" else "Trip"
        }
        holder.binding.tvDestination.text = trip.destination.ifEmpty {
            if (trip.isOuting()) trip.description.ifEmpty { "Outing" } else "—"
        }
        holder.binding.tvDates.text = "${sdf.format(Date(trip.startDate))} – ${sdf.format(Date(trip.endDate))}"
        holder.binding.tvMemberCount.text = "${item.memberCount} members"

        val (accentColor, badgeBg, badgeText) = when (trip.status) {
            "active" -> Triple(
                ContextCompat.getColor(ctx, R.color.tripoo_status_active_bar),
                ContextCompat.getColor(ctx, R.color.tripoo_status_active_bg),
                ContextCompat.getColor(ctx, R.color.tripoo_status_active_text)
            )
            "upcoming" -> Triple(
                ContextCompat.getColor(ctx, R.color.tripoo_status_upcoming_bar),
                ContextCompat.getColor(ctx, R.color.tripoo_status_upcoming_bg),
                ContextCompat.getColor(ctx, R.color.tripoo_status_upcoming_text)
            )
            else -> Triple(
                ContextCompat.getColor(ctx, R.color.tripoo_status_past_bar),
                ContextCompat.getColor(ctx, R.color.tripoo_status_past_bg),
                ContextCompat.getColor(ctx, R.color.tripoo_status_past_text)
            )
        }
        holder.binding.viewAccent.setBackgroundColor(accentColor)
        holder.binding.tvStatus.text = trip.status.replaceFirstChar { it.uppercase() }
        holder.binding.tvStatus.background = GradientDrawable().apply {
            setColor(badgeBg)
            cornerRadius = 5f * ctx.resources.displayMetrics.density
        }
        holder.binding.tvStatus.setTextColor(badgeText)

        if (trip.isOuting()) {
            holder.binding.tvTypeBadge.visibility = View.VISIBLE
        } else {
            holder.binding.tvTypeBadge.visibility = View.GONE
        }

        if (trip.status == "active" && trip.budget > 0) {
            holder.binding.layoutBudget.visibility = View.VISIBLE
            holder.binding.layoutCountdown.visibility = View.GONE
            holder.binding.progressBudget.max = 100
            val spent = item.totalSpent.coerceAtLeast(0.0)
            val progress = ((spent / trip.budget) * 100.0).toInt().coerceIn(0, 100)
            val remainingPct = (100 - progress).coerceIn(0, 100)
            holder.binding.progressBudget.progress = progress
            holder.binding.tvBudgetText.text = "₹${spent.toInt()} / ₹${trip.budget.toInt()} budget"
            holder.binding.tvBudgetPercent.text = "$remainingPct% left"
        } else if (trip.status == "upcoming") {
            holder.binding.layoutBudget.visibility = View.GONE
            holder.binding.layoutCountdown.visibility = View.VISIBLE
            val daysLeft = TimeUnit.MILLISECONDS.toDays(trip.startDate - System.currentTimeMillis()).coerceAtLeast(0)
            holder.binding.tvCountdown.text = "Starts in $daysLeft days"
        } else {
            holder.binding.layoutBudget.visibility = View.GONE
            holder.binding.layoutCountdown.visibility = View.GONE
        }

        holder.itemView.alpha = if (trip.status == "past") 0.6f else 1f
        holder.itemView.setOnClickListener { onClick(item) }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<TripWithMeta>() {
            override fun areItemsTheSame(a: TripWithMeta, b: TripWithMeta) = a.trip.id == b.trip.id
            override fun areContentsTheSame(a: TripWithMeta, b: TripWithMeta) = a == b
        }
    }
}
