package com.manikandan.tripoo.ui.polls

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.os.bundleOf
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.manikandan.tripoo.databinding.BottomSheetCreatePollBinding

class CreatePollBottomSheet : BottomSheetDialogFragment() {
    private var _binding: BottomSheetCreatePollBinding? = null
    private val binding get() = _binding!!
    private val optionFields = mutableListOf<EditText>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetCreatePollBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        addOptionField()
        addOptionField()
        binding.btnClose.setOnClickListener { dismiss() }
        binding.btnAddOption.setOnClickListener { addOptionField() }
        binding.btnCreate.setOnClickListener {
            val q = binding.etQuestion.text?.toString()?.trim().orEmpty()
            val opts = optionFields.map { it.text?.toString()?.trim().orEmpty() }.filter { it.isNotEmpty() }
            if (q.isEmpty() || opts.size < 2) {
                Toast.makeText(requireContext(), "Question and at least 2 options", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            parentFragmentManager.setFragmentResult(
                REQUEST_KEY,
                bundleOf("question" to q, "options" to ArrayList(opts))
            )
            dismiss()
        }
    }

    private fun addOptionField() {
        val et = EditText(requireContext()).apply {
            hint = "Option ${optionFields.size + 1}"
            setBackgroundResource(com.manikandan.tripoo.R.drawable.bg_input_outline)
            setPadding(24, 24, 24, 24)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 8 }
        }
        optionFields.add(et)
        binding.llOptions.addView(et)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val REQUEST_KEY = "create_poll"
    }
}
