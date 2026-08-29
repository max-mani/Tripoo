package com.manikandan.tripoo.ui.home;

import android.os.Bundle;
import android.os.CountDownTimer;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.app.AlertDialog;
import android.widget.Toast;
import android.widget.PopupMenu;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import com.manikandan.tripoo.MainActivity;
import com.manikandan.tripoo.R;
import com.manikandan.tripoo.ads.TripExitInterstitialHelper;
import com.manikandan.tripoo.data.model.Expense;
import com.manikandan.tripoo.data.model.OutingCategories;
import com.manikandan.tripoo.data.model.Trip;
import com.manikandan.tripoo.data.model.TripMember;
import com.manikandan.tripoo.data.model.User;
import com.manikandan.tripoo.data.repository.ExpenseRepository;
import com.manikandan.tripoo.data.repository.ItineraryRepository;
import com.manikandan.tripoo.data.repository.PollRepository;
import com.manikandan.tripoo.data.repository.TripRepository;
import com.manikandan.tripoo.databinding.FragmentHomeBinding;
import com.manikandan.tripoo.ui.expenses.AddExpenseBottomSheet;
import com.manikandan.tripoo.utils.DateFormatter;
import com.manikandan.tripoo.utils.ImageUtils;
import com.manikandan.tripoo.utils.Resource;
import com.manikandan.tripoo.utils.UserAvatarIdentity;
import com.bumptech.glide.Glide;
import com.manikandan.tripoo.viewmodel.HomeViewModel;
import com.google.android.gms.ads.AdRequest;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;
import androidx.core.content.ContextCompat;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class HomeFragment extends Fragment {
    private FragmentHomeBinding binding;
    private HomeViewModel viewModel;
    private ListenerRegistration expensesListener;
    private ListenerRegistration membersListener;
    private ListenerRegistration itineraryListener;
    private ListenerRegistration pollsListener;
    private CountDownTimer countdownTimer;
    private final TripRepository tripRepository = new TripRepository();
    private final ItineraryRepository itineraryRepository = new ItineraryRepository();
    private final PollRepository pollRepository = new PollRepository();
    private final List<TripMember> outingMembers = new ArrayList<>();
    private final List<com.manikandan.tripoo.data.model.Poll> lastPolls = new ArrayList<>();
    private boolean planningExpanded;
    private String lastPlanningTripId;

    private boolean leavingHome;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        viewModel = new ViewModelProvider(this).get(HomeViewModel.class);

        String argTripId = getArguments() != null ? getArguments().getString("tripId", "") : "";
        if (argTripId != null && !argTripId.isEmpty()) {
            viewModel.loadTrip(argTripId);
            if (binding.llInTrip != null) binding.llInTrip.setVisibility(View.VISIBLE);
        }

        if (binding.btnBackToDashboard != null) {
            binding.btnBackToDashboard.setOnClickListener(v -> goToTripDashboard(view));
        }

        if (binding.btnOutingAddExpense != null) {
            binding.btnOutingAddExpense.setOnClickListener(v -> openOutingAddExpense(view));
        }
        if (binding.btnMore != null) {
            binding.btnMore.setOnClickListener(v -> showMoreMenu(view));
        }
        binding.swipeRefreshHome.setOnRefreshListener(() -> {
            viewModel.refreshUser();
            String tripId = getCurrentTripId();
            if (tripId != null && !tripId.isEmpty()) {
                viewModel.loadTrip(tripId);
            }
            binding.swipeRefreshHome.postDelayed(() -> binding.swipeRefreshHome.setRefreshing(false), 600);
        });

        // Bottom nav: mirror quick access navigation
        setActiveBottomNav("home");
        if (binding.navHome != null) {
            binding.navHome.setOnClickListener(v -> {
                // Already on Home; no navigation needed
            });
        }
        if (binding.navExpenses != null) {
            binding.navExpenses.setOnClickListener(v -> {
                String tripId = getCurrentTripId();
                if (tripId != null) {
                    Bundle args = new Bundle();
                    args.putString("tripId", tripId);
                    Navigation.findNavController(view).navigate(R.id.action_home_to_expenses, args);
                }
            });
        }
        if (binding.navTasks != null) {
            binding.navTasks.setOnClickListener(v -> {
                String tripId = getCurrentTripId();
                if (tripId != null) {
                    Bundle args = new Bundle();
                    args.putString("tripId", tripId);
                    Navigation.findNavController(view).navigate(R.id.action_home_to_tasks, args);
                }
            });
        }
        if (binding.navGroups != null) {
            binding.navGroups.setOnClickListener(v -> {
                String tripId = getCurrentTripId();
                if (tripId != null) {
                    Bundle args = new Bundle();
                    args.putString("tripId", tripId);
                    Navigation.findNavController(view).navigate(R.id.action_home_to_participants, args);
                }
            });
        }

        if (binding.qaItinerary != null) {
            binding.qaItinerary.setOnClickListener(v -> openPlanning(view, R.id.action_home_to_itinerary));
        }
        if (binding.qaPolls != null) {
            binding.qaPolls.setOnClickListener(v -> openPlanning(view, R.id.action_home_to_polls));
        }
        if (binding.qaNotes != null) {
            binding.qaNotes.setOnClickListener(v -> openPlanning(view, R.id.action_home_to_notes));
        }
        if (binding.tvMorePlanning != null) {
            binding.tvMorePlanning.setOnClickListener(v -> {
                planningExpanded = true;
                applyPlanningChrome(true);
                bindActivePoll(lastPolls, view);
            });
        }
        if (binding.cardToday != null) {
            binding.cardToday.setOnClickListener(v -> openPlanning(view, R.id.action_home_to_itinerary));
        }
        if (binding.btnBudgetDetails != null) {
            binding.btnBudgetDetails.setOnClickListener(v -> {
                String tripId = getCurrentTripId();
                if (tripId != null) {
                    Bundle args = new Bundle();
                    args.putString("tripId", tripId);
                    Navigation.findNavController(view).navigate(R.id.action_home_to_expenses, args);
                }
            });
        }

        if (binding.btnViewMaps != null) {
            binding.btnViewMaps.setOnClickListener(v -> {
                Resource<Trip> tripRes = viewModel.getTripLiveData().getValue();
                if (tripRes == null || !tripRes.isSuccess() || tripRes.getData() == null) return;
                String dest = tripRes.getData().getDestination();
                if (dest == null || dest.trim().isEmpty()) return;

                try {
                    String q = Uri.encode(dest.trim());
                    Uri uri = Uri.parse("geo:0,0?q=" + q);
                    Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                    intent.setPackage("com.google.android.apps.maps");
                    startActivity(intent);
                } catch (ActivityNotFoundException e) {
                    // Fall back to any maps handler
                    String q = Uri.encode(dest.trim());
                    Uri uri = Uri.parse("geo:0,0?q=" + q);
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                } catch (Exception ignored) {
                }
            });
        }
        
        final boolean hasArgTripId = argTripId != null && !argTripId.isEmpty();
        viewModel.getUserLiveData().observe(getViewLifecycleOwner(), resource -> {
            if (hasArgTripId) return; // tripId from create/join takes precedence
            if (resource.isSuccess() && resource.getData() != null) {
                User user = resource.getData();
                String activeTripId = user.getLastActiveTripId();
                if (activeTripId != null && !activeTripId.isEmpty()) {
                    viewModel.loadTrip(activeTripId);
                    if (binding.llInTrip != null) binding.llInTrip.setVisibility(View.VISIBLE);
                } else {
                    leaveHomeForDashboard(view);
                }
            }
        });
        
        viewModel.getTripLiveData().observe(getViewLifecycleOwner(), resource -> {
            if (resource == null) return;
            if (resource.isError()) {
                String msg = resource.getMessage();
                if (msg != null && !"Logged out".equals(msg)) {
                    leaveHomeForDashboard(view);
                }
                return;
            }
            if (resource.isSuccess() && resource.getData() != null) {
                Trip trip = resource.getData();
                String tripId = getCurrentTripId();
                String dest = trip.getDestination();
                if (dest == null || dest.trim().isEmpty()) {
                    binding.tvTripPlace.setVisibility(View.GONE);
                } else {
                    binding.tvTripPlace.setVisibility(View.VISIBLE);
                    binding.tvTripPlace.setText(dest);
                }
                String tripDesc = trip.getDescription();
                boolean outing = trip.isOuting();
                if (outing) {
                    String emoji = OutingCategories.emojiForDescription(tripDesc);
                    String name = trip.getName() != null ? trip.getName() : "";
                    binding.tvTripTitle.setText(emoji.isEmpty() ? name : (emoji + "  " + name));
                    if (tripDesc != null && !tripDesc.trim().isEmpty()) {
                        binding.tvTripDescription.setVisibility(View.VISIBLE);
                        binding.tvTripDescription.setText(emoji.isEmpty() ? tripDesc.trim() : (emoji + "  " + tripDesc.trim()));
                    } else {
                        binding.tvTripDescription.setVisibility(View.GONE);
                    }
                    binding.tvTripDates.setVisibility(View.GONE);
                    if (binding.llCountdownHeader != null) binding.llCountdownHeader.setVisibility(View.GONE);
                    binding.llCountdownTiles.setVisibility(View.GONE);
                    binding.tripCountdownView.setVisibility(View.GONE);
                    if (binding.llOutingHero != null) binding.llOutingHero.setVisibility(View.VISIBLE);
                    if (binding.cardMap != null) {
                        binding.cardMap.setVisibility(View.GONE);
                    }
                    stopCountdown();
                    listenToOutingMembers(tripId);
                } else {
                    binding.tvTripTitle.setText(trip.getName() != null ? trip.getName() : "");
                    if (tripDesc != null && !tripDesc.trim().isEmpty()) {
                        binding.tvTripDescription.setVisibility(View.VISIBLE);
                        binding.tvTripDescription.setText(tripDesc.trim());
                    } else {
                        binding.tvTripDescription.setVisibility(View.GONE);
                        binding.tvTripDescription.setText("");
                    }
                    binding.tvTripDates.setVisibility(View.VISIBLE);
                    if (binding.llCountdownHeader != null) binding.llCountdownHeader.setVisibility(View.VISIBLE);
                    if (binding.llOutingHero != null) binding.llOutingHero.setVisibility(View.GONE);
                    if (binding.cardMap != null) binding.cardMap.setVisibility(View.VISIBLE);
                    stopOutingMembers();
                    binding.tripCountdownView.setVisibility(View.GONE);
                    binding.llCountdownTiles.setVisibility(View.VISIBLE);
                    updateCountdownForTrip(trip);
                }
                if (lastPlanningTripId == null || !lastPlanningTripId.equals(tripId)) {
                    planningExpanded = false;
                    lastPlanningTripId = tripId;
                }
                applyPlanningChrome(outing);
                listenPlanning(tripId, outing, view);
                binding.tvMapDestination.setText("Destination: " + (dest != null ? dest : ""));
                if (binding.cardBudget != null) {
                    binding.cardBudget.setVisibility(trip.getBudget() > 0 ? View.VISIBLE : View.GONE);
                }
                if (binding.tvBudgetSharedAcross != null) {
                    int count = 0;
                    if (trip.getMemberIds() != null) count = trip.getMemberIds().size();
                    binding.tvBudgetSharedAcross.setText("Shared across " + count + " people");
                }
                
                String dates = (DateFormatter.formatDate(trip.getStartDate()) + " \u2013 " +
                        DateFormatter.formatDate(trip.getEndDate())).toUpperCase();
                binding.tvTripDates.setText(dates);

                if (expensesListener != null) {
                    expensesListener.remove();
                    expensesListener = null;
                }
                if (tripId != null && !tripId.isEmpty()) {
                    expensesListener = new ExpenseRepository().listenToExpenses(tripId, (list, e) -> {
                        if (e != null) return kotlin.Unit.INSTANCE;
                        double total = 0.0;
                        for (Expense ex : list) {
                            total += ex.getAmount();
                        }
                        viewModel.setTotalExpenses(total);
                        return kotlin.Unit.INSTANCE;
                    });
                }
            }
        });

        viewModel.getDeleteTripLiveData().observe(getViewLifecycleOwner(), res -> {
            if (res == null) return;
            if (res.isSuccess()) {
                Resource<Trip> deletedTrip = viewModel.getTripLiveData().getValue();
                boolean outing = deletedTrip != null && deletedTrip.isSuccess() && deletedTrip.getData() != null && deletedTrip.getData().isOuting();
                Toast.makeText(requireContext(), outing ? "Outing deleted" : "Trip deleted", Toast.LENGTH_SHORT).show();
                try {
                    Navigation.findNavController(view).popBackStack(R.id.tripDashboardFragment, false);
                } catch (Exception e) {
                    Navigation.findNavController(view).navigate(R.id.tripDashboardFragment);
                }
                viewModel.acknowledgeDeleteTripResult();
            } else if (res.isError()) {
                Toast.makeText(requireContext(), res.getMessage() != null ? res.getMessage() : "Delete failed", Toast.LENGTH_SHORT).show();
                viewModel.acknowledgeDeleteTripResult();
            }
        });

        viewModel.getTotalExpensesLiveData().observe(getViewLifecycleOwner(), total -> {
            Resource<Trip> tripRes = viewModel.getTripLiveData().getValue();
            if (tripRes == null || !tripRes.isSuccess() || tripRes.getData() == null) return;
            Trip trip = tripRes.getData();
            if (total == null) return;

            double spent = total;
            double budget = trip.getBudget();
            binding.tvBudgetSpent.setText("₹ " + String.format("%.0f", spent) + " / ₹ " + String.format("%.0f", budget));

            if (budget > 0) {
                double remaining = Math.max(0, budget - spent);
                int pctRemaining = (int) Math.round((remaining / budget) * 100.0);
                binding.tvBudgetRemaining.setText(pctRemaining + "% remaining");

                int progress = (int) Math.round((spent / budget) * 100.0);
                binding.progressBudget.setProgress(Math.min(Math.max(progress, 0), 100));
            } else {
                binding.tvBudgetRemaining.setText("0% remaining");
                binding.progressBudget.setProgress(0);
            }
        });

        binding.adViewTripGroup.loadAd(new AdRequest.Builder().build());

        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(),
                new androidx.activity.OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        goToTripDashboard(view);
                    }
                });
    }

    @Override
    public void onPause() {
        binding.adViewTripGroup.pause();
        super.onPause();
    }

    private void leaveHomeForDashboard(View view) {
        if (leavingHome || !isAdded() || binding == null) return;
        leavingHome = true;
        try {
            if (!Navigation.findNavController(view).popBackStack(R.id.tripDashboardFragment, false)) {
                Navigation.findNavController(view).navigate(R.id.tripDashboardFragment);
            }
        } catch (Exception e) {
            try {
                Navigation.findNavController(view).navigate(R.id.tripDashboardFragment);
            } catch (Exception ignored) {
            }
        }
    }

    private void goToTripDashboard(View view) {
        String tripId = getCurrentTripId();
        TripExitInterstitialHelper.navigateToTripDashboard(requireActivity(), tripId, () -> {
            try {
                if (!Navigation.findNavController(view).popBackStack(R.id.tripDashboardFragment, false)) {
                    Navigation.findNavController(view).navigate(R.id.tripDashboardFragment);
                }
            } catch (Exception e) {
                Navigation.findNavController(view).navigate(R.id.tripDashboardFragment);
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        binding.adViewTripGroup.resume();
        if (getActivity() instanceof MainActivity) {
            String pending = ((MainActivity) getActivity()).consumePendingOpenTripId();
            if (pending != null && !pending.isEmpty()) {
                viewModel.loadTrip(pending);
                if (binding.llInTrip != null) binding.llInTrip.setVisibility(View.VISIBLE);
            }
        }
        String tripId = getCurrentTripId();
        if (tripId != null && !tripId.isEmpty()) {
            TripExitInterstitialHelper.preload(requireContext());
        }
    }

    private void setActiveBottomNav(String tab) {
        if (binding == null) return;
        int orange = ContextCompat.getColor(requireContext(), R.color.tripoo_orange);
        int grey = ContextCompat.getColor(requireContext(), R.color.tripoo_text_hint);

        binding.ivNavHome.setSelected("home".equals(tab));
        binding.ivNavExpenses.setSelected("expenses".equals(tab));
        binding.ivNavTasks.setSelected("tasks".equals(tab));
        binding.ivNavGroups.setSelected("groups".equals(tab));

        binding.tvNavHome.setTextColor("home".equals(tab) ? orange : grey);
        binding.tvNavExpenses.setTextColor("expenses".equals(tab) ? orange : grey);
        binding.tvNavTasks.setTextColor("tasks".equals(tab) ? orange : grey);
        binding.tvNavGroups.setTextColor("groups".equals(tab) ? orange : grey);
    }

    private void showMoreMenu(View rootView) {
        Resource<Trip> tripRes = viewModel.getTripLiveData().getValue();
        if (tripRes == null || !tripRes.isSuccess() || tripRes.getData() == null) return;
        Trip trip = tripRes.getData();

        PopupMenu menu = new PopupMenu(new ContextThemeWrapper(requireContext(), R.style.ThemeOverlay_Tripoo_PopupMenu), binding.btnMore);
        final int MENU_EDIT = 2;
        final int MENU_DELETE = 1;
        Boolean canManage = viewModel.getCanManageTripLiveData().getValue();
        boolean showEdit = Boolean.TRUE.equals(canManage);
        if (showEdit) {
            menu.getMenu().add(0, MENU_EDIT, 0, trip.isOuting() ? "Edit outing" : "Edit trip");
        }
        FirebaseUser organizerCheck = FirebaseAuth.getInstance().getCurrentUser();
        String organiserUid = trip.getAdminId() != null ? trip.getAdminId() : "";
        boolean isOrganiser = organizerCheck != null
                && organizerCheck.getUid() != null
                && organizerCheck.getUid().equals(organiserUid);
        if (isOrganiser) {
            menu.getMenu().add(0, MENU_DELETE, 1, trip.isOuting() ? "Delete outing" : "Delete trip");
        }
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == MENU_EDIT) {
                String tripId = getCurrentTripId();
                if (tripId != null && !tripId.isEmpty()) {
                    Bundle args = new Bundle();
                    args.putString("editTripId", tripId);
                    Navigation.findNavController(rootView).navigate(R.id.action_home_to_edit_trip, args);
                }
                return true;
            }
            if (item.getItemId() == MENU_DELETE) {
                showDeleteTripConfirm();
                return true;
            }
            return false;
        });
        menu.show();
    }

    private void showDeleteTripConfirm() {
        Resource<Trip> tripRes = viewModel.getTripLiveData().getValue();
        boolean isOuting = tripRes != null && tripRes.isSuccess() && tripRes.getData() != null && tripRes.getData().isOuting();
        new AlertDialog.Builder(requireContext())
                .setTitle(isOuting ? "Delete outing?" : "Delete trip?")
                .setMessage(isOuting
                        ? "This will permanently delete the outing for everyone."
                        : "This will permanently delete the trip for everyone.")
                .setNegativeButton("Cancel", (d, w) -> d.dismiss())
                .setPositiveButton("Delete", (d, w) -> {
                    String tripId = getCurrentTripId();
                    if (tripId != null && !tripId.isEmpty()) {
                        viewModel.deleteTrip(tripId);
                    }
                })
                .show();
    }

    private String getCurrentTripId() {
        String argTripId = getArguments() != null ? getArguments().getString("tripId", "") : "";
        if (argTripId != null && !argTripId.isEmpty()) return argTripId;
        Resource<User> r = viewModel.getUserLiveData().getValue();
        if (r != null && r.isSuccess() && r.getData() != null) {
            return r.getData().getLastActiveTripId();
        }
        return null;
    }

    private void updateCountdownForTrip(Trip trip) {
        if (trip == null) return;
        stopCountdown();

        long now = System.currentTimeMillis();
        long start = trip.getStartDate();
        long end = trip.getEndDate();

        if (binding != null && binding.tvCountdownSectionTitle != null) {
            if (now < start) {
                binding.tvCountdownSectionTitle.setText(R.string.home_countdown_upcoming_title);
            } else if (now <= end) {
                binding.tvCountdownSectionTitle.setText(R.string.home_countdown_current_title);
            } else {
                binding.tvCountdownSectionTitle.setText(R.string.home_countdown_past_title);
            }
        }

        if (now < start) {
            startCountDownTo(start);
        } else if (now <= end) {
            startCountDownTo(end);
        } else {
            startCountUpSinceEnd(end);
        }
    }

    private void startCountDownTo(long targetMillis) {
        long now = System.currentTimeMillis();
        long millisLeft = Math.max(0, targetMillis - now);

        countdownTimer = new CountDownTimer(millisLeft, 1000L) {
            @Override
            public void onTick(long ms) {
                bindCountdownFromMillis(ms);
            }

            @Override
            public void onFinish() {
                bindCountdownFromMillis(0);
            }
        };
        countdownTimer.start();
    }

    private void startCountUpSinceEnd(long endMillis) {
        // Freeze at 99 days, 23:59:59
        final long capMs = (99L * 24L * 60L * 60L * 1000L) + (23L * 60L * 60L * 1000L) + (59L * 60L * 1000L) + (59L * 1000L);

        countdownTimer = new CountDownTimer(Long.MAX_VALUE, 1000L) {
            @Override
            public void onTick(long ignored) {
                long elapsed = Math.max(0, System.currentTimeMillis() - endMillis);
                if (elapsed >= capMs) {
                    bindPastFreezeCap();
                    if (countdownTimer != null) {
                        countdownTimer.cancel();
                        countdownTimer = null;
                    }
                    return;
                }
                bindPastCountUp(elapsed);
            }

            @Override
            public void onFinish() {
            }
        };
        countdownTimer.start();
    }

    private void bindCountdownFromMillis(long ms) {
        if (binding == null) return;
        long totalSeconds = Math.max(0, ms / 1000L);
        long days = totalSeconds / 86400L;
        long hours = (totalSeconds % 86400L) / 3600L;
        long mins = (totalSeconds % 3600L) / 60L;
        long secs = totalSeconds % 60L;

        binding.tvCdDays.setText(String.valueOf(days));
        binding.tvCdHours.setText(String.valueOf(hours));
        binding.tvCdMins.setText(String.valueOf(mins));
        binding.tvCdSecs.setText(String.valueOf(secs));
    }

    private void bindPastCountUp(long elapsedMs) {
        if (binding == null) return;
        long totalSeconds = elapsedMs / 1000L;
        long totalHours = totalSeconds / 3600L;
        long mins = (totalSeconds % 3600L) / 60L;
        long secs = totalSeconds % 60L;

        long days = totalHours / 24L;
        long hours = totalHours % 24L;

        binding.tvCdDays.setText(String.valueOf(days));
        binding.tvCdHours.setText(String.valueOf(hours));
        binding.tvCdMins.setText(String.valueOf(mins));
        binding.tvCdSecs.setText(String.valueOf(secs));
    }

    private void bindPastFreezeCap() {
        if (binding == null) return;
        binding.tvCdDays.setText("99");
        binding.tvCdHours.setText("23");
        binding.tvCdMins.setText("59");
        binding.tvCdSecs.setText("59");
    }

    private void listenToOutingMembers(String tripId) {
        stopOutingMembers();
        if (tripId == null || tripId.isEmpty()) return;
        membersListener = tripRepository.listenToTripMembers(tripId, (list, e) -> {
            if (binding == null) return kotlin.Unit.INSTANCE;
            outingMembers.clear();
            if (e == null && list != null) outingMembers.addAll(list);
            bindOutingAvatars();
            return kotlin.Unit.INSTANCE;
        });
    }

    private void stopOutingMembers() {
        if (membersListener != null) {
            membersListener.remove();
            membersListener = null;
        }
        outingMembers.clear();
        if (binding != null && binding.llOutingAvatars != null) {
            binding.llOutingAvatars.removeAllViews();
        }
    }

    private void bindOutingAvatars() {
        if (binding == null || binding.llOutingAvatars == null) return;
        binding.llOutingAvatars.removeAllViews();
        float density = getResources().getDisplayMetrics().density;
        int size = (int) (32 * density);
        int overlap = (int) (8 * density);
        int shown = Math.min(outingMembers.size(), 5);
        for (int i = 0; i < shown; i++) {
            TripMember member = outingMembers.get(i);
            FrameLayout wrap = new FrameLayout(requireContext());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            if (i > 0) lp.setMarginStart(-overlap);
            wrap.setLayoutParams(lp);

            GradientDrawable circle = new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            kotlin.Pair<Integer, Integer> colors = UserAvatarIdentity.INSTANCE.chipColors(member, i);
            circle.setColor(colors.getFirst());
            wrap.setBackground(circle);

            ImageView iv = new ImageView(requireContext());
            iv.setLayoutParams(new FrameLayout.LayoutParams(size, size));
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setVisibility(View.GONE);

            TextView tv = new TextView(requireContext());
            FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(size, size);
            tv.setLayoutParams(tlp);
            tv.setGravity(android.view.Gravity.CENTER);
            tv.setText(String.valueOf(UserAvatarIdentity.INSTANCE.displayLetter(member)));
            tv.setTextColor(colors.getSecond());
            tv.setTextSize(11);

            wrap.addView(iv);
            wrap.addView(tv);

            String photo = member.getPhotoUrl();
            if (photo != null && !photo.isEmpty()) {
                iv.setVisibility(View.VISIBLE);
                tv.setVisibility(View.GONE);
                if (ImageUtils.isBase64Image(photo)) {
                    android.graphics.Bitmap bmp = ImageUtils.base64ToBitmap(photo);
                    if (bmp != null) {
                        iv.setImageBitmap(bmp);
                    } else {
                        iv.setVisibility(View.GONE);
                        tv.setVisibility(View.VISIBLE);
                    }
                } else {
                    Glide.with(this).load(photo).circleCrop().into(iv);
                }
            }
            binding.llOutingAvatars.addView(wrap);
        }
        if (outingMembers.size() > shown) {
            TextView more = new TextView(requireContext());
            LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            mlp.setMarginStart((int) (8 * density));
            more.setLayoutParams(mlp);
            more.setText("+" + (outingMembers.size() - shown));
            more.setTextColor(Color.WHITE);
            more.setTextSize(12);
            binding.llOutingAvatars.addView(more);
        }
    }

    private void openPlanning(View view, int actionId) {
        String tripId = getCurrentTripId();
        if (tripId == null || tripId.isEmpty()) return;
        Bundle args = new Bundle();
        args.putString("tripId", tripId);
        Navigation.findNavController(view).navigate(actionId, args);
    }

    private void applyPlanningChrome(boolean outing) {
        if (binding.tvMorePlanning == null || binding.llPlanningGrid == null) return;
        if (outing && !planningExpanded) {
            binding.llPlanningGrid.setVisibility(View.GONE);
            binding.tvMorePlanning.setVisibility(View.VISIBLE);
            if (binding.cardToday != null) binding.cardToday.setVisibility(View.GONE);
            if (binding.cardActivePoll != null) binding.cardActivePoll.setVisibility(View.GONE);
        } else {
            binding.llPlanningGrid.setVisibility(View.VISIBLE);
            binding.tvMorePlanning.setVisibility(View.GONE);
        }
    }

    private void listenPlanning(String tripId, boolean outing, View view) {
        stopPlanningListeners();
        if (tripId == null || tripId.isEmpty()) return;
        if (!outing) {
            itineraryListener = itineraryRepository.listenToDays(tripId, (days, e) -> {
                if (binding == null || e != null) return kotlin.Unit.INSTANCE;
                bindTodayCard(days);
                return kotlin.Unit.INSTANCE;
            });
        } else if (binding.cardToday != null) {
            binding.cardToday.setVisibility(View.GONE);
        }
        pollsListener = pollRepository.listenToPolls(tripId, (polls, e) -> {
            if (binding == null || e != null) return kotlin.Unit.INSTANCE;
            lastPolls.clear();
            lastPolls.addAll(polls);
            if (outing && !planningExpanded) {
                if (binding.cardActivePoll != null) binding.cardActivePoll.setVisibility(View.GONE);
                return kotlin.Unit.INSTANCE;
            }
            bindActivePoll(polls, view);
            return kotlin.Unit.INSTANCE;
        });
    }

    private void bindTodayCard(java.util.List<com.manikandan.tripoo.data.model.ItineraryDay> days) {
        if (binding.cardToday == null) return;
        long today = ItineraryRepository.startOfLocalDay(System.currentTimeMillis());
        com.manikandan.tripoo.data.model.ItineraryDay match = null;
        for (com.manikandan.tripoo.data.model.ItineraryDay d : days) {
            if (d.getDate() > 0 && ItineraryRepository.startOfLocalDay(d.getDate()) == today) {
                match = d;
                break;
            }
        }
        if (match == null || match.getStops().isEmpty()) {
            binding.cardToday.setVisibility(View.GONE);
            return;
        }
        binding.cardToday.setVisibility(View.VISIBLE);
        com.manikandan.tripoo.data.model.ItineraryStop s0 = match.getStops().get(0);
        String t0 = s0.getTime().isEmpty() ? s0.getTitle() : (s0.getTime() + " · " + s0.getTitle());
        binding.tvTodayLine1.setText(t0);
        if (match.getStops().size() > 1) {
            com.manikandan.tripoo.data.model.ItineraryStop s1 = match.getStops().get(1);
            String t1 = s1.getTime().isEmpty() ? s1.getTitle() : (s1.getTime() + " · " + s1.getTitle());
            binding.tvTodayLine2.setVisibility(View.VISIBLE);
            binding.tvTodayLine2.setText(t1);
        } else {
            binding.tvTodayLine2.setVisibility(View.GONE);
        }
    }

    private void bindActivePoll(java.util.List<com.manikandan.tripoo.data.model.Poll> polls, View view) {
        if (binding.cardActivePoll == null) return;
        com.manikandan.tripoo.data.model.Poll open = null;
        for (com.manikandan.tripoo.data.model.Poll p : polls) {
            if (!p.getClosed()) {
                open = p;
                break;
            }
        }
        if (open == null) {
            binding.cardActivePoll.setVisibility(View.GONE);
            return;
        }
        binding.cardActivePoll.setVisibility(View.VISIBLE);
        binding.tvActivePollQ.setText(open.getQuestion());
        String uid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "";
        Integer mine = open.getVotes().get(uid);
        String pick = "";
        if (mine != null && mine >= 0 && mine < open.getOptions().size()) {
            pick = " · your pick: " + open.getOptions().get(mine);
        }
        binding.tvActivePollMeta.setText(open.getVotes().size() + " votes" + pick);
        com.manikandan.tripoo.data.model.Poll selected = open;
        binding.cardActivePoll.setOnClickListener(v -> {
            String tripId = getCurrentTripId();
            if (tripId == null) return;
            Bundle args = new Bundle();
            args.putString("tripId", tripId);
            args.putString("pollId", selected.getId());
            Navigation.findNavController(view).navigate(R.id.action_home_to_poll_detail, args);
        });
    }

    private void stopPlanningListeners() {
        if (itineraryListener != null) {
            itineraryListener.remove();
            itineraryListener = null;
        }
        if (pollsListener != null) {
            pollsListener.remove();
            pollsListener = null;
        }
    }

    private void openOutingAddExpense(View view) {
        String tripId = getCurrentTripId();
        if (tripId == null || tripId.isEmpty()) return;
        if (outingMembers.isEmpty()) {
            Bundle args = new Bundle();
            args.putString("tripId", tripId);
            Navigation.findNavController(view).navigate(R.id.action_home_to_expenses, args);
            return;
        }
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        String uid = u != null ? u.getUid() : "";
        TripMember current = null;
        for (TripMember m : outingMembers) {
            if (m.getUserId() != null && m.getUserId().equals(uid)) {
                current = m;
                break;
            }
        }
        if (current == null) current = outingMembers.get(0);
        new AddExpenseBottomSheet(outingMembers, current, null, expense -> {
            new ExpenseRepository().addExpense(tripId, expense, err -> {
                if (binding == null) return kotlin.Unit.INSTANCE;
                if (err != null) {
                    Toast.makeText(requireContext(),
                            err.getMessage() != null ? err.getMessage() : "Failed to add expense",
                            Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(requireContext(), "Expense added!", Toast.LENGTH_SHORT).show();
                }
                return kotlin.Unit.INSTANCE;
            });
            return kotlin.Unit.INSTANCE;
        }).show(getChildFragmentManager(), "AddExpense");
    }

    private void stopCountdown() {
        if (countdownTimer != null) {
            countdownTimer.cancel();
            countdownTimer = null;
        }
    }

    @Override
    public void onDestroyView() {
        binding.adViewTripGroup.destroy();
        super.onDestroyView();
        if (expensesListener != null) {
            expensesListener.remove();
            expensesListener = null;
        }
        stopOutingMembers();
        stopPlanningListeners();
        stopCountdown();
        binding = null;
    }
}
