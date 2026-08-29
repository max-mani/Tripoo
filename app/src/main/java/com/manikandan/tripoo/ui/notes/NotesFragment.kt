package com.manikandan.tripoo.ui.notes

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import com.manikandan.tripoo.data.repository.TripMetaRepository
import com.manikandan.tripoo.databinding.FragmentNotesBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NotesFragment : Fragment() {
    private var _binding: FragmentNotesBinding? = null
    private val binding get() = _binding!!
    private val repo = TripMetaRepository()
    private val handler = Handler(Looper.getMainLooper())
    private var listener: ListenerRegistration? = null
    private var applyingRemote = false
    private var lastSaved = ""
    private val tripId: String by lazy { arguments?.getString("tripId").orEmpty() }

    private val debounce = Runnable { saveNow() }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentNotesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnBack.setOnClickListener {
            saveNow()
            findNavController().popBackStack()
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                saveNow()
                findNavController().popBackStack()
            }
        })
        binding.etNotes.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (applyingRemote) return
                handler.removeCallbacks(debounce)
                handler.postDelayed(debounce, 800)
            }
        })
        if (tripId.isNotBlank()) {
            listener = repo.listenToNotes(tripId) { notes, _ ->
                if (_binding == null) return@listenToNotes
                val incoming = notes.text
                val local = binding.etNotes.text?.toString().orEmpty()
                if (incoming != local && local == lastSaved) {
                    applyingRemote = true
                    binding.etNotes.setText(incoming)
                    binding.etNotes.setSelection(incoming.length)
                    applyingRemote = false
                    lastSaved = incoming
                }
                val sdf = SimpleDateFormat("d MMM, h:mm a", Locale.getDefault())
                val who = if (notes.updatedBy == FirebaseAuth.getInstance().currentUser?.uid) "you" else notes.updatedBy.take(8)
                binding.tvNotesMeta.text = if (notes.updatedAt <= 0L) {
                    "Not saved yet"
                } else {
                    "Last edited by $who · ${sdf.format(Date(notes.updatedAt))}"
                }
            }
        }
    }

    private fun saveNow() {
        if (tripId.isBlank() || _binding == null) return
        val text = binding.etNotes.text?.toString() ?: return
        if (text == lastSaved) return
        lastSaved = text
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                repo.saveNotes(tripId, text)
            } catch (_: Exception) {
            }
        }
    }

    override fun onPause() {
        saveNow()
        super.onPause()
    }

    override fun onDestroyView() {
        handler.removeCallbacks(debounce)
        listener?.remove()
        super.onDestroyView()
        _binding = null
    }
}
