package com.example.tripoo.ui.groups;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.example.tripoo.R;
import com.example.tripoo.data.model.TripMember;
import com.example.tripoo.databinding.ItemMemberBinding;

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
            
            if (member.isAdmin()) {
                binding.chipAdmin.setVisibility(android.view.View.VISIBLE);
            } else {
                binding.chipAdmin.setVisibility(android.view.View.GONE);
            }
            
            if (member.getPhotoUrl() != null && !member.getPhotoUrl().isEmpty()) {
                Glide.with(binding.getRoot())
                        .load(member.getPhotoUrl())
                        .placeholder(R.drawable.ic_launcher_foreground)
                        .into(binding.ivMemberPhoto);
            }
        }
    }
}
