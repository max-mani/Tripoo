package com.manikandan.tripoo.ui.dashboard

import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.google.android.gms.ads.AdRequest
import com.manikandan.tripoo.R
import com.manikandan.tripoo.databinding.FragmentTripDashboardBinding
import com.manikandan.tripoo.utils.ImageUtils

class TripDashboardFragment : Fragment() {
    private var _binding: FragmentTripDashboardBinding? = null
    private val binding get() = _binding!!
    private val viewModel: TripDashboardViewModel by viewModels()
    private lateinit var adapter: TripCardAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTripDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = TripCardAdapter { tripWithMeta ->
            findNavController().navigate(
                R.id.action_dashboard_to_home,
                Bundle().apply { putString("tripId", tripWithMeta.trip.id) }
            )
        }
        binding.rvTrips.layoutManager = LinearLayoutManager(requireContext())
        binding.rvTrips.adapter = adapter

        binding.chipAll.setOnClickListener { viewModel.setFilter("all"); updateChipState("all") }
        binding.chipActive.setOnClickListener { viewModel.setFilter("active"); updateChipState("active") }
        binding.chipUpcoming.setOnClickListener { viewModel.setFilter("upcoming"); updateChipState("upcoming") }
        binding.chipPast.setOnClickListener { viewModel.setFilter("past"); updateChipState("past") }

        binding.btnProfile.setOnClickListener {
            findNavController().navigate(R.id.action_dashboard_to_profile)
        }
        binding.btnJoinTrip.setOnClickListener {
            findNavController().navigate(R.id.action_dashboard_to_join)
        }
        binding.btnNewTrip.setOnClickListener {
            findNavController().navigate(R.id.action_dashboard_to_create)
        }
        binding.swipeRefreshDashboard.setOnRefreshListener {
            viewModel.loadTrips()
            binding.swipeRefreshDashboard.postDelayed(
                { binding.swipeRefreshDashboard.isRefreshing = false },
                500
            )
        }

        viewModel.filteredTrips.observe(viewLifecycleOwner) { trips ->
            adapter.submitList(trips ?: emptyList())
            val activeCount = (trips ?: emptyList()).count { it.trip.status == "active" }
            binding.tvTripCount.text = "You have $activeCount active trips"
        }
        viewModel.user.observe(viewLifecycleOwner) { user ->
            val fullName = (user?.name?.takeIf { it.isNotBlank() }) ?: viewModel.getCurrentUserName()
            val firstName = fullName.split(" ").firstOrNull().takeUnless { it.isNullOrBlank() } ?: fullName
            binding.tvGreeting.text = "Hey $firstName \uD83D\uDC4B"

            val photoUrl = user?.photoUrl
            if (!photoUrl.isNullOrBlank()) {
                binding.ivAvatar.visibility = View.VISIBLE
                binding.tvAvatar.visibility = View.GONE
                if (ImageUtils.isBase64Image(photoUrl)) {
                    val bmp: Bitmap? = ImageUtils.base64ToBitmap(photoUrl)
                    if (bmp != null) {
                        binding.ivAvatar.setImageBitmap(bmp)
                    } else {
                        binding.ivAvatar.visibility = View.GONE
                        binding.tvAvatar.visibility = View.VISIBLE
                        binding.tvAvatar.text = viewModel.getCurrentUserInitials()
                    }
                } else {
                    Glide.with(this)
                        .load(photoUrl)
                        .centerCrop()
                        .into(binding.ivAvatar)
                }
            } else {
                binding.ivAvatar.visibility = View.GONE
                binding.tvAvatar.visibility = View.VISIBLE
                binding.tvAvatar.text = viewModel.getCurrentUserInitials()
            }
        }
        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.progressLoading.visibility = if (loading == true) View.VISIBLE else View.GONE
        }
        viewModel.errorMessage.observe(viewLifecycleOwner) { msg ->
            msg?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                viewModel.clearErrorMessage()
            }
        }

        viewModel.loadTrips()

        binding.adViewDashboard.loadAd(AdRequest.Builder().build())
    }

    override fun onPause() {
        binding.adViewDashboard.pause()
        super.onPause()
    }

    private fun updateChipState(selected: String) {
        val chips = listOf(
            "all" to binding.chipAll,
            "active" to binding.chipActive,
            "upcoming" to binding.chipUpcoming,
            "past" to binding.chipPast
        )
        chips.forEach { (filter, chip) ->
            if (filter == selected) {
                chip.setBackgroundResource(R.drawable.bg_chip_on)
                chip.setTextColor(resources.getColor(android.R.color.white, null))
            } else {
                chip.setBackgroundResource(R.drawable.bg_chip_off)
                chip.setTextColor(resources.getColor(R.color.tripoo_text_secondary, null))
            }
        }
    }

    override fun onResume() {
        super.onResume()
        binding.adViewDashboard.resume()
        viewModel.loadTrips()
    }

    override fun onDestroyView() {
        binding.adViewDashboard.destroy()
        super.onDestroyView()
        _binding = null
    }
}
