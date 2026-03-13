package com.example.tripoo.ui.auth;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import java.io.IOException;
import java.io.InputStream;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import com.example.tripoo.R;
import com.example.tripoo.databinding.FragmentAuthBinding;
import com.example.tripoo.viewmodel.AuthViewModel;

public class AuthFragment extends Fragment {
    private FragmentAuthBinding binding;
    private AuthViewModel viewModel;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentAuthBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(AuthViewModel.class);

        // Load world + search background illustration from assets/nav_icons/travel.png
        try (InputStream is = requireContext().getAssets().open("nav_icons/travel.png")) {
            Bitmap bitmap = BitmapFactory.decodeStream(is);
            if (bitmap != null && binding.ivHeroBackground != null) {
                binding.ivHeroBackground.setImageBitmap(bitmap);
            }
        } catch (IOException ignored) {
            // If asset is missing, we silently skip; hero still shows gradient.
        }

        if (binding.btnGoogleSignIn != null) binding.btnGoogleSignIn.setVisibility(View.GONE);
        if (binding.btnEmailLogin != null) binding.btnEmailLogin.setVisibility(View.GONE);

        binding.tvSignUp.setOnClickListener(v ->
                Navigation.findNavController(view).navigate(R.id.action_auth_to_signup));

        binding.tvForgotPassword.setOnClickListener(v ->
                Toast.makeText(requireContext(), "Forgot password coming soon", Toast.LENGTH_SHORT).show());

        binding.btnLogin.setOnClickListener(v -> {
            String email = binding.etEmail.getText() != null ? binding.etEmail.getText().toString() : "";
            String password = binding.etPassword.getText() != null ? binding.etPassword.getText().toString() : "";
            viewModel.signIn(email, password);
        });

        binding.tvForgotPassword.setOnClickListener(v -> {
            String email = binding.etEmail.getText() != null ? binding.etEmail.getText().toString() : "";
            viewModel.sendPasswordReset(email);
        });

        // Phase 2 state observers
        viewModel.getIsLoading().observe(getViewLifecycleOwner(), loading -> {
            boolean isLoading = loading != null && loading;
            binding.progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            binding.btnLogin.setEnabled(!isLoading);
        });

        viewModel.getErrorMessage().observe(getViewLifecycleOwner(), msg -> {
            if (msg != null && !msg.isEmpty()) {
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
                viewModel.clearErrorMessage();
            }
        });

        viewModel.getNavigateToDashboard().observe(getViewLifecycleOwner(), go -> {
            if (go != null && go) {
                Navigation.findNavController(view).navigate(R.id.action_auth_to_dashboard);
                viewModel.clearNavigateToDashboard();
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
