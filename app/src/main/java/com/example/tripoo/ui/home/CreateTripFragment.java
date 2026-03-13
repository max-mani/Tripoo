package com.example.tripoo.ui.home;

import android.app.DatePickerDialog;
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
import com.example.tripoo.databinding.FragmentCreateTripBinding;
import com.example.tripoo.utils.DateFormatter;
import com.example.tripoo.utils.Resource;
import com.example.tripoo.viewmodel.HomeViewModel;
import com.google.firebase.Timestamp;

import java.util.Calendar;

public class CreateTripFragment extends Fragment {
    private FragmentCreateTripBinding binding;
    private HomeViewModel viewModel;
    private Calendar startCalendar = Calendar.getInstance();
    private Calendar endCalendar = Calendar.getInstance();

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentCreateTripBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        viewModel = new ViewModelProvider(this).get(HomeViewModel.class);

        if (binding.btnBack != null) {
            binding.btnBack.setOnClickListener(v -> Navigation.findNavController(view).popBackStack());
        }
        
        binding.etStartDate.setOnClickListener(v -> showDatePicker(true));
        binding.etEndDate.setOnClickListener(v -> showDatePicker(false));
        
        binding.btnCreateTrip.setOnClickListener(v -> {
            String place = binding.etPlace.getText().toString().trim();
            String startDateStr = binding.etStartDate.getText().toString().trim();
            String endDateStr = binding.etEndDate.getText().toString().trim();
            String budgetStr = binding.etBudget.getText().toString().trim();
            
            if (TextUtils.isEmpty(place) || TextUtils.isEmpty(startDateStr) || 
                TextUtils.isEmpty(endDateStr) || TextUtils.isEmpty(budgetStr)) {
                Toast.makeText(requireContext(), "Please fill all fields", Toast.LENGTH_SHORT).show();
                return;
            }
            
            try {
                double budget = Double.parseDouble(budgetStr);
                Timestamp startDate = new Timestamp(startCalendar.getTime());
                Timestamp endDate = new Timestamp(endCalendar.getTime());
                
                if (endDate.compareTo(startDate) < 0) {
                    Toast.makeText(requireContext(), "End date must be after start date", Toast.LENGTH_SHORT).show();
                    return;
                }
                
                viewModel.createTrip(place, startDate, endDate, budget);
            } catch (NumberFormatException e) {
                Toast.makeText(requireContext(), "Invalid budget amount", Toast.LENGTH_SHORT).show();
            }
        });
        
        viewModel.getCreateTripLiveData().observe(getViewLifecycleOwner(), resource -> {
            if (resource.isLoading()) {
                binding.progressBar.setVisibility(View.VISIBLE);
                binding.btnCreateTrip.setEnabled(false);
            } else {
                binding.progressBar.setVisibility(View.GONE);
                binding.btnCreateTrip.setEnabled(true);
                
                if (resource.isSuccess()) {
                    Toast.makeText(requireContext(), "Trip created! Code: " + resource.getData(), Toast.LENGTH_LONG).show();
                    Navigation.findNavController(view).navigate(R.id.action_create_to_home);
                } else if (resource.isError()) {
                    Toast.makeText(requireContext(), resource.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void showDatePicker(boolean isStartDate) {
        Calendar calendar = isStartDate ? startCalendar : endCalendar;
        DatePickerDialog datePickerDialog = new DatePickerDialog(
                requireContext(),
                (view, year, month, dayOfMonth) -> {
                    calendar.set(year, month, dayOfMonth);
                    String dateStr = DateFormatter.formatDate(new Timestamp(calendar.getTime()));
                    if (isStartDate) {
                        binding.etStartDate.setText(dateStr);
                    } else {
                        binding.etEndDate.setText(dateStr);
                    }
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
        );
        datePickerDialog.show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
