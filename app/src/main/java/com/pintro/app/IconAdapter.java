package com.pintro.app;

import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;

import java.util.ArrayList;
import java.util.List;

public class IconAdapter extends RecyclerView.Adapter<IconAdapter.Holder> {

    public interface Listener {
        void onIconClick(Pin pin);
    }

    private final List<Pin> items = new ArrayList<>();
    private final Listener listener;

    public IconAdapter(Listener listener) {
        this.listener = listener;
    }

    public void clear() {
        items.clear();
        notifyDataSetChanged();
    }

    public void setPins(List<Pin> pins) {
        items.clear();
        if (pins != null) {
            items.addAll(pins);
        }
        notifyDataSetChanged();
    }

    public void append(List<Pin> pins) {
        if (pins == null || pins.isEmpty()) {
            return;
        }
        int start = items.size();
        items.addAll(pins);
        notifyItemRangeInserted(start, pins.size());
    }

    public List<Pin> items() {
        return items;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_icon, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        Pin pin = items.get(position);
        holder.image.setContentDescription(pin.title.isEmpty() ? "Icon" : pin.title);
        Glide.with(holder.image)
                .load(pin.gridUrl)
                .placeholder(new ColorDrawable(pin.colorInt()))
                .override(420, 420)
                .centerCrop()
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .transition(DrawableTransitionOptions.withCrossFade(120))
                .into(holder.image);
        holder.itemView.setOnClickListener(v -> listener.onIconClick(pin));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final SquareImageView image;

        Holder(@NonNull View view) {
            super(view);
            image = view.findViewById(R.id.icon_image);
        }
    }
}
