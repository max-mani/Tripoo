package com.manikandan.tripoo.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.manikandan.tripoo.R
import com.manikandan.tripoo.databinding.FragmentProfileTripsListBinding
import com.manikandan.tripoo.ui.dashboard.TripCardAdapter

class ProfileTripsListFragment : Fragment() {

    private var _binding: FragmentProfileTripsListBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: ProfileTripsListViewModel
    private lateinit var adapter: TripCardAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProfileTripsListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(this)[ProfileTripsListViewModel::class.java]
        adapter = TripCardAdapter { meta ->
            findNavController().navigate(
                R.id.action_profileMyTripsList_to_home,
                Bundle().apply { putString("tripId", meta.trip.id) },
            )
        }
        binding.rvTrips.layoutManager = LinearLayoutManager(requireContext())
        binding.rvTrips.adapter = adapter

        binding.btnBack.setOnClickListener { findNavController().navigateUp() }

        binding.swipeRefresh.setOnRefreshListener { viewModel.load() }

        viewModel.trips.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list ?: emptyList())
        }
        viewModel.error.observe(viewLifecycleOwner) { msg ->
            msg?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
            }
        }
        viewModel.loading.observe(viewLifecycleOwner) { loading ->
            val listEmpty = adapter.currentList.isEmpty()
            binding.progressLoading.visibility =
                if (loading == true && listEmpty) View.VISIBLE else View.GONE
            if (loading != true) binding.swipeRefresh.isRefreshing = false
        }
        viewModel.load()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
