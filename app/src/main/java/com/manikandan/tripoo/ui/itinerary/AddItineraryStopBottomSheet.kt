package com.manikandan.tripoo.ui.itinerary

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.os.bundleOf
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.manikandan.tripoo.data.model.ItineraryStop
import com.manikandan.tripoo.databinding.BottomSheetAddItineraryStopBinding

class AddItineraryStopBottomSheet : BottomSheetDialogFragment() {
    private var _binding: BottomSheetAddItineraryStopBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetAddItineraryStopBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val id = arguments?.getString(ARG_ID).orEmpty()
        if (id.isNotBlank()) {
            binding.tvSheetTitle.text = "Edit stop"
            binding.etTime.setText(arguments?.getString(ARG_TIME))
            binding.etTitle.setText(arguments?.getString(ARG_TITLE))
            binding.etLocation.setText(arguments?.getString(ARG_LOCATION))
            binding.etNotes.setText(arguments?.getString(ARG_NOTES))
        }
        binding.btnClose.setOnClickListener { dismiss() }
        binding.btnSave.setOnClickListener {
            val title = binding.etTitle.text?.toString()?.trim().orEmpty()
            if (title.isEmpty()) {
                Toast.makeText(requireContext(), "Enter a title", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            parentFragmentManager.setFragmentResult(
                REQUEST_KEY,
                bundleOf(
                    "id" to id,
                    "time" to binding.etTime.text?.toString()?.trim().orEmpty(),
                    "title" to title,
                    "location" to binding.etLocation.text?.toString()?.trim().orEmpty(),
                    "notes" to binding.etNotes.text?.toString()?.trim().orEmpty()
                )
            )
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val REQUEST_KEY = "itinerary_stop"
        private const val ARG_ID = "id"
        private const val ARG_TIME = "time"
        private const val ARG_TITLE = "title"
        private const val ARG_LOCATION = "location"
        private const val ARG_NOTES = "notes"

        fun newInstance(stop: ItineraryStop?): AddItineraryStopBottomSheet {
            return AddItineraryStopBottomSheet().apply {
                arguments = bundleOf(
                    ARG_ID to (stop?.id ?: ""),
                    ARG_TIME to (stop?.time ?: ""),
                    ARG_TITLE to (stop?.title ?: ""),
                    ARG_LOCATION to (stop?.location ?: ""),
                    ARG_NOTES to (stop?.notes ?: "")
                )
            }
        }
    }
}
