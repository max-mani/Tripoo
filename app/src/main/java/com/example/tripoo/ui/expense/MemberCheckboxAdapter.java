package com.example.tripoo.ui.expense;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.example.tripoo.R;
import com.example.tripoo.data.model.TripMember;
import com.example.tripoo.databinding.ItemMemberCheckboxBinding;

import java.util.ArrayList;
import java.util.List;

public class MemberCheckboxAdapter extends RecyclerView.Adapter<MemberCheckboxAdapter.MemberViewHolder> {
    private List<TripMember> members;

    public MemberCheckboxAdapter(List<TripMember> members) {
        this.members = members != null ? members : new ArrayList<>();
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
        notifyDataSetChanged();
    }

    public List<String> getSelectedMembers() {
        List<String> selected = new ArrayList<>();
        for (int i = 0; i < members.size(); i++) {
            // This would need to track checkbox states - simplified for now
            selected.add(members.get(i).getUserId());
        }
        return selected;
    }

    class MemberViewHolder extends RecyclerView.ViewHolder {
        private ItemMemberCheckboxBinding binding;

        public MemberViewHolder(@NonNull ItemMemberCheckboxBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(TripMember member) {
            binding.tvMemberName.setText(member.getName());
            
            if (member.getPhotoUrl() != null && !member.getPhotoUrl().isEmpty()) {
                Glide.with(binding.getRoot())
                        .load(member.getPhotoUrl())
                        .placeholder(R.drawable.ic_launcher_foreground)
                        .into(binding.ivMemberPhoto);
            }
        }
    }
}
