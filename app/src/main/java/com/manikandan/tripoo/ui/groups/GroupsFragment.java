package com.manikandan.tripoo.ui.groups;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.core.content.ContextCompat;
import com.google.android.gms.ads.AdRequest;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.manikandan.tripoo.R;
import com.manikandan.tripoo.ads.TripExitInterstitialHelper;
import com.manikandan.tripoo.data.model.LeaveTripResult;
import com.manikandan.tripoo.data.model.Trip;
import com.manikandan.tripoo.data.model.TripMember;
import com.manikandan.tripoo.data.model.User;
import com.manikandan.tripoo.databinding.FragmentGroupsBinding;
import com.manikandan.tripoo.utils.DateFormatter;
import com.manikandan.tripoo.utils.Resource;
import com.manikandan.tripoo.viewmodel.GroupsViewModel;
import com.manikandan.tripoo.viewmodel.HomeViewModel;

import java.util.ArrayList;
import java.util.List;

public class GroupsFragment extends Fragment implements MemberAdapter.MemberMenuListener {
    private FragmentGroupsBinding binding;
    private GroupsViewModel groupsViewModel;
    private HomeViewModel homeViewModel;
    private MemberAdapter adapter;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentGroupsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        groupsViewModel = new ViewModelProvider(this).get(GroupsViewModel.class);
        homeViewModel = new ViewModelProvider(requireActivity()).get(HomeViewModel.class);

        adapter = new MemberAdapter(new ArrayList<>());
        binding.rvMembers.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvMembers.setAdapter(adapter);

        String tripIdForScreen = getCurrentTripId();
        if (tripIdForScreen != null && !tripIdForScreen.isEmpty()) {
            homeViewModel.loadTrip(tripIdForScreen);
        }

        binding.swipeRefreshGroups.setOnRefreshListener(() -> {
            String tripId = getCurrentTripId();
            if (tripId != null && !tripId.isEmpty()) {
                groupsViewModel.loadTripAndMembers(tripId);
            }
            binding.swipeRefreshGroups.postDelayed(() -> binding.swipeRefreshGroups.setRefreshing(false), 500);
        });

        binding.btnCopyCode.setOnClickListener(v -> {
            String tripCode = binding.tvTripCode.getText().toString();
            ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Trip Code", tripCode);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(requireContext(), "Trip code copied!", Toast.LENGTH_SHORT).show();
        });

        binding.btnBack.setOnClickListener(v -> navigateToTripDashboard(view));

        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                navigateToTripDashboard(view);
            }
        });

        binding.btnMore.setOnClickListener(v -> showMoreMenu(view));

        binding.btnInvite.setOnClickListener(v -> openAddPeople());

        binding.btnLeaveTrip.setOnClickListener(v -> confirmLeaveTrip(view));

        groupsViewModel.getLeaveTripResult().observe(getViewLifecycleOwner(), result -> {
            if (result == null) return;
            if (result instanceof LeaveTripResult.Success) {
                String tripId = getCurrentTripId();
                if (tripId != null && !tripId.isEmpty()) {
                    groupsViewModel.removeTripFromCurrentUser(tripId, () -> requireActivity().runOnUiThread(() -> {
                        homeViewModel.refreshUser();
                        try {
                            if (!Navigation.findNavController(view).popBackStack(R.id.tripDashboardFragment, false)) {
                                Navigation.findNavController(view).navigate(R.id.tripDashboardFragment);
                            }
                        } catch (Exception e) {
                            Navigation.findNavController(view).navigate(R.id.tripDashboardFragment);
                        }
                    }));
                }
                groupsViewModel.clearLeaveResult();
            } else if (result instanceof LeaveTripResult.MustTransferAdmin) {
                LeaveTripResult.MustTransferAdmin mta = (LeaveTripResult.MustTransferAdmin) result;
                showTransferAdminDialog(view, mta.getOtherMembers());
                groupsViewModel.clearLeaveResult();
            } else if (result instanceof LeaveTripResult.LastMember) {
                Toast.makeText(requireContext(), R.string.groups_last_member, Toast.LENGTH_LONG).show();
                groupsViewModel.clearLeaveResult();
            }
        });

        groupsViewModel.getLeaveTripError().observe(getViewLifecycleOwner(), msg -> {
            if (msg != null && !msg.isEmpty()) {
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
            }
        });

        groupsViewModel.getAdminMutationError().observe(getViewLifecycleOwner(), msg -> {
            if (msg != null && !msg.isEmpty()) {
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
            }
        });

        homeViewModel.getDeleteTripLiveData().observe(getViewLifecycleOwner(), res -> {
            if (res == null) return;
            if (res.isSuccess()) {
                Toast.makeText(requireContext(), "Trip deleted", Toast.LENGTH_SHORT).show();
                try {
                    Navigation.findNavController(view).popBackStack(R.id.tripDashboardFragment, false);
                } catch (Exception e) {
                    Navigation.findNavController(view).navigate(R.id.tripDashboardFragment);
                }
                homeViewModel.acknowledgeDeleteTripResult();
            } else if (res.isError()) {
                Toast.makeText(requireContext(), res.getMessage() != null ? res.getMessage() : "Delete failed", Toast.LENGTH_SHORT).show();
                homeViewModel.acknowledgeDeleteTripResult();
            }
        });

        setActiveBottomNav("groups");
        if (binding.navHome != null) {
            binding.navHome.setOnClickListener(v -> {
                String tripId = getCurrentTripId();
                if (tripId != null) {
                    Bundle args = new Bundle();
                    args.putString("tripId", tripId);
                    Navigation.findNavController(view).navigate(R.id.homeFragment, args);
                } else {
                    Navigation.findNavController(view).navigate(R.id.homeFragment);
                }
            });
        }
        if (binding.navExpenses != null) {
            binding.navExpenses.setOnClickListener(v -> {
                String tripId = getCurrentTripId();
                if (tripId != null) {
                    Bundle args = new Bundle();
                    args.putString("tripId", tripId);
                    Navigation.findNavController(view).navigate(R.id.expensesFragment, args);
                }
            });
        }
        if (binding.navTasks != null) {
            binding.navTasks.setOnClickListener(v -> {
                String tripId = getCurrentTripId();
                if (tripId != null) {
                    Bundle args = new Bundle();
                    args.putString("tripId", tripId);
                    Navigation.findNavController(view).navigate(R.id.tasksFragment, args);
                }
            });
        }
        if (binding.navGroups != null) {
            binding.navGroups.setOnClickListener(v -> { });
        }

        if (tripIdForScreen != null && !tripIdForScreen.isEmpty()) {
            groupsViewModel.loadTripAndMembers(tripIdForScreen);
        } else {
            homeViewModel.getUserLiveData().observe(getViewLifecycleOwner(), resource -> {
                if (resource.isSuccess() && resource.getData() != null) {
                    User user = resource.getData();
                    String tripId = user.getLastActiveTripId();
                    if (tripId != null && !tripId.isEmpty()) {
                        homeViewModel.loadTrip(tripId);
                        groupsViewModel.loadTripAndMembers(tripId);
                    }
                }
            });
        }

        groupsViewModel.getTripLiveData().observe(getViewLifecycleOwner(), resource -> {
            if (resource.isSuccess() && resource.getData() != null) {
                Trip trip = resource.getData();
                binding.tvTripCode.setText(trip.getJoinCode());
                if (binding.tvTripTitle != null) {
                    binding.tvTripTitle.setText(trip.getName() != null && !trip.getName().isEmpty() ? trip.getName() : getString(R.string.app_name));
                }
            }
            refreshHeaderAndAdapterContext();
        });

        groupsViewModel.getMembersLiveData().observe(getViewLifecycleOwner(), resource -> {
            if (resource.isSuccess() && resource.getData() != null) {
                adapter.updateMembers(resource.getData());
            }
            refreshHeaderAndAdapterContext();
        });

        binding.adViewTripGroup.loadAd(new AdRequest.Builder().build());
    }

    private void refreshHeaderAndAdapterContext() {
        if (binding == null) return;
        Resource<Trip> tr = groupsViewModel.getTripLiveData().getValue();
        Resource<List<TripMember>> mr = groupsViewModel.getMembersLiveData().getValue();
        Trip trip = (tr != null && tr.isSuccess()) ? tr.getData() : null;
        List<TripMember> members = (mr != null && mr.isSuccess() && mr.getData() != null) ? mr.getData() : null;
        int n = members != null ? members.size() : 0;
        String dates = "";
        if (trip != null) {
            dates = (DateFormatter.formatDate(trip.getStartDate()) + " \u2013 " +
                    DateFormatter.formatDate(trip.getEndDate())).toUpperCase();
        }
        if (binding.tvTripSubtitle != null) {
            binding.tvTripSubtitle.setText(getString(R.string.groups_header_subtitle_fmt, n, dates));
        }
        if (binding.tvMembersHeader != null) {
            binding.tvMembersHeader.setText(getString(R.string.groups_members_header_fmt, n));
        }
        FirebaseUser fu = FirebaseAuth.getInstance().getCurrentUser();
        String uid = fu != null ? fu.getUid() : "";
        String creator = trip != null && trip.getAdminId() != null ? trip.getAdminId() : "";
        adapter.setTripContext(creator, uid, this);
    }

    private void showMoreMenu(View rootView) {
        Resource<Trip> tripRes = groupsViewModel.getTripLiveData().getValue();
        if (tripRes == null || !tripRes.isSuccess() || tripRes.getData() == null) return;
        Trip trip = tripRes.getData();

        PopupMenu menu = new PopupMenu(new ContextThemeWrapper(requireContext(), R.style.ThemeOverlay_Tripoo_PopupMenu), binding.btnMore);
        final int MENU_EDIT = 2;
        final int MENU_DELETE = 1;
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String uid = user != null ? user.getUid() : "";
        boolean isOrganiser = !uid.isEmpty() && trip.getAdminId() != null && trip.getAdminId().equals(uid);
        Resource<List<TripMember>> mr = groupsViewModel.getMembersLiveData().getValue();
        List<TripMember> members = (mr != null && mr.isSuccess() && mr.getData() != null) ? mr.getData() : null;
        boolean isCoOrganiser = false;
        if (members != null && !uid.isEmpty()) {
            for (TripMember m : members) {
                if (uid.equals(m.getUserId()) && m.isAdmin()) {
                    isCoOrganiser = true;
                    break;
                }
            }
        }
        if (isOrganiser || isCoOrganiser) {
            menu.getMenu().add(0, MENU_EDIT, 0, "Edit trip");
        }
        if (isOrganiser) {
            menu.getMenu().add(0, MENU_DELETE, 1, "Delete trip");
        }
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == MENU_EDIT) {
                String tripId = getCurrentTripId();
                if (tripId != null && !tripId.isEmpty()) {
                    Bundle args = new Bundle();
                    args.putString("editTripId", tripId);
                    Navigation.findNavController(rootView).navigate(R.id.createTripFragment, args);
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
        new AlertDialog.Builder(requireContext())
                .setTitle("Delete trip?")
                .setMessage("This will permanently delete the trip for everyone.")
                .setNegativeButton("Cancel", (d, w) -> d.dismiss())
                .setPositiveButton("Delete", (d, w) -> {
                    String tripId = getCurrentTripId();
                    if (tripId != null && !tripId.isEmpty()) {
                        homeViewModel.deleteTrip(tripId);
                    }
                })
                .show();
    }

    private void openAddPeople() {
        Resource<Trip> tr = groupsViewModel.getTripLiveData().getValue();
        Resource<List<TripMember>> mr = groupsViewModel.getMembersLiveData().getValue();
        if (tr == null || !tr.isSuccess() || tr.getData() == null) {
            shareTripCode();
            return;
        }
        Trip trip = tr.getData();
        String tripId = trip.getId() != null && !trip.getId().isEmpty() ? trip.getId() : getCurrentTripId();
        if (tripId == null || tripId.isEmpty()) return;
        java.util.ArrayList<String> exclude = new java.util.ArrayList<>();
        if (mr != null && mr.isSuccess() && mr.getData() != null) {
            for (TripMember m : mr.getData()) {
                if (m.getUserId() != null && !m.getUserId().isEmpty()) exclude.add(m.getUserId());
            }
        }
        String name = trip.getName() != null ? trip.getName() : "";
        String code = trip.getJoinCode() != null ? trip.getJoinCode() : binding.tvTripCode.getText().toString();
        com.manikandan.tripoo.ui.people.AddPeopleBottomSheet.newInstance(tripId, name, code, exclude)
                .show(getChildFragmentManager(), "add_people");
    }

    private void shareTripCode() {
        String code = binding.tvTripCode.getText().toString();
        if (code == null || code.isEmpty()) return;
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT, getString(R.string.groups_invite_share, code));
        startActivity(Intent.createChooser(send, getString(R.string.more_options)));
    }

    private void confirmLeaveTrip(View view) {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.groups_leave_confirm_title)
                .setMessage(R.string.groups_leave_confirm_message)
                .setNegativeButton(android.R.string.cancel, (d, w) -> d.dismiss())
                .setPositiveButton("Leave", (d, w) -> {
                    String tripId = getCurrentTripId();
                    if (tripId != null && !tripId.isEmpty()) {
                        groupsViewModel.leaveTrip(tripId);
                    }
                })
                .show();
    }

    private void showTransferAdminDialog(View view, List<TripMember> others) {
        if (others == null || others.isEmpty()) {
            Toast.makeText(requireContext(), R.string.groups_last_member, Toast.LENGTH_SHORT).show();
            return;
        }
        String[] labels = new String[others.size()];
        for (int i = 0; i < others.size(); i++) {
            labels[i] = others.get(i).getName();
        }
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.groups_transfer_title)
                .setMessage(R.string.groups_transfer_message)
                .setItems(labels, (d, which) -> {
                    String tripId = getCurrentTripId();
                    if (tripId == null) return;
                    TripMember pick = others.get(which);
                    new AlertDialog.Builder(requireContext())
                            .setTitle(R.string.groups_transfer_title)
                            .setMessage(getString(R.string.groups_transfer_confirm_pick, pick.getName()))
                            .setNegativeButton(android.R.string.cancel, (d2, w) -> d2.dismiss())
                            .setPositiveButton(android.R.string.ok, (d2, w) ->
                                    groupsViewModel.transferAdminAndLeave(tripId, pick.getUserId()))
                            .show();
                })
                .setNegativeButton("Cancel", (d, w) -> d.dismiss())
                .show();
    }

    @Override
    public void onMakeAdmin(@NonNull String userId) {
        String tripId = getCurrentTripId();
        Resource<Trip> tr = groupsViewModel.getTripLiveData().getValue();
        if (tripId == null || tr == null || !tr.isSuccess() || tr.getData() == null) return;
        groupsViewModel.setMemberAdminRole(tripId, userId, true, tr.getData().getAdminId());
    }

    @Override
    public void onRemoveAdmin(@NonNull String userId) {
        String tripId = getCurrentTripId();
        Resource<Trip> tr = groupsViewModel.getTripLiveData().getValue();
        if (tripId == null || tr == null || !tr.isSuccess() || tr.getData() == null) return;
        groupsViewModel.setMemberAdminRole(tripId, userId, false, tr.getData().getAdminId());
    }

    @Override
    public void onRemoveMember(@NonNull String userId) {
        String tripId = getCurrentTripId();
        Resource<Trip> tr = groupsViewModel.getTripLiveData().getValue();
        Resource<List<TripMember>> mr = groupsViewModel.getMembersLiveData().getValue();
        if (tripId == null || tr == null || !tr.isSuccess() || tr.getData() == null) return;
        String name = userId;
        if (mr != null && mr.isSuccess() && mr.getData() != null) {
            for (TripMember m : mr.getData()) {
                if (userId.equals(m.getUserId())) {
                    name = m.getName() != null ? m.getName() : userId;
                    break;
                }
            }
        }
        String finalName = name;
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.groups_remove_member_title)
                .setMessage(getString(R.string.groups_remove_member_message, finalName))
                .setNegativeButton(android.R.string.cancel, (d, w) -> d.dismiss())
                .setPositiveButton(R.string.groups_remove_from_trip, (d, w) ->
                        groupsViewModel.removeMemberFromTrip(tripId, userId, tr.getData().getAdminId()))
                .show();
    }

    @Override
    public void onPause() {
        binding.adViewTripGroup.pause();
        super.onPause();
    }

    private void navigateToTripDashboard(View anchor) {
        String tripId = getCurrentTripId();
        TripExitInterstitialHelper.navigateToTripDashboard(requireActivity(), tripId, () -> {
            try {
                if (!Navigation.findNavController(anchor).popBackStack(R.id.tripDashboardFragment, false)) {
                    Navigation.findNavController(anchor).navigate(R.id.tripDashboardFragment);
                }
            } catch (Exception e) {
                Navigation.findNavController(anchor).navigate(R.id.tripDashboardFragment);
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        binding.adViewTripGroup.resume();
        String tripId = getCurrentTripId();
        if (tripId != null && !tripId.isEmpty()) {
            TripExitInterstitialHelper.preload(requireContext());
        }
    }

    private String getCurrentTripId() {
        String argTripId = getArguments() != null ? getArguments().getString("tripId", "") : "";
        if (argTripId != null && !argTripId.isEmpty()) return argTripId;
        Resource<User> r = homeViewModel != null ? homeViewModel.getUserLiveData().getValue() : null;
        if (r != null && r.isSuccess() && r.getData() != null) {
            return r.getData().getLastActiveTripId();
        }
        return null;
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

    @Override
    public void onDestroyView() {
        binding.adViewTripGroup.destroy();
        super.onDestroyView();
        binding = null;
    }
}
