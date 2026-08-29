package com.manikandan.tripoo.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.manikandan.tripoo.data.model.Trip
import com.manikandan.tripoo.databinding.BottomSheetCreateChoiceBinding

class CreateChoiceBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetCreateChoiceBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetCreateChoiceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnClose.setOnClickListener { dismiss() }
        binding.rowFullTrip.setOnClickListener { choose(Trip.TYPE_TRIP) }
        binding.rowQuickOuting.setOnClickListener { choose(Trip.TYPE_OUTING) }
    }

    private fun choose(type: String) {
        parentFragmentManager.setFragmentResult(REQUEST_KEY, bundleOf(RESULT_TYPE to type))
        dismiss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val REQUEST_KEY = "create_choice"
        const val RESULT_TYPE = "type"
        const val TAG = "CreateChoice"
    }
}
