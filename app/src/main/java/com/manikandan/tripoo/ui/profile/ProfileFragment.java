package com.manikandan.tripoo.ui.profile;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.core.content.ContextCompat;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import com.bumptech.glide.Glide;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.radiobutton.MaterialRadioButton;
import com.google.firebase.auth.FirebaseAuth;
import com.manikandan.tripoo.R;
import com.manikandan.tripoo.data.model.User;
import com.manikandan.tripoo.databinding.FragmentProfileBinding;
import com.manikandan.tripoo.utils.ImageUtils;
import com.manikandan.tripoo.utils.TripooConstants;
import com.manikandan.tripoo.utils.UserAvatarIdentity;
import com.manikandan.tripoo.viewmodel.ProfileViewModel;

import java.io.IOException;

public class ProfileFragment extends Fragment {
    private static final String PREFS = "tripoo_profile_prefs";
    private static final String KEY_NOTIFY = "notifications_on";

    private static final String LANGUAGE_OPTION = "English";
    private static final String CURRENCY_OPTION = "INR (₹)";

    private FragmentProfileBinding binding;
    private ProfileViewModel viewModel;
    private String currentPhotoUrl;
    private ActivityResultLauncher<String> pickImageLauncher;
    @Nullable
    private User lastUser;

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

        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        binding.switchNotifications.setChecked(prefs.getBoolean(KEY_NOTIFY, true));
        binding.switchNotifications.setOnCheckedChangeListener((buttonView, isChecked) ->
                prefs.edit().putBoolean(KEY_NOTIFY, isChecked).apply());

        binding.btnChangePhoto.setOnClickListener(v -> pickImageLauncher.launch("image/*"));

        binding.rowFullName.setOnClickListener(v -> showEditNameDialog());
        binding.rowEmail.setOnClickListener(v -> showEditEmailDialog());
        binding.rowPhone.setOnClickListener(v -> showEditPhoneDialog());
        binding.rowChangePassword.setOnClickListener(v -> showChangePasswordDialog());

        binding.rowMyTrips.setOnClickListener(v ->
                Navigation.findNavController(view).navigate(R.id.action_profile_to_myTripsList));
        binding.rowTotalSpent.setOnClickListener(v ->
                Navigation.findNavController(view).navigate(R.id.action_profile_to_spendingList));

        binding.rowLanguage.setOnClickListener(v -> showSingleRadioPreferenceDialog(
                "Language", LANGUAGE_OPTION, () -> viewModel.savePreferredLanguage()));

        binding.rowCurrency.setOnClickListener(v -> showSingleRadioPreferenceDialog(
                "Default currency", CURRENCY_OPTION, () -> viewModel.savePreferredCurrency()));

        binding.rowHelp.setOnClickListener(v -> openSupportUrl());
        binding.rowFeedback.setOnClickListener(v -> openSupportUrl());
        binding.rowPrivacy.setOnClickListener(v -> openSupportUrl());

        binding.rowLogout.setOnClickListener(v -> {
            viewModel.signOut();
            Navigation.findNavController(view).navigate(R.id.action_profile_to_auth);
        });

        binding.rowDeleteAccount.setOnClickListener(v -> showDeleteAccountConfirm(view));

        binding.swipeRefreshProfile.setOnRefreshListener(() -> {
            viewModel.refreshUser();
            binding.swipeRefreshProfile.postDelayed(() -> binding.swipeRefreshProfile.setRefreshing(false), 500);
        });

        binding.btnBackToDashboard.setOnClickListener(v ->
                Navigation.findNavController(view).navigate(R.id.action_profile_to_dashboard));

        viewModel.getUserLiveData().observe(getViewLifecycleOwner(), resource -> {
            if (resource.isSuccess() && resource.getData() != null) {
                User user = resource.getData();
                lastUser = user;
                binding.tvNameDisplay.setText(user.getName());
                binding.tvEmailValue.setText(user.getEmail());
                binding.tvProfileName.setText(user.getName());
                binding.tvProfileEmail.setText(user.getEmail());

                String phoneStored = user.getPhoneNumber();
                if (phoneStored != null && !phoneStored.trim().isEmpty()) {
                    binding.tvPhoneValue.setText(phoneStored.trim());
                } else {
                    String phone = FirebaseAuth.getInstance().getCurrentUser() != null
                            ? FirebaseAuth.getInstance().getCurrentUser().getPhoneNumber() : null;
                    if (phone != null && !phone.isEmpty()) {
                        binding.tvPhoneValue.setText(phone);
                    } else {
                        binding.tvPhoneValue.setText("Not set");
                    }
                }

                String lang = user.getPreferredLanguage();
                binding.tvLanguageValue.setText(lang != null && !lang.isEmpty() ? lang : "English");
                String cur = user.getPreferredCurrency();
                binding.tvCurrencyValue.setText(cur != null && !cur.isEmpty() ? cur : "INR (₹)");

                applyAvatarIdentity(user);

                if (user.getPhotoUrl() != null && !user.getPhotoUrl().isEmpty()) {
                    loadImage(user.getPhotoUrl());
                    currentPhotoUrl = user.getPhotoUrl();
                } else {
                    currentPhotoUrl = null;
                    showInitialsAvatar(user);
                }
            }
        });

        viewModel.getProfileStatsLiveData().observe(getViewLifecycleOwner(), stats -> {
            if (stats == null) return;
            binding.tvStatTrips.setText(String.valueOf(stats.getTripCount()));
            binding.tvStatSpent.setText(stats.getSpentCompact());
            binding.tvStatFriends.setText(String.valueOf(stats.getFriendsUnique()));
            binding.tvMyTripsSubtitle.setText(
                    stats.getTripCount() + " trips · " + stats.getActiveTripCount() + " active");
            binding.tvTotalSpentSubtitle.setText(
                    stats.getSpentFullInr() + " across all trips");
        });

        viewModel.getUploadImageLiveData().observe(getViewLifecycleOwner(), resource -> {
            if (resource.isSuccess()) {
                currentPhotoUrl = resource.getData();
                loadImage(currentPhotoUrl);
                String name = binding.tvNameDisplay.getText().toString().trim();
                if (!TextUtils.isEmpty(name)) {
                    viewModel.updateProfile(name, currentPhotoUrl);
                } else {
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

        viewModel.getAccountMessageLiveData().observe(getViewLifecycleOwner(), resource -> {
            if (resource == null) return;
            if (resource.isSuccess() && ProfileViewModel.MSG_ACCOUNT_DELETED.equals(resource.getData())) {
                viewModel.consumeAccountMessage();
                Navigation.findNavController(view).navigate(R.id.action_profile_to_auth);
                return;
            }
            if (resource.isSuccess()) {
                Toast.makeText(requireContext(), resource.getData(), Toast.LENGTH_SHORT).show();
            } else if (resource.isError()) {
                Toast.makeText(requireContext(), resource.getMessage(), Toast.LENGTH_SHORT).show();
            }
            viewModel.consumeAccountMessage();
        });
    }

    private void openSupportUrl() {
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(TripooConstants.SUPPORT_WEB_URL));
        startActivity(i);
    }

    private MaterialAlertDialogBuilder themedDialog() {
        return new MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_Tripoo_MaterialAlertDialog);
    }

    private MaterialAlertDialogBuilder themedDialog(Context ctx) {
        return new MaterialAlertDialogBuilder(ctx, R.style.ThemeOverlay_Tripoo_MaterialAlertDialog);
    }

    /** Theme for EditText / hints / cursor so fields are not stuck with unreadable DayNight defaults. */
    private Context dialogFieldContext() {
        return new ContextThemeWrapper(requireContext(), R.style.ThemeOverlay_Tripoo_AlertDialogField);
    }

    /** Explicit colours so typed text and hints stay readable (DayNight can ignore theme-only hints). */
    @SuppressWarnings("deprecation")
    private void styleProfileDialogField(EditText input) {
        input.setTextColor(ContextCompat.getColor(requireContext(), R.color.tripoo_text_primary));
        input.setHintTextColor(ContextCompat.getColor(requireContext(), R.color.tripoo_text_hint));
    }

    /**
     * One {@link MaterialRadioButton} (always selected); selection cannot be cleared.
     * User confirms with Save (same values as {@link ProfileViewModel} persistence).
     */
    private void showSingleRadioPreferenceDialog(String title, String optionLabel, Runnable onSave) {
        View root = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_profile_single_radio, null, false);
        RadioGroup group = root.findViewById(R.id.radioGroup);
        MaterialRadioButton radio = root.findViewById(R.id.radioSingleOption);
        radio.setText(optionLabel);
        int black = ContextCompat.getColor(requireContext(), R.color.black);
        radio.setTextColor(new ColorStateList(
                new int[][]{
                        new int[]{android.R.attr.state_checked},
                        new int[]{-android.R.attr.state_checked},
                },
                new int[]{black, black}));
        radio.setChecked(true);
        group.setOnCheckedChangeListener((g, checkedId) -> {
            if (checkedId == View.NO_ID) {
                radio.setChecked(true);
            }
        });
        radio.setOnClickListener(v -> radio.setChecked(true));

        themedDialog()
                .setTitle(title)
                .setView(root)
                .setPositiveButton(R.string.save, (d, w) -> {
                    onSave.run();
                    Toast.makeText(requireContext(), "Saved", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showEditNameDialog() {
        EditText input = new EditText(dialogFieldContext());
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        input.setText(binding.tvNameDisplay.getText());
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad / 2, pad, pad / 2);
        styleProfileDialogField(input);
        themedDialog()
                .setTitle("Full name")
                .setView(input)
                .setPositiveButton(R.string.save, (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(requireContext(), "Name cannot be empty", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    viewModel.updateProfile(name, currentPhotoUrl);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showEditEmailDialog() {
        EditText input = new EditText(dialogFieldContext());
        input.setInputType(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        input.setText(binding.tvEmailValue.getText());
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad / 2, pad, pad / 2);
        styleProfileDialogField(input);
        themedDialog()
                .setTitle("Email")
                .setView(input)
                .setPositiveButton(R.string.save, (d, w) ->
                        viewModel.updateEmail(input.getText().toString()))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showEditPhoneDialog() {
        EditText input = new EditText(dialogFieldContext());
        input.setInputType(InputType.TYPE_CLASS_PHONE);
        CharSequence cur = binding.tvPhoneValue.getText();
        if (!"Not set".contentEquals(cur)) {
            input.setText(cur);
        }
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad / 2, pad, pad / 2);
        styleProfileDialogField(input);
        themedDialog()
                .setTitle("Phone number")
                .setView(input)
                .setPositiveButton(R.string.save, (d, w) ->
                        viewModel.updatePhone(input.getText().toString().trim()))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showChangePasswordDialog() {
        Context ctx = requireContext();
        Context fieldCtx = dialogFieldContext();
        LinearLayout layout = new LinearLayout(fieldCtx);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad / 2, pad, pad / 2);
        EditText oldP = new EditText(fieldCtx);
        oldP.setHint("Current password");
        oldP.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        EditText newP = new EditText(fieldCtx);
        newP.setHint("New password");
        newP.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        EditText newP2 = new EditText(fieldCtx);
        newP2.setHint("Confirm new password");
        newP2.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        styleProfileDialogField(oldP);
        styleProfileDialogField(newP);
        styleProfileDialogField(newP2);
        layout.addView(oldP);
        layout.addView(newP);
        layout.addView(newP2);
        themedDialog(ctx)
                .setTitle("Change password")
                .setView(layout)
                .setPositiveButton(R.string.save, (d, w) -> {
                    String o = oldP.getText().toString();
                    String n = newP.getText().toString();
                    String n2 = newP2.getText().toString();
                    if (n.length() < 6) {
                        Toast.makeText(ctx, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (!n.equals(n2)) {
                        Toast.makeText(ctx, "New passwords do not match", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    viewModel.updatePassword(o, n);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showDeleteAccountConfirm(View view) {
        themedDialog()
                .setTitle("Delete account?")
                .setMessage("This will remove you from all trips, delete your Ulla profile data, and delete your sign-in account. This cannot be undone.")
                .setPositiveButton("Delete", (d, w) -> viewModel.deleteAccount())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void applyAvatarIdentity(@NonNull User user) {
        String seed = user.getUid() != null && !user.getUid().isEmpty() ? user.getUid() : user.getEmail();
        String bgHex = user.getAvatarColorHex();
        if (bgHex == null || bgHex.isEmpty()) {
            bgHex = UserAvatarIdentity.INSTANCE.bgForSeed(seed);
        }
        String textHex = UserAvatarIdentity.INSTANCE.textColorForBackgroundHex(user.getAvatarColorHex(), seed);
        int bg = parseColorSafe(bgHex, Color.parseColor("#F5F5F4"));
        int tc = parseColorSafe(textHex, Color.parseColor("#57534D"));
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(bg);
        binding.flAvatarColorInner.setBackground(circle);
        binding.tvProfileInitials.setTextColor(tc);
    }

    private static int parseColorSafe(String hex, int fallback) {
        if (hex == null || hex.isEmpty()) return fallback;
        try {
            return Color.parseColor(hex);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private void showInitialsAvatar(@NonNull User user) {
        binding.ivProfilePhoto.setVisibility(View.GONE);
        binding.tvProfileInitials.setVisibility(View.VISIBLE);
        applyAvatarIdentity(user);
        binding.tvProfileInitials.setText(initialsFromName(user.getName()));
    }

    private static String initialsFromName(String name) {
        if (name == null || name.trim().isEmpty()) return "?";
        String[] p = name.trim().split("\\s+");
        if (p.length >= 2) {
            return ("" + p[0].charAt(0) + p[p.length - 1].charAt(0)).toUpperCase();
        }
        String t = p[0];
        return t.length() >= 2 ? t.substring(0, 2).toUpperCase() : t.toUpperCase();
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

    private void loadImage(String imageData) {
        if (imageData == null || imageData.isEmpty()) {
            return;
        }
        binding.ivProfilePhoto.setVisibility(View.VISIBLE);
        binding.tvProfileInitials.setVisibility(View.GONE);
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
        } else {
            binding.ivProfilePhoto.setVisibility(View.GONE);
            binding.tvProfileInitials.setVisibility(View.VISIBLE);
            if (lastUser != null) {
                showInitialsAvatar(lastUser);
            } else {
                binding.tvProfileInitials.setText("?");
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
