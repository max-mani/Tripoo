package com.example.tripoo.ui.profile;

import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import com.bumptech.glide.Glide;
import com.example.tripoo.R;
import com.example.tripoo.data.model.User;
import com.example.tripoo.databinding.FragmentProfileBinding;
import com.example.tripoo.utils.ImageUtils;
import com.example.tripoo.viewmodel.ProfileViewModel;

import java.io.IOException;

public class ProfileFragment extends Fragment {
    private FragmentProfileBinding binding;
    private ProfileViewModel viewModel;
    private String currentPhotoUrl;
    private ActivityResultLauncher<String> pickImageLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        pickImageLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                this::onImagePicked
        );
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentProfileBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        viewModel = new ViewModelProvider(this).get(ProfileViewModel.class);
        
        binding.btnChangePhoto.setOnClickListener(v -> pickImageLauncher.launch("image/*"));
        
        binding.btnUpdateProfile.setOnClickListener(v -> {
            String name = binding.etName.getText().toString().trim();
            
            if (TextUtils.isEmpty(name)) {
                Toast.makeText(requireContext(), "Please enter name", Toast.LENGTH_SHORT).show();
                return;
            }
            
            viewModel.updateProfile(name, currentPhotoUrl);
        });
        
        binding.btnSignOut.setOnClickListener(v -> {
            viewModel.signOut();
            Navigation.findNavController(view).navigate(R.id.action_profile_to_auth);
        });

        if (binding.btnBackToDashboard != null) {
            binding.btnBackToDashboard.setOnClickListener(v ->
                    Navigation.findNavController(view).navigate(R.id.action_profile_to_dashboard));
        }
        
        viewModel.getUserLiveData().observe(getViewLifecycleOwner(), resource -> {
            if (resource.isSuccess() && resource.getData() != null) {
                User user = resource.getData();
                binding.etName.setText(user.getName());
                binding.etEmail.setText(user.getEmail());
                if (binding.tvProfileName != null) binding.tvProfileName.setText(user.getName());
                if (binding.tvProfileEmail != null) binding.tvProfileEmail.setText(user.getEmail());
                
                if (user.getPhotoUrl() != null && !user.getPhotoUrl().isEmpty()) {
                    loadImage(user.getPhotoUrl());
                    currentPhotoUrl = user.getPhotoUrl();
                }
            }
        });
        
        viewModel.getUploadImageLiveData().observe(getViewLifecycleOwner(), resource -> {
            if (resource.isSuccess()) {
                currentPhotoUrl = resource.getData();
                loadImage(currentPhotoUrl);
                // Auto-save to Firestore so the image is persisted
                String name = binding.etName.getText().toString().trim();
                if (!TextUtils.isEmpty(name)) {
                    viewModel.updateProfile(name, currentPhotoUrl);
                } else {
                    // Save with current user name from ViewModel if name field is empty
                    viewModel.savePhotoToFirestore(currentPhotoUrl);
                }
            } else if (resource.isError()) {
                Toast.makeText(requireContext(), resource.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
        
        viewModel.getUpdateProfileLiveData().observe(getViewLifecycleOwner(), resource -> {
            if (resource.isSuccess()) {
                Toast.makeText(requireContext(), "Profile updated successfully", Toast.LENGTH_SHORT).show();
            } else if (resource.isError()) {
                Toast.makeText(requireContext(), resource.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void onImagePicked(@Nullable Uri imageUri) {
        if (imageUri == null) return;
        try {
            String base64Image = ImageUtils.cropAndConvertToBase64(requireContext(), imageUri);
            viewModel.uploadProfileImage(base64Image);
        } catch (IOException e) {
            Toast.makeText(requireContext(), "Failed to process image: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * Load image from base64 string or URL.
     * Never pass base64 string to Glide - it treats it as a file path and fails.
     */
    private void loadImage(String imageData) {
        if (imageData == null || imageData.isEmpty()) {
            return;
        }
        // Only pass URLs to Glide; decode base64 ourselves
        if (imageData.startsWith("http://") || imageData.startsWith("https://")) {
            Glide.with(this)
                    .load(imageData)
                    .circleCrop()
                    .into(binding.ivProfilePhoto);
            return;
        }
        Bitmap bitmap = ImageUtils.base64ToBitmap(imageData);
        if (bitmap != null) {
            Glide.with(this)
                    .load(bitmap)
                    .circleCrop()
                    .into(binding.ivProfilePhoto);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
