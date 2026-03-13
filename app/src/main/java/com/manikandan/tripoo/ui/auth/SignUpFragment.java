package com.manikandan.tripoo.ui.auth;

import android.util.Base64;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.net.Uri;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import com.manikandan.tripoo.R;
import com.manikandan.tripoo.databinding.FragmentSignupBinding;
import com.manikandan.tripoo.viewmodel.AuthViewModel;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public class SignUpFragment extends Fragment {
    private FragmentSignupBinding binding;
    private AuthViewModel viewModel;
    private ActivityResultLauncher<String> pickImageLauncher;
    private String selectedAvatarBase64;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentSignupBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        viewModel = new ViewModelProvider(this).get(AuthViewModel.class);

        setupAvatarPickers();

        if (binding.btnBack != null) {
            binding.btnBack.setOnClickListener(v -> Navigation.findNavController(view).popBackStack());
        }
        if (binding.tvLoginLink != null) {
            binding.tvLoginLink.setOnClickListener(v -> Navigation.findNavController(view).popBackStack());
        }

        // Live initials avatar based on name
        binding.etName.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (selectedAvatarBase64 != null) return;
                String text = s != null ? s.toString().trim() : "";
                if (text.isEmpty()) {
                    if (binding.ivAvatarIcon != null) binding.ivAvatarIcon.setVisibility(View.VISIBLE);
                    if (binding.tvInitials != null) binding.tvInitials.setVisibility(View.GONE);
                    if (binding.avatarRing != null) binding.avatarRing.setBackgroundResource(R.drawable.bg_avatar_ring);
                } else {
                    String initials = text.length() > 0 ? String.valueOf(text.charAt(0)).toUpperCase() : "";
                    if (text.contains(" ") && text.split(" ")[0].length() > 0) {
                        initials = String.valueOf(text.split(" ")[0].charAt(0)).toUpperCase();
                    }
                    if (binding.ivAvatarIcon != null) binding.ivAvatarIcon.setVisibility(View.GONE);
                    if (binding.tvInitials != null) {
                        binding.tvInitials.setText(initials);
                        binding.tvInitials.setVisibility(View.VISIBLE);
                    }
                    if (binding.avatarRing != null) binding.avatarRing.setBackgroundResource(R.drawable.bg_avatar_solid_orange);
                }
            }
        });

        if (binding.avatarRing != null) {
            binding.avatarRing.setOnClickListener(v -> openGallery());
        }
        if (binding.ivAvatarIcon != null) {
            binding.ivAvatarIcon.setOnClickListener(v -> openGallery());
        }
        
        binding.btnSignUp.setOnClickListener(v -> {
            String name = binding.etName.getText().toString().trim();
            String email = binding.etEmail.getText().toString().trim();
            String password = binding.etPassword.getText().toString().trim();
            String confirm = binding.etConfirmPassword.getText().toString().trim();

            viewModel.signUp(name, email, password, confirm, selectedAvatarBase64);
        });

        // Phase 2 style observers shared with AuthFragment
        viewModel.getIsLoading().observe(getViewLifecycleOwner(), loading -> {
            boolean isLoading = loading != null && loading;
            binding.progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            binding.btnSignUp.setEnabled(!isLoading);
        });

        viewModel.getErrorMessage().observe(getViewLifecycleOwner(), msg -> {
            if (msg != null && !msg.isEmpty()) {
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
                viewModel.clearErrorMessage();
            }
        });

        viewModel.getNavigateToDashboard().observe(getViewLifecycleOwner(), go -> {
            if (go != null && go) {
                Navigation.findNavController(view).navigate(R.id.action_signup_to_dashboard);
                viewModel.clearNavigateToDashboard();
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void setupAvatarPickers() {
        pickImageLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri == null) return;
                    showAvatarFromUri(uri);
                }
        );
    }

    private void openGallery() {
        if (pickImageLauncher != null) pickImageLauncher.launch("image/*");
    }

    private void showAvatarFromUri(@NonNull Uri uri) {
        try (InputStream is = requireContext().getContentResolver().openInputStream(uri)) {
            if (is == null) return;
            Bitmap bitmap = BitmapFactory.decodeStream(is);
            if (bitmap == null) return;

            Bitmap scaled = downscale(bitmap, 512);
            selectedAvatarBase64 = bitmapToBase64Jpeg(scaled, 85);

            if (binding.ivAvatarPhoto != null) {
                binding.ivAvatarPhoto.setImageBitmap(scaled);
                binding.ivAvatarPhoto.setVisibility(View.VISIBLE);
            }
            if (binding.ivAvatarIcon != null) binding.ivAvatarIcon.setVisibility(View.GONE);
            if (binding.tvInitials != null) binding.tvInitials.setVisibility(View.GONE);
            if (binding.avatarRing != null) binding.avatarRing.setBackgroundResource(R.drawable.bg_avatar_photo_clip);
        } catch (Exception ignored) {
        }
    }

    private static Bitmap downscale(@NonNull Bitmap src, int maxDim) {
        int w = src.getWidth();
        int h = src.getHeight();
        if (w <= maxDim && h <= maxDim) return src;
        float scale = Math.min((float) maxDim / (float) w, (float) maxDim / (float) h);
        int nw = Math.max(1, Math.round(w * scale));
        int nh = Math.max(1, Math.round(h * scale));
        return Bitmap.createScaledBitmap(src, nw, nh, true);
    }

    private static String bitmapToBase64Jpeg(@NonNull Bitmap bitmap, int quality) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos);
        byte[] bytes = baos.toByteArray();
        return Base64.encodeToString(bytes, Base64.NO_WRAP);
    }
}
