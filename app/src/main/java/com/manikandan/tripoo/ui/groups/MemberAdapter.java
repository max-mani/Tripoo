package com.manikandan.tripoo.ui.groups;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import android.graphics.Bitmap;
import com.bumptech.glide.Glide;
import com.manikandan.tripoo.R;
import com.manikandan.tripoo.data.model.TripMember;
import com.manikandan.tripoo.databinding.ItemMemberBinding;
import com.manikandan.tripoo.utils.ImageUtils;

import java.util.ArrayList;
import java.util.List;

public class MemberAdapter extends RecyclerView.Adapter<MemberAdapter.MemberViewHolder> {
    private List<TripMember> members;

    public MemberAdapter(List<TripMember> members) {
        this.members = members != null ? members : new ArrayList<>();
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
        private ItemMemberBinding binding;

        public MemberViewHolder(@NonNull ItemMemberBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(TripMember member) {
            binding.tvMemberName.setText(member.getName());
            binding.tvMemberEmail.setText(member.getEmail());

            // Admin chip visibility
            if (member.isAdmin()) {
                binding.chipAdmin.setVisibility(android.view.View.VISIBLE);
            } else {
                binding.chipAdmin.setVisibility(android.view.View.GONE);
            }

            // Default to initials avatar
            String initials = getInitials(member.getName());
            if (binding.tvMemberInitials != null) {
                binding.tvMemberInitials.setText(initials);
                binding.tvMemberInitials.setVisibility(android.view.View.VISIBLE);
            }
            if (binding.ivMemberPhoto != null) {
                binding.ivMemberPhoto.setImageDrawable(null);
                binding.ivMemberPhoto.setVisibility(android.view.View.GONE);
            }

            // If there is a photoUrl, try to show photo instead of initials
            String photoUrl = member.getPhotoUrl();
            if (photoUrl != null && !photoUrl.isEmpty()) {
                loadMemberPhoto(photoUrl);
            }
        }

        private String getInitials(String name) {
            if (name == null) return "";
            String trimmed = name.trim();
            if (trimmed.isEmpty()) return "";
            String[] parts = trimmed.split("\\s+");
            StringBuilder sb = new StringBuilder();
            sb.append(Character.toUpperCase(parts[0].charAt(0)));
            if (parts.length > 1) {
                sb.append(Character.toUpperCase(parts[1].charAt(0)));
            }
            return sb.toString();
        }
        
        private void loadMemberPhoto(String photoUrl) {
            if (photoUrl == null || photoUrl.isEmpty()) return;

            // Hide initials while we attempt to load the photo
            if (binding.tvMemberInitials != null) {
                binding.tvMemberInitials.setVisibility(android.view.View.GONE);
            }
            if (binding.ivMemberPhoto != null) {
                binding.ivMemberPhoto.setVisibility(android.view.View.VISIBLE);
            }

            // Never pass base64 directly to Glide - decode first
            if (photoUrl.startsWith("http://") || photoUrl.startsWith("https://")) {
                Glide.with(binding.getRoot())
                        .load(photoUrl)
                        .placeholder(R.drawable.ic_launcher_foreground)
                        .into(binding.ivMemberPhoto);
                return;
            }
            Bitmap bitmap = ImageUtils.base64ToBitmap(photoUrl);
            if (bitmap != null) {
                Glide.with(binding.getRoot())
                        .load(bitmap)
                        .placeholder(R.drawable.ic_launcher_foreground)
                        .into(binding.ivMemberPhoto);
            } else {
                // Fallback back to initials
                if (binding.ivMemberPhoto != null) {
                    binding.ivMemberPhoto.setVisibility(android.view.View.GONE);
                }
                if (binding.tvMemberInitials != null) {
                    binding.tvMemberInitials.setVisibility(android.view.View.VISIBLE);
                }
            }
        }
    }
}
