package com.manikandan.tripoo.ui.groups;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
import android.graphics.Bitmap;
import com.bumptech.glide.Glide;
import com.manikandan.tripoo.R;
import com.manikandan.tripoo.data.model.TripMember;
import com.manikandan.tripoo.databinding.ItemMemberBinding;
import com.manikandan.tripoo.utils.ImageUtils;
import com.manikandan.tripoo.utils.UserAvatarIdentity;

import java.util.ArrayList;
import java.util.List;

public class MemberAdapter extends RecyclerView.Adapter<MemberAdapter.MemberViewHolder> {
    public interface MemberMenuListener {
        void onMakeAdmin(@NonNull String userId);

        void onRemoveAdmin(@NonNull String userId);

        void onRemoveMember(@NonNull String userId);
    }

    private List<TripMember> members;
    private String tripCreatorUserId = "";
    private String currentUserId = "";
    @Nullable
    private MemberMenuListener menuListener;

    public MemberAdapter(List<TripMember> members) {
        this.members = members != null ? members : new ArrayList<>();
    }

    public void setTripContext(String tripCreatorUserId, String currentUserId, @Nullable MemberMenuListener listener) {
        this.tripCreatorUserId = tripCreatorUserId != null ? tripCreatorUserId : "";
        this.currentUserId = currentUserId != null ? currentUserId : "";
        this.menuListener = listener;
    }

    @NonNull
    @Override
    public MemberViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemMemberBinding binding = ItemMemberBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new MemberViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull MemberViewHolder holder, int position) {
        holder.bind(members.get(position));
    }

    @Override
    public int getItemCount() {
        return members.size();
    }

    public void updateMembers(List<TripMember> newMembers) {
        this.members = newMembers != null ? newMembers : new ArrayList<>();
        notifyDataSetChanged();
    }

    class MemberViewHolder extends RecyclerView.ViewHolder {
        private final ItemMemberBinding binding;

        MemberViewHolder(@NonNull ItemMemberBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(TripMember member) {
            binding.tvMemberName.setText(member.getName());

            boolean isOrganiser = !tripCreatorUserId.isEmpty() && tripCreatorUserId.equals(member.getUserId());
            boolean isCoAdmin = member.isAdmin() && !isOrganiser;

            if (isOrganiser) {
                binding.tvMemberRole.setText(R.string.groups_role_organiser);
            } else if (isCoAdmin) {
                binding.tvMemberRole.setText(R.string.groups_role_co_organiser);
            } else {
                binding.tvMemberRole.setText(R.string.groups_role_member);
            }

            binding.flOrganiserStar.setVisibility(isOrganiser ? View.VISIBLE : View.GONE);

            boolean iAmOrganiser = !tripCreatorUserId.isEmpty() && tripCreatorUserId.equals(currentUserId);
            boolean targetIsOrganiser = isOrganiser;
            boolean canManage = iAmOrganiser && !targetIsOrganiser && !member.getUserId().equals(currentUserId);
            binding.btnMemberMenu.setVisibility(canManage ? View.VISIBLE : View.GONE);
            binding.btnMemberMenu.setOnClickListener(v -> showMemberMenu(v, member));

            char letterCh = UserAvatarIdentity.INSTANCE.displayLetter(member);
            binding.tvMemberInitials.setText(String.valueOf(letterCh));

            String bgHex = member.getAvatarColorHex();
            int innerColor = parseColorSafe(bgHex, Color.parseColor("#F5F5F4"));
            GradientDrawable circle = new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            circle.setColor(innerColor);
            binding.flAvatarInner.setBackground(circle);
            binding.tvMemberInitials.setTextColor(Color.parseColor("#1C1410"));

            binding.ivMemberPhoto.setVisibility(View.GONE);
            binding.tvMemberInitials.setVisibility(View.VISIBLE);

            String photoUrl = member.getPhotoUrl();
            if (photoUrl != null && !photoUrl.isEmpty()) {
                loadMemberPhoto(photoUrl);
            }
        }

        private void showMemberMenu(View anchor, TripMember member) {
            Context ctx = anchor.getContext();
            Context wrapper = new ContextThemeWrapper(ctx, R.style.ThemeOverlay_Tripoo_PopupMenu);
            PopupMenu popup = new PopupMenu(wrapper, anchor);
            int order = 0;
            if (member.isAdmin() && !tripCreatorUserId.equals(member.getUserId())) {
                popup.getMenu().add(0, 2, order++, ctx.getString(R.string.groups_remove_co_organiser));
            } else if (!member.isAdmin()) {
                popup.getMenu().add(0, 1, order++, ctx.getString(R.string.groups_make_co_organiser));
            }
            popup.getMenu().add(0, 3, order, ctx.getString(R.string.groups_remove_from_trip));
            popup.setOnMenuItemClickListener(item -> {
                if (menuListener == null) return false;
                if (item.getItemId() == 1) {
                    menuListener.onMakeAdmin(member.getUserId());
                    return true;
                }
                if (item.getItemId() == 2) {
                    menuListener.onRemoveAdmin(member.getUserId());
                    return true;
                }
                if (item.getItemId() == 3) {
                    menuListener.onRemoveMember(member.getUserId());
                    return true;
                }
                return false;
            });
            popup.show();
        }

        private int parseColorSafe(String hex, int fallback) {
            if (hex == null || hex.isEmpty()) return fallback;
            try {
                return Color.parseColor(hex);
            } catch (IllegalArgumentException e) {
                return fallback;
            }
        }

        private void loadMemberPhoto(String photoUrl) {
            if (photoUrl == null || photoUrl.isEmpty()) return;

            binding.tvMemberInitials.setVisibility(View.GONE);
            binding.ivMemberPhoto.setVisibility(View.VISIBLE);

            if (photoUrl.startsWith("http://") || photoUrl.startsWith("https://")) {
                Glide.with(binding.getRoot())
                        .load(photoUrl)
                        .into(binding.ivMemberPhoto);
                return;
            }
            Bitmap bitmap = ImageUtils.base64ToBitmap(photoUrl);
            if (bitmap != null) {
                Glide.with(binding.getRoot())
                        .load(bitmap)
                        .into(binding.ivMemberPhoto);
            } else {
                binding.ivMemberPhoto.setVisibility(View.GONE);
                binding.tvMemberInitials.setVisibility(View.VISIBLE);
            }
        }
    }
}
