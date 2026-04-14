package com.manikandan.tripoo.ui.home;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
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
import com.manikandan.tripoo.databinding.FragmentJoinTripBinding;
import com.manikandan.tripoo.utils.ImageUtils;
import com.manikandan.tripoo.utils.Resource;
import com.manikandan.tripoo.viewmodel.HomeViewModel;

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

        AuthRepository auth = new AuthRepository();
        UserRepository userRepo = new UserRepository();
        if (auth.getCurrentUser() != null) {
            String uid = auth.getCurrentUser().getUid();
            userRepo.getUser(uid, user -> {
                if (binding == null) return kotlin.Unit.INSTANCE;
                String name = (user != null && user.getName() != null && !user.getName().isEmpty())
                        ? user.getName()
                        : (auth.getCurrentUser().getDisplayName() != null ? auth.getCurrentUser().getDisplayName() : "User");
                if (binding.tvJoinIdentityName != null) {
                    binding.tvJoinIdentityName.setText("Joining as " + name);
                }
                // Avatar: base64 / URL / initials
                String photoUrl = user != null ? user.getPhotoUrl() : null;
                if (photoUrl != null && !photoUrl.isEmpty() && binding.ivJoinIdentityAvatar != null) {
                    binding.ivJoinIdentityAvatar.setVisibility(View.VISIBLE);
                    binding.tvJoinIdentityAvatar.setVisibility(View.GONE);
                    if (ImageUtils.isBase64Image(photoUrl)) {
                        Bitmap bmp = ImageUtils.base64ToBitmap(photoUrl);
                        if (bmp != null) {
                            binding.ivJoinIdentityAvatar.setImageBitmap(bmp);
                        } else {
                            binding.ivJoinIdentityAvatar.setVisibility(View.GONE);
                            binding.tvJoinIdentityAvatar.setVisibility(View.VISIBLE);
                        }
                    } else {
                        Glide.with(requireContext())
                                .load(photoUrl)
                                .centerCrop()
                                .into(binding.ivJoinIdentityAvatar);
                    }
                }
                if (binding.tvJoinIdentityAvatar.getVisibility() == View.VISIBLE) {
                    String[] parts = name.trim().split(" ");
                    String initials = parts.length >= 2
                            ? (String.valueOf(parts[0].charAt(0)) + parts[parts.length - 1].charAt(0)).toUpperCase()
                            : name.length() >= 2 ? name.substring(0, 2).toUpperCase() : "?";
                    binding.tvJoinIdentityAvatar.setText(initials);
                }
                return kotlin.Unit.INSTANCE;
            });
        }

        if (binding.btnBack != null) {
            binding.btnBack.setOnClickListener(v -> Navigation.findNavController(view).popBackStack());
        }
        if (binding.tvCreateTrip != null) {
            binding.tvCreateTrip.setOnClickListener(v -> Navigation.findNavController(view).navigate(R.id.action_join_to_create));
        }
        if (binding.tvJoinChange != null) {
            binding.tvJoinChange.setOnClickListener(v ->
                    Navigation.findNavController(view).navigate(R.id.profileFragment)
            );
        }

        View.OnClickListener focusTripCodeField = v -> {
            binding.etTripCodeSuffix.requestFocus();
            InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(binding.etTripCodeSuffix, InputMethodManager.SHOW_IMPLICIT);
            }
        };
        if (binding.rowTripCode != null) {
            binding.rowTripCode.setOnClickListener(focusTripCodeField);
        }

        binding.btnJoinTrip.setOnClickListener(v -> {
            String suffix = binding.etTripCodeSuffix.getText().toString();
            if (suffix == null) suffix = "";
            suffix = suffix.trim().toUpperCase()
                    .replace("\u2013", "")
                    .replace("\u2014", "")
                    .replace("\u2011", "")
                    .replace("\u2212", "")
                    .replace(" ", "")
                    .replace("-", "")
                    .replace("\n", "")
                    .replace("\t", "");
            suffix = suffix.replaceAll("[^A-Z0-9]", "");

            if (suffix.length() != 3) {
                Toast.makeText(requireContext(), "Enter the last 3 characters (e.g. ABC)", Toast.LENGTH_SHORT).show();
                return;
            }

            String tripCode = "TRP-" + suffix;
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
                    String tripId = resource.getData();
                    Toast.makeText(requireContext(), "Successfully joined trip!", Toast.LENGTH_SHORT).show();
                    Bundle args = new Bundle();
                    args.putString("tripId", tripId != null ? tripId : "");
                    Navigation.findNavController(view).navigate(R.id.action_join_to_home, args);
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
