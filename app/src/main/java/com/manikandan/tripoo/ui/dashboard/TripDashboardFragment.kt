package com.manikandan.tripoo.ui.dashboard

import android.graphics.Outline
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.google.android.gms.ads.AdRequest
import com.manikandan.tripoo.R
import com.manikandan.tripoo.data.model.Trip
import com.manikandan.tripoo.data.model.TripWithMeta
import com.manikandan.tripoo.databinding.FragmentTripDashboardBinding
import com.manikandan.tripoo.ui.home.CreateChoiceBottomSheet
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
        binding.chipOutings.setOnClickListener { viewModel.setFilter("outing"); updateChipState("outing") }

        binding.btnProfile.setOnClickListener {
            findNavController().navigate(R.id.action_dashboard_to_profile)
        }
        binding.btnJoinTrip.setOnClickListener {
            findNavController().navigate(R.id.action_dashboard_to_join)
        }
        childFragmentManager.setFragmentResultListener(
            CreateChoiceBottomSheet.REQUEST_KEY,
            viewLifecycleOwner
        ) { _, bundle ->
            val type = bundle.getString(CreateChoiceBottomSheet.RESULT_TYPE) ?: Trip.TYPE_TRIP
            findNavController().navigate(
                R.id.action_dashboard_to_create,
                Bundle().apply { putString("type", type) }
            )
        }
        binding.btnNewTrip.setOnClickListener {
            CreateChoiceBottomSheet().show(childFragmentManager, CreateChoiceBottomSheet.TAG)
        }
        binding.swipeRefreshDashboard.setOnRefreshListener {
            viewModel.loadTrips()
            binding.swipeRefreshDashboard.postDelayed(
                { binding.swipeRefreshDashboard.isRefreshing = false },
                500
            )
        }

        // Parent clipToOutline is unreliable on some API 24–28 devices; clip the ImageView itself.
        // (ViewOutlineProvider.OVAL is API 30+; use setOval for API 21–29.)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            binding.ivAvatar.outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    val w = view.width
                    val h = view.height
                    if (w > 0 && h > 0) {
                        outline.setOval(0, 0, w, h)
                    }
                }
            }
            binding.ivAvatar.clipToOutline = true
        }

        viewModel.filteredTrips.observe(viewLifecycleOwner) { trips ->
            bindTripList(trips ?: emptyList())
        }
        viewModel.allTrips.observe(viewLifecycleOwner) {
            bindTripList(viewModel.filteredTrips.value ?: emptyList())
        }
        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.progressLoading.visibility = if (loading == true) View.VISIBLE else View.GONE
            bindTripList(viewModel.filteredTrips.value ?: emptyList())
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
                    val bmp = ImageUtils.base64ToBitmap(photoUrl)
                    if (bmp != null) {
                        Glide.with(this)
                            .load(bmp)
                            .circleCrop()
                            .into(binding.ivAvatar)
                    } else {
                        binding.ivAvatar.visibility = View.GONE
                        binding.tvAvatar.visibility = View.VISIBLE
                        binding.tvAvatar.text = viewModel.getCurrentUserInitials()
                    }
                } else {
                    Glide.with(this)
                        .load(photoUrl)
                        .circleCrop()
                        .into(binding.ivAvatar)
                }
            } else {
                Glide.with(this).clear(binding.ivAvatar)
                binding.ivAvatar.visibility = View.GONE
                binding.tvAvatar.visibility = View.VISIBLE
                binding.tvAvatar.text = viewModel.getCurrentUserInitials()
            }
        }
        viewModel.errorMessage.observe(viewLifecycleOwner) { msg ->
            msg?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                viewModel.clearErrorMessage()
            }
        }

        binding.btnEmptyNewTrip.setOnClickListener {
            findNavController().navigate(
                R.id.action_dashboard_to_create,
                Bundle().apply { putString("type", Trip.TYPE_TRIP) }
            )
        }
        binding.btnEmptyNewOuting.setOnClickListener {
            findNavController().navigate(
                R.id.action_dashboard_to_create,
                Bundle().apply { putString("type", Trip.TYPE_OUTING) }
            )
        }
        binding.tvEmptyJoinCode.setOnClickListener {
            findNavController().navigate(R.id.action_dashboard_to_join)
        }

        viewModel.loadTrips()

        binding.adViewDashboard.loadAd(AdRequest.Builder().build())
    }

    private fun bindTripList(filtered: List<TripWithMeta>) {
        if (_binding == null) return
        val loading = viewModel.isLoading.value == true
        val noneAtAll = viewModel.allTrips.value.isNullOrEmpty()
        val showOnboarding = !loading && noneAtAll
        binding.llEmptyOnboarding.visibility = if (showOnboarding) View.VISIBLE else View.GONE
        binding.hsvFilters.visibility = if (showOnboarding) View.GONE else View.VISIBLE
        binding.tvYourTripsLabel.visibility = if (showOnboarding) View.GONE else View.VISIBLE
        binding.rvTrips.visibility = if (showOnboarding) View.GONE else View.VISIBLE
        binding.layoutTripActions.visibility = if (showOnboarding) View.GONE else View.VISIBLE
        if (showOnboarding) {
            binding.tvTripCount.text = "Ready when you are"
            adapter.submitList(emptyList())
            return
        }
        adapter.submitList(filtered)
        val activeCount = filtered.count { it.trip.status == "active" }
        binding.tvTripCount.text = "You have $activeCount active trips"
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
            "past" to binding.chipPast,
            "outing" to binding.chipOutings
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
