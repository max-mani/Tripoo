package com.example.tripoo;

import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.example.tripoo.databinding.ActivityMainBinding;
import com.example.tripoo.data.model.User;
import com.example.tripoo.utils.ImageUtils;
import com.example.tripoo.utils.ProfileIconDrawable;
import com.example.tripoo.utils.Resource;
import com.example.tripoo.viewmodel.HomeViewModel;
import com.example.tripoo.viewmodel.ProfileViewModel;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {
    private ActivityMainBinding binding;
    private NavController navController;
    private ProfileViewModel profileViewModel;
    private HomeViewModel homeViewModel;
    /** Last known profile photo URL; re-applied when nav destination changes so NavigationUI doesn't overwrite our icon. */
    private String lastProfilePhotoUrl;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Set up toolbar
        setSupportActionBar(binding.toolbar);
        
        // Hide toolbar title
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        binding.toolbar.setTitle("");
        binding.toolbar.setSubtitle("");

        // Set up Navigation Component - wait for view to be laid out
        binding.navHostFragment.post(() -> {
            Fragment navHostFragment = getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
            if (navHostFragment instanceof NavHostFragment) {
                navController = ((NavHostFragment) navHostFragment).getNavController();
            } else {
                navController = Navigation.findNavController(binding.navHostFragment);
            }

            // Set up Bottom Navigation
            BottomNavigationView bottomNav = binding.bottomNavigationView;
            AppBarConfiguration appBarConfiguration = new AppBarConfiguration.Builder(
                    R.id.homeFragment,
                    R.id.expenseFragment,
                    R.id.tasksFragment,
                    R.id.groupsFragment,
                    R.id.profileFragment
            ).build();

            NavigationUI.setupActionBarWithNavController(MainActivity.this, navController, appBarConfiguration);
            NavigationUI.setupWithNavController(bottomNav, navController);

            // Disable icon tint so the profile photo drawable shows actual image colors, not grey/orange tint
            bottomNav.setItemIconTintList(null);

            // Set up profile icon update observer from both ViewModels
            profileViewModel = new ViewModelProvider(MainActivity.this).get(ProfileViewModel.class);
            homeViewModel = new ViewModelProvider(MainActivity.this).get(HomeViewModel.class);
            
            // Observe ProfileViewModel for profile updates
            profileViewModel.getUserLiveData().observe(MainActivity.this, resource -> {
                if (resource.isSuccess() && resource.getData() != null) {
                    User user = resource.getData();
                    lastProfilePhotoUrl = user.getPhotoUrl();
                    updateProfileIcon(bottomNav, lastProfilePhotoUrl);
                }
            });
            
            // Also observe HomeViewModel for initial load
            homeViewModel.getUserLiveData().observe(MainActivity.this, resource -> {
                if (resource.isSuccess() && resource.getData() != null) {
                    User user = resource.getData();
                    lastProfilePhotoUrl = user.getPhotoUrl();
                    updateProfileIcon(bottomNav, lastProfilePhotoUrl);
                }
            });

            // Give the Groups item a bit more width (index 3: Home=0, Expense=1, Tasks=2, Groups=3, Profile=4)
            bottomNav.post(() -> {
                if (bottomNav.getChildCount() > 0) {
                    View menuView = bottomNav.getChildAt(0);
                    if (menuView instanceof ViewGroup) {
                        ViewGroup menu = (ViewGroup) menuView;
                        if (menu.getChildCount() > 3) {
                            View groupsItem = menu.getChildAt(3);
                            int minWidthPx = (int) (84 * getResources().getDisplayMetrics().density);
                            groupsItem.setMinimumWidth(minWidthPx);
                        }
                    }
                }
            });

            // Show/hide bottom navigation based on current destination
            navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
                int destinationId = destination.getId();
                
                // Always hide toolbar title
                if (getSupportActionBar() != null) {
                    getSupportActionBar().setDisplayShowTitleEnabled(false);
                }
                binding.toolbar.setTitle("");
                binding.toolbar.setSubtitle("");
                
                if (destinationId == R.id.splashFragment || 
                    destinationId == R.id.authFragment || 
                    destinationId == R.id.loginFragment || 
                    destinationId == R.id.signUpFragment ||
                    destinationId == R.id.createTripFragment ||
                    destinationId == R.id.joinTripFragment) {
                    bottomNav.setVisibility(View.GONE);
                } else {
                    bottomNav.setVisibility(View.VISIBLE);
                    // Re-apply profile icon when nav is shown (NavigationUI can overwrite custom icons)
                    updateProfileIcon(bottomNav, lastProfilePhotoUrl);
                }
            });
        });
    }

    private static final int PROFILE_ICON_SELECTED_BORDER_COLOR = 0xFFF48C25; // #F48C25

    private void updateProfileIcon(BottomNavigationView bottomNav, String photoUrl) {
        if (bottomNav == null) return;
        Menu menu = bottomNav.getMenu();
        MenuItem profileItem = menu.findItem(R.id.profileFragment);
        if (profileItem == null) return;

        Bitmap bitmap = null;
        if (photoUrl != null && !photoUrl.isEmpty()) {
            if (photoUrl.startsWith("http://") || photoUrl.startsWith("https://")) {
                // Load URL async and then set icon (ensure UI update on main thread)
                Glide.with(this)
                        .asBitmap()
                        .load(photoUrl)
                        .circleCrop()
                        .into(new CustomTarget<Bitmap>(96, 96) {
                            @Override
                            public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                                runOnUiThread(() -> setProfileIconFromBitmap(bottomNav, resource));
                            }
                            @Override
                            public void onLoadCleared(@Nullable Drawable placeholder) {}
                        });
                return;
            }
            // Firestore stores profile image as base64 in photoUrl – try decoding
            bitmap = ImageUtils.base64ToBitmap(photoUrl);
        }

        if (bitmap != null) {
            setProfileIconFromBitmap(bottomNav, bitmap);
        }
    }

    private void setProfileIconFromBitmap(BottomNavigationView bottomNav, Bitmap bitmap) {
        if (bitmap == null || bottomNav == null) return;
        MenuItem profileItem = bottomNav.getMenu().findItem(R.id.profileFragment);
        if (profileItem == null) return;
        StateListDrawable selector = ProfileIconDrawable.createProfileIconSelector(
                getResources(), bitmap, PROFILE_ICON_SELECTED_BORDER_COLOR);
        if (selector != null) {
            selector.mutate();
            selector.setTintList(null);
            int iconSizePx = (int) (28 * getResources().getDisplayMetrics().density);
            selector.setBounds(0, 0, iconSizePx, iconSizePx);
            profileItem.setIcon(selector);
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        return navController.navigateUp() || super.onSupportNavigateUp();
    }
}
