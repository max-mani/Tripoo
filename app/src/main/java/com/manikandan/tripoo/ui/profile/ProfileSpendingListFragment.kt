package com.manikandan.tripoo.ui.profile

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.manikandan.tripoo.R
import com.manikandan.tripoo.databinding.FragmentProfileSpendingListBinding
import com.manikandan.tripoo.databinding.ItemProfileSpendingRowBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ProfileSpendingListFragment : Fragment() {

    private var _binding: FragmentProfileSpendingListBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: ProfileSpendingListViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProfileSpendingListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(this)[ProfileSpendingListViewModel::class.java]

        val adapter = SpendingAdapter(viewModel)
        binding.rvSpending.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSpending.adapter = adapter

        binding.btnBack.setOnClickListener { findNavController().navigateUp() }

        binding.swipeRefresh.setOnRefreshListener { viewModel.load() }

        viewModel.rows.observe(viewLifecycleOwner) { adapter.submit(it) }
        viewModel.error.observe(viewLifecycleOwner) { msg ->
            msg?.let { Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show() }
        }
        viewModel.loading.observe(viewLifecycleOwner) { loading ->
            val rowsEmpty = (viewModel.rows.value ?: emptyList()).isEmpty()
            binding.progressLoading.visibility =
                if (loading == true && rowsEmpty) View.VISIBLE else View.GONE
            if (loading != true) binding.swipeRefresh.isRefreshing = false
        }
        viewModel.load()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private class SpendingAdapter(private val vm: ProfileSpendingListViewModel) : RecyclerView.Adapter<SpendingAdapter.VH>() {
        private var items: List<TripSpendingRow> = emptyList()
        private val sdf = SimpleDateFormat("MMM d", Locale.getDefault())

        fun submit(list: List<TripSpendingRow>?) {
            items = list ?: emptyList()
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val b = ItemProfileSpendingRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return VH(b)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val row = items[position]
            val ctx = holder.binding.root.context

            holder.binding.tvTripName.text = row.tripName
            holder.binding.tvDestination.text = row.destination.ifEmpty { "—" }
            holder.binding.tvDates.text = "${sdf.format(Date(row.startDate))} – ${sdf.format(Date(row.endDate))}"
            holder.binding.tvMemberCount.text = "${row.memberCount} members"
            holder.binding.tvExpenseTotal.text =
                "Total trip expenses: ${vm.formatTotal(row.totalExpenses)}"

            val (accentColor, badgeBg, badgeText) = when (row.status) {
                "active" -> Triple(
                    ContextCompat.getColor(ctx, R.color.tripoo_status_active_bar),
                    ContextCompat.getColor(ctx, R.color.tripoo_status_active_bg),
                    ContextCompat.getColor(ctx, R.color.tripoo_status_active_text),
                )
                "upcoming" -> Triple(
                    ContextCompat.getColor(ctx, R.color.tripoo_status_upcoming_bar),
                    ContextCompat.getColor(ctx, R.color.tripoo_status_upcoming_bg),
                    ContextCompat.getColor(ctx, R.color.tripoo_status_upcoming_text),
                )
                else -> Triple(
                    ContextCompat.getColor(ctx, R.color.tripoo_status_past_bar),
                    ContextCompat.getColor(ctx, R.color.tripoo_status_past_bg),
                    ContextCompat.getColor(ctx, R.color.tripoo_status_past_text),
                )
            }
            holder.binding.viewAccent.setBackgroundColor(accentColor)
            holder.binding.tvStatus.text = row.status.replaceFirstChar { it.uppercase() }
            holder.binding.tvStatus.background = GradientDrawable().apply {
                setColor(badgeBg)
                cornerRadius = 5f * ctx.resources.displayMetrics.density
            }
            holder.binding.tvStatus.setTextColor(badgeText)

            holder.itemView.alpha = if (row.status == "past") 0.6f else 1f
        }

        override fun getItemCount(): Int = items.size

        class VH(val binding: ItemProfileSpendingRowBinding) : RecyclerView.ViewHolder(binding.root)
    }
}
