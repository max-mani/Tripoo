package com.manikandan.tripoo.ui.expense;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import android.graphics.Bitmap;
import com.bumptech.glide.Glide;
import com.manikandan.tripoo.R;
import com.manikandan.tripoo.data.model.TripMember;
import com.manikandan.tripoo.databinding.ItemMemberCheckboxBinding;
import com.manikandan.tripoo.utils.ImageUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MemberCheckboxAdapter extends RecyclerView.Adapter<MemberCheckboxAdapter.MemberViewHolder> {
    private List<TripMember> members;
    private Set<String> selectedMemberIds;

    public MemberCheckboxAdapter(List<TripMember> members) {
        this.members = members != null ? members : new ArrayList<>();
        this.selectedMemberIds = new HashSet<>();
    }

    @NonNull
    @Override
    public MemberViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemMemberCheckboxBinding binding = ItemMemberCheckboxBinding.inflate(
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
        // Preserve selected state for existing members
        Set<String> newSelectedIds = new HashSet<>();
        for (TripMember member : newMembers) {
            if (selectedMemberIds.contains(member.getUserId())) {
                newSelectedIds.add(member.getUserId());
            }
        }
        selectedMemberIds = newSelectedIds;
        notifyDataSetChanged();
    }

    public void selectMember(String userId) {
        if (userId != null) {
            selectedMemberIds.add(userId);
            notifyDataSetChanged();
        }
    }

    public List<String> getSelectedMembers() {
        return new ArrayList<>(selectedMemberIds);
    }

    class MemberViewHolder extends RecyclerView.ViewHolder {
        private ItemMemberCheckboxBinding binding;

        public MemberViewHolder(@NonNull ItemMemberCheckboxBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(TripMember member) {
            binding.tvMemberName.setText(member.getName());
            
            boolean isSelected = selectedMemberIds.contains(member.getUserId());
            binding.cbMember.setOnCheckedChangeListener(null);
            binding.cbMember.setChecked(isSelected);
            binding.cbMember.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    selectedMemberIds.add(member.getUserId());
                } else {
                    selectedMemberIds.remove(member.getUserId());
                }
            });
            
            binding.getRoot().setOnClickListener(v -> {
                binding.cbMember.setChecked(!binding.cbMember.isChecked());
            });
            
            if (member.getPhotoUrl() != null && !member.getPhotoUrl().isEmpty()) {
                loadMemberPhoto(member.getPhotoUrl());
            }
        }
        
        private void loadMemberPhoto(String photoUrl) {
            if (photoUrl == null || photoUrl.isEmpty()) return;
            // Never pass base64 to Glide - decode first
            if (photoUrl.startsWith("http://") || photoUrl.startsWith("https://")) {
                Glide.with(binding.getRoot())
                        .load(photoUrl)
                        .circleCrop()
                        .placeholder(R.mipmap.ic_launcher_foreground)
                        .into(binding.ivMemberPhoto);
                return;
            }
            Bitmap bitmap = ImageUtils.base64ToBitmap(photoUrl);
            if (bitmap != null) {
                Glide.with(binding.getRoot())
                        .load(bitmap)
                        .circleCrop()
                        .placeholder(R.mipmap.ic_launcher_foreground)
                        .into(binding.ivMemberPhoto);
            }
        }
    }
}
