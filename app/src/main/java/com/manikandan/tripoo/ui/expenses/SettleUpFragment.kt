package com.manikandan.tripoo.ui.expenses

import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.manikandan.tripoo.R
import com.manikandan.tripoo.data.model.Settlement
import com.manikandan.tripoo.databinding.FragmentSettleUpBinding
import com.manikandan.tripoo.databinding.ItemMemberNetBinding
import com.manikandan.tripoo.databinding.ItemSettlementHistoryBinding
import com.manikandan.tripoo.databinding.ItemSuggestedPaymentBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettleUpFragment : Fragment() {

    private var _binding: FragmentSettleUpBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ExpensesViewModel by viewModels({ requireParentFragment() })

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettleUpBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.netBalances.observe(viewLifecycleOwner) { renderNets(it) }
        viewModel.mySuggestedPayments.observe(viewLifecycleOwner) { renderSuggested(it) }
        viewModel.settlements.observe(viewLifecycleOwner) { renderHistory(it) }
        viewModel.members.observe(viewLifecycleOwner) {
            renderNets(viewModel.netBalances.value.orEmpty())
            renderSuggested(viewModel.mySuggestedPayments.value.orEmpty())
            renderHistory(viewModel.settlements.value.orEmpty())
        }
    }

    private fun renderNets(nets: Map<String, Double>) {
        if (_binding == null) return
        val uid = viewModel.currentUserId
        val myNet = nets[uid] ?: 0.0
        val green = ContextCompat.getColor(requireContext(), R.color.tripoo_green)
        val red = ContextCompat.getColor(requireContext(), R.color.tripoo_red)
        val primary = ContextCompat.getColor(requireContext(), R.color.tripoo_text_primary)
        when {
            SettlementCalculator.isZero(myNet) -> {
                binding.tvYourBalance.text = "You're all settled"
                binding.tvYourBalance.setTextColor(primary)
            }
            myNet > 0 -> {
                binding.tvYourBalance.text = "You are owed ₹${fmt(myNet)} overall"
                binding.tvYourBalance.setTextColor(green)
            }
            else -> {
                binding.tvYourBalance.text = "You owe ₹${fmt(-myNet)} overall"
                binding.tvYourBalance.setTextColor(red)
            }
        }

        binding.llEveryone.removeAllViews()
        val members = viewModel.members.value.orEmpty()
        val ordered = if (members.isNotEmpty()) {
            members.map { it.userId to (nets[it.userId] ?: 0.0) }
        } else {
            nets.toList()
        }
        ordered.forEach { (id, net) ->
            val row = ItemMemberNetBinding.inflate(layoutInflater, binding.llEveryone, false)
            row.tvMemberName.text = viewModel.displayName(id)
            row.tvMemberNet.text = when {
                SettlementCalculator.isZero(net) -> "₹0"
                net > 0 -> "+₹${fmt(net)}"
                else -> "−₹${fmt(-net)}"
            }
            row.tvMemberNet.setTextColor(
                when {
                    SettlementCalculator.isZero(net) -> primary
                    net > 0 -> green
                    else -> red
                }
            )
            binding.llEveryone.addView(row.root)
        }
    }

    private fun renderSuggested(payments: List<SettlementCalculator.SuggestedPayment>) {
        if (_binding == null) return
        binding.llSuggested.removeAllViews()
        binding.tvSuggestedEmpty.visibility = if (payments.isEmpty()) View.VISIBLE else View.GONE
        val me = viewModel.currentUserId
        payments.forEach { pay ->
            val row = ItemSuggestedPaymentBinding.inflate(layoutInflater, binding.llSuggested, false)
            val iPay = pay.fromUserId == me
            val other = if (iPay) pay.toUserId else pay.fromUserId
            val otherName = viewModel.displayName(other)
            row.tvPaymentLine.text = if (iPay) {
                "You → $otherName · ₹${fmt(pay.amount)}"
            } else {
                "$otherName → You · ₹${fmt(pay.amount)}"
            }
            row.btnMark.text = if (iPay) "Mark as paid" else "Mark as received"
            row.btnMark.setOnClickListener { confirmSettlement(pay, iPay) }
            binding.llSuggested.addView(row.root)
        }
    }

    private fun renderHistory(list: List<Settlement>) {
        if (_binding == null) return
        binding.llHistory.removeAllViews()
        binding.tvHistoryEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        val sdf = SimpleDateFormat("d MMM", Locale.getDefault())
        list.forEach { s ->
            val row = ItemSettlementHistoryBinding.inflate(layoutInflater, binding.llHistory, false)
            val from = viewModel.displayName(s.fromUserId)
            val to = viewModel.displayName(s.toUserId)
            row.tvHistoryLine.text = "$from paid $to ₹${fmt(s.amount)}"
            val date = sdf.format(Date(s.timestamp))
            val note = s.note?.trim().orEmpty()
            row.tvHistoryMeta.text = if (note.isEmpty()) date else "$date · $note"
            val canDelete = viewModel.canDeleteSettlement(s)
            row.btnHistoryDelete.visibility = if (canDelete) View.VISIBLE else View.INVISIBLE
            row.btnHistoryDelete.setOnClickListener {
                if (!canDelete) return@setOnClickListener
                AlertDialog.Builder(requireContext())
                    .setTitle("Delete settlement?")
                    .setMessage("This restores the corresponding balance.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Delete") { _, _ -> viewModel.deleteSettlement(s.id) }
                    .show()
            }
            binding.llHistory.addView(row.root)
        }
    }

    private fun confirmSettlement(pay: SettlementCalculator.SuggestedPayment, iPay: Boolean) {
        val amountField = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(fmt(pay.amount))
            hint = "Amount"
        }
        val noteField = EditText(requireContext()).apply {
            hint = "Note (optional)"
        }
        val wrap = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad / 2, pad, 0)
            addView(amountField)
            addView(noteField)
        }
        val title = if (iPay) "Mark as paid" else "Mark as received"
        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setView(wrap)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val amount = amountField.text.toString().trim().toDoubleOrNull()
                if (amount == null || amount <= 0) {
                    Toast.makeText(requireContext(), "Enter a valid amount", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                viewModel.addSettlement(pay.fromUserId, pay.toUserId, amount, noteField.text?.toString())
            }
            .show()
    }

    private fun fmt(value: Double): String =
        if (value == kotlin.math.round(value)) "%.0f".format(value) else "%.2f".format(value)

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
