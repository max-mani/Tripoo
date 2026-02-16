package com.example.tripoo;

import android.os.Bundle;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import com.example.tripoo.databinding.ActivityMainBinding;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {
    private ActivityMainBinding binding;
    private NavController navController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Set up toolbar
        setSupportActionBar(binding.toolbar);

        // Set up Navigation Component
        navController = Navigation.findNavController(this, R.id.nav_host_fragment);

        // Set up Bottom Navigation
        BottomNavigationView bottomNav = binding.bottomNavigationView;
        AppBarConfiguration appBarConfiguration = new AppBarConfiguration.Builder(
                R.id.homeFragment,
                R.id.expenseFragment,
                R.id.tasksFragment,
                R.id.groupsFragment
        ).build();

        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);
        NavigationUI.setupWithNavController(bottomNav, navController);

        // Profile icon click handler
        binding.ivProfileIcon.setOnClickListener(v -> {
            navController.navigate(R.id.profileFragment);
        });

        // Show/hide bottom navigation based on current destination
        navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
            int destinationId = destination.getId();
            if (destinationId == R.id.splashFragment || 
                destinationId == R.id.authFragment || 
                destinationId == R.id.loginFragment || 
                destinationId == R.id.signUpFragment ||
                destinationId == R.id.createTripFragment ||
                destinationId == R.id.joinTripFragment ||
                destinationId == R.id.profileFragment) {
                bottomNav.setVisibility(View.GONE);
            } else {
                bottomNav.setVisibility(View.VISIBLE);
            }
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        return navController.navigateUp() || super.onSupportNavigateUp();
    }
}