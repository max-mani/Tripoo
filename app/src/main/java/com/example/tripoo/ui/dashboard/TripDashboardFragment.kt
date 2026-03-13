package com.example.tripoo.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.tripoo.R
import com.example.tripoo.databinding.FragmentTripDashboardBinding

class TripDashboardFragment : Fragment() {
    private var _binding: FragmentTripDashboardBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTripDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnProfile.setOnClickListener {
            findNavController().navigate(R.id.action_dashboard_to_profile)
        }
        binding.btnJoinTrip.setOnClickListener {
            findNavController().navigate(R.id.action_dashboard_to_join)
        }
        binding.btnNewTrip.setOnClickListener {
            findNavController().navigate(R.id.action_dashboard_to_create)
        }
        binding.cardActiveTrip.setOnClickListener {
            // Demo: navigate with default/empty tripId for now.
            findNavController().navigate(R.id.action_dashboard_to_home)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
