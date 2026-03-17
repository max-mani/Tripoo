package com.manikandan.tripoo.ui.home;

import android.os.Bundle;
import android.os.CountDownTimer;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import com.manikandan.tripoo.R;
import com.manikandan.tripoo.data.model.Expense;
import com.manikandan.tripoo.data.model.Trip;
import com.manikandan.tripoo.data.model.User;
import com.manikandan.tripoo.data.repository.ExpenseRepository;
import com.manikandan.tripoo.databinding.FragmentHomeBinding;
import com.manikandan.tripoo.utils.DateFormatter;
import com.manikandan.tripoo.utils.Resource;
import com.manikandan.tripoo.viewmodel.HomeViewModel;
import com.google.firebase.firestore.ListenerRegistration;
import androidx.core.content.ContextCompat;

public class HomeFragment extends Fragment {
    private FragmentHomeBinding binding;
    private HomeViewModel viewModel;
    private ListenerRegistration expensesListener;
    private CountDownTimer countdownTimer;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        viewModel = new ViewModelProvider(this).get(HomeViewModel.class);

        // If tripId was passed from create/join, load that trip
        String argTripId = getArguments() != null ? getArguments().getString("tripId", "") : "";
        if (argTripId != null && !argTripId.isEmpty()) {
            viewModel.loadTrip(argTripId);
            binding.llNoTrip.setVisibility(View.GONE);
            binding.llInTrip.setVisibility(View.VISIBLE);
        }
        
        binding.btnCreateTrip.setOnClickListener(v -> 
                Navigation.findNavController(view).navigate(R.id.action_home_to_create_trip));
        binding.btnJoinTrip.setOnClickListener(v -> 
                Navigation.findNavController(view).navigate(R.id.action_home_to_join_trip));

        if (binding.btnBackToDashboard != null) {
            binding.btnBackToDashboard.setOnClickListener(v -> {
                try {
                    Navigation.findNavController(view).popBackStack(R.id.tripDashboardFragment, false);
                } catch (Exception e) {
                    Navigation.findNavController(view).navigate(R.id.tripDashboardFragment);
                }
            });
        }

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

        if (binding.qaExpenses != null) {
            binding.qaExpenses.setOnClickListener(v -> {
                String tripId = getCurrentTripId();
                if (tripId != null) {
                    Bundle args = new Bundle();
                    args.putString("tripId", tripId);
                    Navigation.findNavController(view).navigate(R.id.action_home_to_expenses, args);
                }
            });
        }
        if (binding.qaTasks != null) {
            binding.qaTasks.setOnClickListener(v -> {
                String tripId = getCurrentTripId();
                if (tripId != null) {
                    Bundle args = new Bundle();
                    args.putString("tripId", tripId);
                    Navigation.findNavController(view).navigate(R.id.action_home_to_tasks, args);
                }
            });
        }
        if (binding.qaGroups != null) {
            binding.qaGroups.setOnClickListener(v -> {
                String tripId = getCurrentTripId();
                if (tripId != null) {
                    Bundle args = new Bundle();
                    args.putString("tripId", tripId);
                    Navigation.findNavController(view).navigate(R.id.action_home_to_participants, args);
                }
            });
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
                    binding.llNoTrip.setVisibility(View.GONE);
                    binding.llInTrip.setVisibility(View.VISIBLE);
                } else {
                    binding.llNoTrip.setVisibility(View.VISIBLE);
                    binding.llInTrip.setVisibility(View.GONE);
                }
            }
        });
        
        viewModel.getTripLiveData().observe(getViewLifecycleOwner(), resource -> {
            if (resource.isSuccess() && resource.getData() != null) {
                Trip trip = resource.getData();
                binding.tvTripTitle.setText(trip.getName() != null ? trip.getName() : "");
                binding.tvTripPlace.setText(trip.getDestination());
                binding.tvMapDestination.setText("Destination: " + (trip.getDestination() != null ? trip.getDestination() : ""));
                if (binding.tvBudgetSharedAcross != null) {
                    int count = 0;
                    if (trip.getMemberIds() != null) count = trip.getMemberIds().size();
                    binding.tvBudgetSharedAcross.setText("Shared across " + count + " people");
                }
                
                String dates = (DateFormatter.formatDate(trip.getStartDate()) + " \u2013 " +
                        DateFormatter.formatDate(trip.getEndDate())).toUpperCase();
                binding.tvTripDates.setText(dates);

                // Listen for expenses total for this trip (used by budget UI)
                if (expensesListener != null) {
                    expensesListener.remove();
                    expensesListener = null;
                }
                String tripId = getCurrentTripId();
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
                
                // Countdown should show for upcoming/active/past trips
                binding.tripCountdownView.setVisibility(View.GONE);
                binding.llCountdownTiles.setVisibility(View.VISIBLE);
                updateCountdownForTrip(trip);
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
        
        // Create/Join navigation is handled by CreateTripFragment and JoinTripFragment
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

    private void stopCountdown() {
        if (countdownTimer != null) {
            countdownTimer.cancel();
            countdownTimer = null;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (expensesListener != null) {
            expensesListener.remove();
            expensesListener = null;
        }
        stopCountdown();
        binding = null;
    }
}
