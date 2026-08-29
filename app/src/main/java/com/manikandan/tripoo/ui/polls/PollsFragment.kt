package com.manikandan.tripoo.ui.polls

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
import com.manikandan.tripoo.data.model.Poll
import com.manikandan.tripoo.databinding.FragmentPollsBinding
import com.manikandan.tripoo.databinding.ItemPollBinding

class PollsFragment : Fragment() {
    private var _binding: FragmentPollsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: PollsViewModel by viewModels()
    private val adapter = PollAdapter { poll ->
        findNavController().navigate(
            R.id.action_polls_to_detail,
            Bundle().apply {
                putString("tripId", viewModel.tripId)
                putString("pollId", poll.id)
            }
        )
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPollsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnBack.setOnClickListener { findNavController().popBackStack() }
        binding.rvPolls.layoutManager = LinearLayoutManager(requireContext())
        binding.rvPolls.adapter = adapter
        binding.fabAddPoll.setOnClickListener {
            CreatePollBottomSheet().show(childFragmentManager, "create_poll")
        }
        childFragmentManager.setFragmentResultListener(CreatePollBottomSheet.REQUEST_KEY, viewLifecycleOwner) { _, b ->
            val q = b.getString("question").orEmpty()
            val opts = b.getStringArrayList("options").orEmpty()
            viewModel.create(q, opts)
        }
        viewModel.polls.observe(viewLifecycleOwner) { list ->
            adapter.submit(list)
            binding.tvEmptyPolls.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            binding.rvPolls.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
        }
        viewModel.error.observe(viewLifecycleOwner) { msg ->
            if (!msg.isNullOrBlank()) Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private class PollAdapter(private val onClick: (Poll) -> Unit) : RecyclerView.Adapter<PollAdapter.VH>() {
        private var items: List<Poll> = emptyList()
        fun submit(list: List<Poll>) {
            items = list
            notifyDataSetChanged()
        }

        inner class VH(val binding: ItemPollBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(ItemPollBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: VH, position: Int) {
            val p = items[position]
            holder.binding.tvPollQuestion.text = p.question
            val counts = IntArray(p.options.size)
            p.votes.values.forEach { idx -> if (idx in counts.indices) counts[idx]++ }
            val leadIdx = counts.indices.maxByOrNull { counts[it] } ?: -1
            val lead = if (leadIdx >= 0 && p.options.isNotEmpty()) p.options[leadIdx] else "No votes"
            val status = if (p.closed) "Closed" else "Open"
            holder.binding.tvPollMeta.text = "$status · $lead · ${p.votes.size} votes"
            holder.itemView.setOnClickListener { onClick(p) }
        }

        override fun getItemCount(): Int = items.size
    }
}
