package com.example.tripoo.ui.profile;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import com.bumptech.glide.Glide;
import com.example.tripoo.R;
import com.example.tripoo.data.model.User;
import com.example.tripoo.databinding.FragmentProfileBinding;
import com.example.tripoo.utils.Resource;
import com.example.tripoo.viewmodel.ProfileViewModel;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class ProfileFragment extends Fragment {
    private static final int PICK_IMAGE_REQUEST = 1001;
    private FragmentProfileBinding binding;
    private ProfileViewModel viewModel;
    private String currentPhotoUrl;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentProfileBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        viewModel = new ViewModelProvider(this).get(ProfileViewModel.class);
        
        binding.btnChangePhoto.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            startActivityForResult(intent, PICK_IMAGE_REQUEST);
        });
        
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
        
        viewModel.getUserLiveData().observe(getViewLifecycleOwner(), resource -> {
            if (resource.isSuccess() && resource.getData() != null) {
                User user = resource.getData();
                binding.etName.setText(user.getName());
                binding.etEmail.setText(user.getEmail());
                
                if (user.getPhotoUrl() != null && !user.getPhotoUrl().isEmpty()) {
                    Glide.with(this).load(user.getPhotoUrl()).into(binding.ivProfilePhoto);
                    currentPhotoUrl = user.getPhotoUrl();
                }
            }
        });
        
        viewModel.getUploadImageLiveData().observe(getViewLifecycleOwner(), resource -> {
            if (resource.isSuccess()) {
                currentPhotoUrl = resource.getData();
                Glide.with(this).load(currentPhotoUrl).into(binding.ivProfilePhoto);
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

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == PICK_IMAGE_REQUEST && data != null && data.getData() != null) {
            Uri imageUri = data.getData();
            try {
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(requireContext().getContentResolver(), imageUri);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos);
                byte[] imageData = baos.toByteArray();
                
                viewModel.uploadProfileImage(imageData);
            } catch (IOException e) {
                Toast.makeText(requireContext(), "Failed to load image", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
