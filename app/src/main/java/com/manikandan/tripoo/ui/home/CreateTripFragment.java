package com.manikandan.tripoo.ui.home;

import android.app.DatePickerDialog;
import android.content.Context;
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
import com.manikandan.tripoo.data.model.Trip;
import com.manikandan.tripoo.data.repository.AuthRepository;
import com.manikandan.tripoo.data.repository.UserRepository;
import com.manikandan.tripoo.databinding.FragmentCreateTripBinding;
import com.manikandan.tripoo.ui.people.AddPeopleBottomSheet;
import com.manikandan.tripoo.utils.DateFormatter;
import com.manikandan.tripoo.utils.ImageUtils;
import com.manikandan.tripoo.utils.Resource;
import com.manikandan.tripoo.viewmodel.HomeViewModel;
import com.google.android.material.chip.Chip;
import com.google.firebase.Timestamp;

import java.util.Calendar;
import java.util.Date;

public class CreateTripFragment extends Fragment {
    private FragmentCreateTripBinding binding;
    private HomeViewModel viewModel;
    private Calendar startCalendar = Calendar.getInstance();
    private Calendar endCalendar = Calendar.getInstance();
    private String editTripId = "";
    private boolean tripFormPrefilled = false;
    private String gatheringType = Trip.TYPE_TRIP;
    private Trip loadedTrip;
    private boolean postCreateInviteShown;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentCreateTripBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    private boolean isEditMode() {
        return editTripId != null && !editTripId.isEmpty();
    }

    private boolean isOuting() {
        return Trip.TYPE_OUTING.equals(gatheringType);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(HomeViewModel.class);

        if (getArguments() != null) {
            editTripId = getArguments().getString("editTripId", "");
            gatheringType = getArguments().getString("type", Trip.TYPE_TRIP);
        } else {
            editTripId = "";
            gatheringType = Trip.TYPE_TRIP;
        }
        if (gatheringType == null || gatheringType.isEmpty()) {
            gatheringType = Trip.TYPE_TRIP;
        }

        applyTypeUi();

        if (isEditMode()) {
            binding.btnCreateTrip.setText("Save changes");
            binding.btnCreateTrip.setEnabled(false);
            viewModel.loadTrip(editTripId);
        }

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
                    binding.tvCreateIdentityName.setText(isEditMode() ? ("Editing as " + name) : ("Creating as " + name));
                }
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

        if (isEditMode()) {
            viewModel.getTripLiveData().observe(getViewLifecycleOwner(), resource -> {
                if (binding == null || tripFormPrefilled) return;
                if (resource != null && resource.isSuccess() && resource.getData() != null) {
                    Trip t = resource.getData();
                    if (t.getId() != null && t.getId().equals(editTripId)) {
                        tripFormPrefilled = true;
                        loadedTrip = t;
                        if (t.getType() != null && !t.getType().isEmpty()) {
                            gatheringType = t.getType();
                        }
                        applyTypeUi();
                        if (binding.etTripName != null) binding.etTripName.setText(t.getName());
                        if (binding.etPlace != null) binding.etPlace.setText(t.getDestination());
                        startCalendar.setTimeInMillis(t.getStartDate());
                        endCalendar.setTimeInMillis(t.getEndDate());
                        binding.etStartDate.setText(DateFormatter.formatDate(t.getStartDate()));
                        binding.etEndDate.setText(DateFormatter.formatDate(t.getEndDate()));
                        if (binding.etBudget != null) binding.etBudget.setText(String.valueOf(t.getBudget()));
                        if (binding.etDescription != null) {
                            String d = t.getDescription();
                            binding.etDescription.setText(d != null ? d : "");
                        }
                        selectCategoryChip(t.getDescription());
                        binding.btnCreateTrip.setEnabled(true);
                    }
                } else if (resource != null && resource.isError()) {
                    Toast.makeText(requireContext(), resource.getMessage() != null ? resource.getMessage() : "Failed to load trip", Toast.LENGTH_SHORT).show();
                }
            });

            viewModel.getUpdateTripLiveData().observe(getViewLifecycleOwner(), resource -> {
                if (resource == null || binding == null) return;
                if (resource.isLoading()) {
                    binding.progressBar.setVisibility(View.VISIBLE);
                    binding.btnCreateTrip.setEnabled(false);
                } else {
                    binding.progressBar.setVisibility(View.GONE);
                    binding.btnCreateTrip.setEnabled(true);
                    if (resource.isSuccess()) {
                        Toast.makeText(requireContext(), isOuting() ? "Outing updated" : "Trip updated", Toast.LENGTH_SHORT).show();
                        Bundle args = new Bundle();
                        args.putString("tripId", editTripId);
                        Navigation.findNavController(view).navigate(R.id.action_create_to_home, args);
                    } else if (resource.isError()) {
                        Toast.makeText(requireContext(), resource.getMessage() != null ? resource.getMessage() : "Update failed", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        } else {
            viewModel.getCreateTripLiveData().observe(getViewLifecycleOwner(), resource -> {
                if (resource == null || binding == null) return;
                if (resource.isLoading()) {
                    binding.progressBar.setVisibility(View.VISIBLE);
                    binding.btnCreateTrip.setEnabled(false);
                } else {
                    binding.progressBar.setVisibility(View.GONE);
                    binding.btnCreateTrip.setEnabled(true);

                    if (resource.isSuccess() && !postCreateInviteShown) {
                        postCreateInviteShown = true;
                        String tripId = resource.getData();
                        Toast.makeText(requireContext(),
                                isOuting() ? "Outing created! Invite people now?" : "Trip created! Invite people now?",
                                Toast.LENGTH_SHORT).show();
                        if (tripId == null || tripId.isEmpty()) {
                            Bundle args = new Bundle();
                            args.putString("tripId", "");
                            Navigation.findNavController(view).navigate(R.id.action_create_to_home, args);
                            return;
                        }
                        java.util.ArrayList<String> exclude = new java.util.ArrayList<>();
                        if (auth.getCurrentUser() != null) {
                            exclude.add(auth.getCurrentUser().getUid());
                        }
                        String createdName = binding.etTripName != null
                                ? binding.etTripName.getText().toString().trim() : "";
                        getChildFragmentManager().setFragmentResultListener(
                                AddPeopleBottomSheet.RESULT_DISMISSED,
                                getViewLifecycleOwner(),
                                (k, b) -> {
                                    Bundle args = new Bundle();
                                    args.putString("tripId", tripId);
                                    Navigation.findNavController(view).navigate(R.id.action_create_to_home, args);
                                });
                        AddPeopleBottomSheet.newInstance(tripId, createdName, "", exclude)
                                .show(getChildFragmentManager(), "add_people");
                    } else if (resource.isError()) {
                        Toast.makeText(requireContext(), resource.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }

        binding.btnCreateTrip.setOnClickListener(v -> submit(view));
    }

    private void applyTypeUi() {
        if (binding == null) return;
        boolean outing = isOuting();
        if (binding.llTripScheduleFields != null) {
            binding.llTripScheduleFields.setVisibility(outing ? View.GONE : View.VISIBLE);
        }
        if (binding.llOutingCategory != null) {
            binding.llOutingCategory.setVisibility(outing ? View.VISIBLE : View.GONE);
        }
        if (binding.llTripDescriptionFields != null) {
            binding.llTripDescriptionFields.setVisibility(outing ? View.GONE : View.VISIBLE);
        }
        if (binding.tvTripNameLabel != null) {
            binding.tvTripNameLabel.setText(outing ? "Outing Name" : "Trip Name");
        }
        if (binding.etTripName != null) {
            binding.etTripName.setHint(outing ? "e.g. Dinner at Marina" : "e.g. Summer in Bali");
        }
        if (binding.tvDestinationLabel != null) {
            binding.tvDestinationLabel.setText(outing ? "Where (optional)" : "Destination");
        }
        if (binding.etPlace != null) {
            binding.etPlace.setHint(outing ? "e.g. Marina Diner" : "e.g. Bali, Indonesia");
        }
        if (binding.tvInviteCopy != null && !isEditMode()) {
            binding.tvInviteCopy.setText(outing
                    ? "A unique join code will be generated after creating the outing. Share it with your group to let them join instantly!"
                    : "A unique join code will be generated after creating the trip. Share it with your group to let them join instantly!");
        }
        if (binding.tvCreateScreenTitle != null) {
            if (isEditMode()) {
                binding.tvCreateScreenTitle.setText(outing ? "Edit outing" : "Edit trip");
            } else {
                binding.tvCreateScreenTitle.setText(outing ? "Create Outing" : "Create Trip");
            }
        }
        if (binding.tvCreateScreenSubtitle != null) {
            if (isEditMode()) {
                binding.tvCreateScreenSubtitle.setText(outing ? "Update your outing details" : "Update your trip details");
            } else {
                binding.tvCreateScreenSubtitle.setText(outing ? "Name it and go — dates optional" : "Fill in your trip details");
            }
        }
        if (!isEditMode()) {
            binding.btnCreateTrip.setText(outing ? "Create Outing" : "Create Trip");
        }
    }

    private void selectCategoryChip(String description) {
        if (binding.chipCategoryGroup == null || description == null) return;
        String d = description.trim();
        for (int i = 0; i < binding.chipCategoryGroup.getChildCount(); i++) {
            View child = binding.chipCategoryGroup.getChildAt(i);
            if (child instanceof Chip) {
                Chip chip = (Chip) child;
                Object tag = chip.getTag();
                chip.setChecked(tag != null && d.equalsIgnoreCase(tag.toString()));
            }
        }
    }

    private String selectedCategoryLabel() {
        if (binding.chipCategoryGroup == null) return "";
        int id = binding.chipCategoryGroup.getCheckedChipId();
        if (id == View.NO_ID) return "";
        View chip = binding.chipCategoryGroup.findViewById(id);
        if (chip != null && chip.getTag() != null) {
            return chip.getTag().toString();
        }
        return "";
    }

    private void submit(View view) {
        String name = binding.etTripName != null ? binding.etTripName.getText().toString().trim() : "";
        String destination = binding.etPlace != null ? binding.etPlace.getText().toString().trim() : "";
        String description = binding.etDescription != null ? binding.etDescription.getText().toString().trim() : "";

        if (isOuting()) {
            if (TextUtils.isEmpty(name)) {
                Toast.makeText(requireContext(), "Please enter a name", Toast.LENGTH_SHORT).show();
                return;
            }
            description = selectedCategoryLabel();
            if (isEditMode() && loadedTrip != null) {
                Timestamp startDate = new Timestamp(new Date(loadedTrip.getStartDate()));
                Timestamp endDate = new Timestamp(new Date(loadedTrip.getEndDate()));
                viewModel.updateTrip(editTripId, name, destination, description, startDate, endDate, loadedTrip.getBudget());
            } else {
                // Same-day outing: start=now, end=end of local day so deriveStatus() stays
                // "active" until midnight (start==end==now would flip to "past" immediately).
                long now = System.currentTimeMillis();
                Timestamp startDate = new Timestamp(new Date(now));
                Timestamp endDate = new Timestamp(new Date(DateFormatter.endOfLocalDayMillis(now)));
                viewModel.createTrip(name, destination, description, startDate, endDate, 0.0, Trip.TYPE_OUTING);
            }
            return;
        }

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

            if (isEditMode()) {
                viewModel.updateTrip(editTripId, name, destination, description, startDate, endDate, budget);
            } else {
                viewModel.createTrip(name, destination, description, startDate, endDate, budget, Trip.TYPE_TRIP);
            }
        } catch (NumberFormatException e) {
            Toast.makeText(requireContext(), "Invalid budget amount", Toast.LENGTH_SHORT).show();
        }
    }

    private void showDatePicker(boolean isStartDate) {
        Calendar calendar = isStartDate ? startCalendar : endCalendar;
        Context themed = new android.view.ContextThemeWrapper(requireContext(), R.style.ThemeOverlay_Tripoo_DatePickerDialog);
        DatePickerDialog datePickerDialog = new DatePickerDialog(
                themed,
                (v, year, month, dayOfMonth) -> {
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
