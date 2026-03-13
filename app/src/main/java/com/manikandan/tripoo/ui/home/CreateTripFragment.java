package com.manikandan.tripoo.ui.home;

import android.app.DatePickerDialog;
import android.graphics.Bitmap;
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
import com.bumptech.glide.Glide;
import com.manikandan.tripoo.R;
import com.manikandan.tripoo.data.repository.AuthRepository;
import com.manikandan.tripoo.data.repository.UserRepository;
import com.manikandan.tripoo.databinding.FragmentCreateTripBinding;
import com.manikandan.tripoo.utils.DateFormatter;
import com.manikandan.tripoo.utils.ImageUtils;
import com.manikandan.tripoo.utils.Resource;
import com.manikandan.tripoo.viewmodel.HomeViewModel;
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

        AuthRepository auth = new AuthRepository();
        UserRepository userRepo = new UserRepository();
        if (auth.getCurrentUser() != null) {
            String uid = auth.getCurrentUser().getUid();
            userRepo.getUser(uid, user -> {
                if (binding == null) return kotlin.Unit.INSTANCE;
                String name = (user != null && user.getName() != null && !user.getName().isEmpty())
                        ? user.getName()
                        : (auth.getCurrentUser().getDisplayName() != null ? auth.getCurrentUser().getDisplayName() : "User");
                if (binding.tvCreateIdentityName != null) {
                    binding.tvCreateIdentityName.setText("Creating as " + name);
                }
                // Avatar: base64 / URL / initials
                String photoUrl = user != null ? user.getPhotoUrl() : null;
                if (photoUrl != null && !photoUrl.isEmpty() && binding.ivCreateIdentityAvatar != null) {
                    binding.ivCreateIdentityAvatar.setVisibility(View.VISIBLE);
                    binding.tvCreateIdentityAvatar.setVisibility(View.GONE);
                    if (ImageUtils.isBase64Image(photoUrl)) {
                        Bitmap bmp = ImageUtils.base64ToBitmap(photoUrl);
                        if (bmp != null) {
                            binding.ivCreateIdentityAvatar.setImageBitmap(bmp);
                        } else {
                            binding.ivCreateIdentityAvatar.setVisibility(View.GONE);
                            binding.tvCreateIdentityAvatar.setVisibility(View.VISIBLE);
                        }
                    } else {
                        Glide.with(requireContext())
                                .load(photoUrl)
                                .centerCrop()
                                .into(binding.ivCreateIdentityAvatar);
                    }
                }
                if (binding.tvCreateIdentityAvatar.getVisibility() == View.VISIBLE) {
                    String[] parts = name.trim().split(" ");
                    String initials = parts.length >= 2
                            ? (String.valueOf(parts[0].charAt(0)) + parts[parts.length - 1].charAt(0)).toUpperCase()
                            : name.length() >= 2 ? name.substring(0, 2).toUpperCase() : "?";
                    binding.tvCreateIdentityAvatar.setText(initials);
                }
                return kotlin.Unit.INSTANCE;
            });
        }

        if (binding.btnBack != null) {
            binding.btnBack.setOnClickListener(v -> Navigation.findNavController(view).popBackStack());
        }
        if (binding.tvCreateChange != null) {
            binding.tvCreateChange.setOnClickListener(v ->
                    Navigation.findNavController(view).navigate(R.id.profileFragment)
            );
        }
        
        binding.etStartDate.setOnClickListener(v -> showDatePicker(true));
        binding.etEndDate.setOnClickListener(v -> showDatePicker(false));
        
        binding.btnCreateTrip.setOnClickListener(v -> {
            String name = binding.etTripName != null ? binding.etTripName.getText().toString().trim() : "";
            String destination = binding.etPlace != null ? binding.etPlace.getText().toString().trim() : "";
            String startDateStr = binding.etStartDate.getText().toString().trim();
            String endDateStr = binding.etEndDate.getText().toString().trim();
            String budgetStr = binding.etBudget.getText().toString().trim();
            
            if (TextUtils.isEmpty(name) || TextUtils.isEmpty(destination) || TextUtils.isEmpty(startDateStr) || 
                TextUtils.isEmpty(endDateStr) || TextUtils.isEmpty(budgetStr)) {
                Toast.makeText(requireContext(), "Please fill all required fields", Toast.LENGTH_SHORT).show();
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
                
                viewModel.createTrip(name, destination, startDate, endDate, budget);
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
                    String tripId = resource.getData();
                    Toast.makeText(requireContext(), "Trip created!", Toast.LENGTH_SHORT).show();
                    Bundle args = new Bundle();
                    args.putString("tripId", tripId != null ? tripId : "");
                    Navigation.findNavController(view).navigate(R.id.action_create_to_home, args);
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
