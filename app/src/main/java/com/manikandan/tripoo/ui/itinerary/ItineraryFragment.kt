package com.manikandan.tripoo.ui.itinerary

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.manikandan.tripoo.R
import com.manikandan.tripoo.data.model.ItineraryStop
import com.manikandan.tripoo.databinding.FragmentItineraryBinding
import com.manikandan.tripoo.databinding.ItemItineraryStopBinding
import android.view.ContextThemeWrapper
import android.widget.PopupMenu
import android.widget.TextView

class ItineraryFragment : Fragment() {
    private var _binding: FragmentItineraryBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ItineraryViewModel by viewModels()
    private val adapter = StopAdapter(
        onEdit = { stop ->
            AddItineraryStopBottomSheet.newInstance(stop).show(childFragmentManager, "add_stop")
        },
        onDelete = { stop -> viewModel.deleteStop(stop.id) }
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentItineraryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnBack.setOnClickListener { findNavController().popBackStack() }
        binding.rvStops.layoutManager = LinearLayoutManager(requireContext())
        binding.rvStops.adapter = adapter
        binding.fabAddStop.setOnClickListener {
            if (viewModel.selectedDay() == null) {
                Toast.makeText(requireContext(), "No day yet", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            AddItineraryStopBottomSheet.newInstance(null).show(childFragmentManager, "add_stop")
        }
        childFragmentManager.setFragmentResultListener(AddItineraryStopBottomSheet.REQUEST_KEY, viewLifecycleOwner) { _, bundle ->
            val stop = ItineraryStop(
                id = bundle.getString("id").orEmpty(),
                time = bundle.getString("time").orEmpty(),
                title = bundle.getString("title").orEmpty(),
                location = bundle.getString("location").orEmpty(),
                notes = bundle.getString("notes").orEmpty()
            )
            if (stop.id.isBlank()) viewModel.addStop(stop) else viewModel.updateStop(stop)
        }
        viewModel.days.observe(viewLifecycleOwner) { days ->
            renderTabs(days.size)
            val day = viewModel.selectedDay()
            val stops = day?.stops.orEmpty()
            adapter.submit(stops)
            binding.tvEmptyStops.visibility = if (stops.isEmpty()) View.VISIBLE else View.GONE
            binding.rvStops.visibility = if (stops.isEmpty()) View.GONE else View.VISIBLE
        }
        viewModel.selectedIndex.observe(viewLifecycleOwner) {
            val days = viewModel.days.value.orEmpty()
            renderTabs(days.size)
            val stops = viewModel.selectedDay()?.stops.orEmpty()
            adapter.submit(stops)
            binding.tvEmptyStops.visibility = if (stops.isEmpty()) View.VISIBLE else View.GONE
            binding.rvStops.visibility = if (stops.isEmpty()) View.GONE else View.VISIBLE
        }
        viewModel.error.observe(viewLifecycleOwner) { msg ->
            if (!msg.isNullOrBlank()) Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun renderTabs(count: Int) {
        binding.llDayTabs.removeAllViews()
        val selected = viewModel.selectedIndex.value ?: 0
        for (i in 0 until count) {
            val tab = layoutInflater.inflate(R.layout.item_itinerary_day_tab, binding.llDayTabs, false) as TextView
            tab.text = "Day ${i + 1}"
            if (i == selected) {
                tab.setBackgroundResource(R.drawable.bg_chip_on)
                tab.setTextColor(resources.getColor(android.R.color.white, null))
            } else {
                tab.setBackgroundResource(R.drawable.bg_chip_off)
                tab.setTextColor(resources.getColor(R.color.tripoo_text_secondary, null))
            }
            tab.setOnClickListener { viewModel.selectedIndex.value = i }
            binding.llDayTabs.addView(tab)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private class StopAdapter(
        private val onEdit: (ItineraryStop) -> Unit,
        private val onDelete: (ItineraryStop) -> Unit
    ) : RecyclerView.Adapter<StopAdapter.VH>() {
        private var items: List<ItineraryStop> = emptyList()
        fun submit(list: List<ItineraryStop>) {
            items = list
            notifyDataSetChanged()
        }

        inner class VH(val binding: ItemItineraryStopBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(ItemItineraryStopBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: VH, position: Int) {
            val s = items[position]
            holder.binding.tvStopTime.text = s.time.ifBlank { "—" }
            holder.binding.tvStopTitle.text = s.title
            holder.binding.tvStopLocation.visibility = if (s.location.isBlank()) View.GONE else View.VISIBLE
            holder.binding.tvStopLocation.text = s.location
            holder.binding.tvStopNotes.visibility = if (s.notes.isBlank()) View.GONE else View.VISIBLE
            holder.binding.tvStopNotes.text = s.notes
            holder.binding.btnStopMore.setOnClickListener { anchor ->
                val wrapper = ContextThemeWrapper(anchor.context, androidx.appcompat.R.style.ThemeOverlay_AppCompat_Light)
                val popup = PopupMenu(wrapper, anchor)
                popup.menu.add(0, 1, 0, "Edit")
                popup.menu.add(0, 2, 1, "Delete")
                popup.setOnMenuItemClickListener {
                    when (it.itemId) {
                        1 -> onEdit(s)
                        2 -> onDelete(s)
                    }
                    true
                }
                popup.show()
            }
        }

        override fun getItemCount(): Int = items.size
    }
}
