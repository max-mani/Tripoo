package com.manikandan.tripoo.ui.people

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.auth.FirebaseAuth
import com.manikandan.tripoo.R
import com.manikandan.tripoo.data.model.RecentCollaborator
import com.manikandan.tripoo.data.repository.TripRepository
import com.manikandan.tripoo.data.repository.UserRepository
import com.manikandan.tripoo.databinding.BottomSheetAddPeopleBinding
import com.manikandan.tripoo.databinding.ItemRecentCollaboratorBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AddPeopleBottomSheet : BottomSheetDialogFragment() {
    private var _binding: BottomSheetAddPeopleBinding? = null
    private val binding get() = _binding!!
    private val userRepo = UserRepository()
    private val tripRepo = TripRepository()

    private var tripName: String = ""
    private var joinCode: String = ""

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetAddPeopleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        tripName = arguments?.getString(ARG_TRIP_NAME).orEmpty()
        joinCode = arguments?.getString(ARG_JOIN_CODE).orEmpty()
        binding.tvJoinCode.text = joinCode.ifBlank { "…" }
        binding.btnClose.setOnClickListener { dismiss() }
        binding.btnCopyCode.setOnClickListener { copyCode() }
        binding.btnShareGeneric.setOnClickListener { shareGeneric() }

        val tripId = arguments?.getString(ARG_TRIP_ID).orEmpty()
        viewLifecycleOwner.lifecycleScope.launch {
            if (joinCode.isBlank() && tripId.isNotBlank()) {
                val trip = withContext(Dispatchers.IO) { tripRepo.getTrip(tripId) }
                if (_binding == null) return@launch
                if (trip != null) {
                    if (tripName.isBlank()) tripName = trip.name
                    joinCode = trip.joinCode.orEmpty()
                    binding.tvJoinCode.text = joinCode
                }
            }
            val recents = withContext(Dispatchers.IO) {
                val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
                if (uid.isBlank()) emptyList()
                else userRepo.getUser(uid)?.recentCollaborators.orEmpty()
            }
            if (_binding == null) return@launch
            bindRecents(recents)
        }
    }

    override fun onDismiss(dialog: android.content.DialogInterface) {
        parentFragmentManager.setFragmentResult(RESULT_DISMISSED, bundleOf())
        super.onDismiss(dialog)
    }

    private fun bindRecents(all: List<RecentCollaborator>) {
        val exclude = arguments?.getStringArrayList(ARG_EXCLUDE_IDS)?.toSet().orEmpty()
        val shown = all
            .filter { it.uid.isNotBlank() && it.uid !in exclude }
            .sortedByDescending { it.lastSeenAt }
        binding.llRecents.removeAllViews()
        if (shown.isEmpty()) {
            binding.llRecentsSection.visibility = View.GONE
            return
        }
        binding.llRecentsSection.visibility = View.VISIBLE
        val inflater = layoutInflater
        for (c in shown) {
            val row = ItemRecentCollaboratorBinding.inflate(inflater, binding.llRecents, false)
            row.tvRecentName.text = c.name.ifBlank { c.uid.take(8) }
            row.btnShareInvite.setOnClickListener { sharePersonalized(c.name.ifBlank { "there" }) }
            binding.llRecents.addView(row.root)
        }
    }

    private fun copyCode() {
        if (joinCode.isBlank()) return
        val cm = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Trip Code", joinCode))
        Toast.makeText(requireContext(), "Trip code copied!", Toast.LENGTH_SHORT).show()
    }

    private fun shareGeneric() {
        if (joinCode.isBlank()) return
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, getString(R.string.groups_invite_share, joinCode))
                },
                getString(R.string.more_options)
            )
        )
    }

    private fun sharePersonalized(name: String) {
        if (joinCode.isBlank()) return
        val label = tripName.ifBlank { getString(R.string.app_name) }
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(
                        Intent.EXTRA_TEXT,
                        getString(R.string.add_people_share_personal, name, label, joinCode)
                    )
                },
                getString(R.string.more_options)
            )
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val RESULT_DISMISSED = "add_people_dismissed"
        private const val ARG_TRIP_ID = "tripId"
        private const val ARG_TRIP_NAME = "tripName"
        private const val ARG_JOIN_CODE = "joinCode"
        private const val ARG_EXCLUDE_IDS = "excludeIds"

        @JvmStatic
        fun newInstance(
            tripId: String,
            tripName: String,
            joinCode: String,
            excludeMemberIds: List<String>
        ): AddPeopleBottomSheet {
            return AddPeopleBottomSheet().apply {
                arguments = bundleOf(
                    ARG_TRIP_ID to tripId,
                    ARG_TRIP_NAME to tripName,
                    ARG_JOIN_CODE to joinCode,
                    ARG_EXCLUDE_IDS to ArrayList(excludeMemberIds)
                )
            }
        }
    }
}
