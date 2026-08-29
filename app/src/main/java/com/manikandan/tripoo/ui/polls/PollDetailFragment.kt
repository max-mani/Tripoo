package com.manikandan.tripoo.ui.polls

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.firebase.auth.FirebaseAuth
import com.manikandan.tripoo.R
import com.manikandan.tripoo.data.model.Poll
import com.manikandan.tripoo.databinding.FragmentPollDetailBinding

class PollDetailFragment : Fragment() {
    private var _binding: FragmentPollDetailBinding? = null
    private val binding get() = _binding!!
    private val viewModel: PollsViewModel by viewModels()
    private val pollId: String by lazy { arguments?.getString("pollId").orEmpty() }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPollDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnBack.setOnClickListener { findNavController().popBackStack() }
        viewModel.polls.observe(viewLifecycleOwner) { list ->
            val poll = list.firstOrNull { it.id == pollId } ?: return@observe
            render(poll)
        }
        viewModel.isLeader.observe(viewLifecycleOwner) {
            val poll = viewModel.polls.value?.firstOrNull { it.id == pollId }
            if (poll != null) render(poll)
        }
        viewModel.error.observe(viewLifecycleOwner) { msg ->
            if (!msg.isNullOrBlank()) Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun render(poll: Poll) {
        binding.tvQuestion.text = poll.question
        val me = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        val myVote = poll.votes[me]
        val total = poll.votes.size.coerceAtLeast(1)
        val counts = IntArray(poll.options.size)
        poll.votes.values.forEach { idx -> if (idx in counts.indices) counts[idx]++ }
        binding.llOptions.removeAllViews()
        poll.options.forEachIndexed { index, label ->
            val wrap = layoutInflater.inflate(R.layout.item_poll, binding.llOptions, false)
            wrap.findViewById<TextView>(R.id.tvPollQuestion).text = label
            val share = if (poll.votes.isEmpty()) 0 else (counts.getOrElse(index) { 0 } * 100 / total)
            val mine = myVote == index
            wrap.findViewById<TextView>(R.id.tvPollMeta).text =
                "${counts.getOrElse(index) { 0 }} votes · $share%" + if (mine) " · your pick" else ""
            val bar = wrap.findViewById<ProgressBar>(R.id.progressVotes)
            bar.visibility = View.VISIBLE
            bar.progress = share
            wrap.isClickable = !poll.closed
            wrap.setOnClickListener {
                if (poll.closed) {
                    Toast.makeText(requireContext(), "This poll is closed", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                viewModel.vote(poll.id, index)
            }
            if (mine) {
                wrap.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.tripoo_orange_12))
            }
            binding.llOptions.addView(wrap)
        }
        val showClose = viewModel.isLeader.value == true && !poll.closed
        binding.btnClosePoll.visibility = if (showClose) View.VISIBLE else View.GONE
        binding.btnClosePoll.setOnClickListener { viewModel.close(poll.id) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
