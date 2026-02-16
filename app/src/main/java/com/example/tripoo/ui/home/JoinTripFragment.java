package com.example.tripoo.ui.home;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import com.example.tripoo.R;
import com.example.tripoo.databinding.FragmentJoinTripBinding;
import com.example.tripoo.utils.Resource;
import com.example.tripoo.viewmodel.HomeViewModel;

public class JoinTripFragment extends Fragment {
    private FragmentJoinTripBinding binding;
    private HomeViewModel viewModel;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentJoinTripBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        viewModel = new ViewModelProvider(this).get(HomeViewModel.class);
        
        binding.btnJoinTrip.setOnClickListener(v -> {
            String tripCode = binding.etTripCode.getText().toString().trim().toUpperCase();
            
            if (TextUtils.isEmpty(tripCode)) {
                Toast.makeText(requireContext(), "Please enter trip code", Toast.LENGTH_SHORT).show();
                return;
            }
            
            if (!tripCode.matches("TRP-\\d{3}")) {
                Toast.makeText(requireContext(), "Invalid trip code format", Toast.LENGTH_SHORT).show();
                return;
            }
            
            viewModel.joinTrip(tripCode);
        });
        
        viewModel.getJoinTripLiveData().observe(getViewLifecycleOwner(), resource -> {
            if (resource.isLoading()) {
                binding.progressBar.setVisibility(View.VISIBLE);
                binding.btnJoinTrip.setEnabled(false);
            } else {
                binding.progressBar.setVisibility(View.GONE);
                binding.btnJoinTrip.setEnabled(true);
                
                if (resource.isSuccess()) {
                    Toast.makeText(requireContext(), "Successfully joined trip!", Toast.LENGTH_SHORT).show();
                    Navigation.findNavController(view).navigate(R.id.action_join_trip_to_home);
                } else if (resource.isError()) {
                    Toast.makeText(requireContext(), resource.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
