package com.manikandan.tripoo.ui.auth;

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
import com.manikandan.tripoo.R;
import com.manikandan.tripoo.databinding.FragmentLoginBinding;
import com.manikandan.tripoo.utils.Resource;
import com.manikandan.tripoo.viewmodel.AuthViewModel;
import com.google.firebase.auth.FirebaseUser;

public class LoginFragment extends Fragment {
    private FragmentLoginBinding binding;
    private AuthViewModel viewModel;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentLoginBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        viewModel = new ViewModelProvider(this).get(AuthViewModel.class);
        
        binding.btnLogin.setOnClickListener(v -> {
            String email = binding.etEmail.getText().toString().trim();
            String password = binding.etPassword.getText().toString().trim();
            
            if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
                Toast.makeText(requireContext(), "Please fill all fields", Toast.LENGTH_SHORT).show();
                return;
            }
            
            viewModel.signInWithEmailPassword(email, password);
        });
        
        viewModel.getAuthResultLiveData().observe(getViewLifecycleOwner(), resource -> {
            if (resource.isLoading()) {
                binding.progressBar.setVisibility(View.VISIBLE);
                binding.btnLogin.setEnabled(false);
            } else {
                binding.progressBar.setVisibility(View.GONE);
                binding.btnLogin.setEnabled(true);
                
                if (resource.isSuccess()) {
                    Navigation.findNavController(view).navigate(R.id.action_login_to_dashboard);
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
