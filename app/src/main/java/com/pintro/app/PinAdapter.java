package com.pintro.app;

import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.google.android.material.imageview.ShapeableImageView;

import java.util.ArrayList;
import java.util.List;

public class PinAdapter extends RecyclerView.Adapter<PinAdapter.PinHolder> {

    public interface Listener {
        void onPinClick(Pin pin);
    }

    private final List<Pin> items = new ArrayList<>();
    private final Listener listener;
    private final int columnWidth;

    public PinAdapter(Listener listener, int spanCount, int screenWidth) {
        this.listener = listener;
        int columns = Math.max(1, spanCount);
        int padding = dp(20);
        this.columnWidth = Math.max(dp(80), (screenWidth - padding) / columns - dp(10));
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
    public PinHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_pin, parent, false);
        return new PinHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PinHolder holder, int position) {
        Pin pin = items.get(position);

        float ratio = pin.height / (float) Math.max(1, pin.width);
        ratio = Math.max(0.65f, Math.min(1.8f, ratio));
        int height = Math.max((int) (columnWidth * 0.65f), Math.min((int) (columnWidth * 1.8f), (int) (columnWidth * ratio)));

        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) holder.image.getLayoutParams();
        params.height = height;
        holder.image.setLayoutParams(params);

        holder.image.setContentDescription(pin.title);
        holder.title.setText(pin.title);
        holder.title.setVisibility(pin.title.isEmpty() ? View.GONE : View.VISIBLE);

        String source = pin.domain.isEmpty() ? pin.author : pin.domain;
        holder.source.setText(source);
        holder.source.setVisibility(source.isEmpty() ? View.GONE : View.VISIBLE);

        Glide.with(holder.image)
                .load(pin.gridUrl)
                .placeholder(new ColorDrawable(pin.colorInt()))
                .thumbnail(0.2f)
                .override(columnWidth, height)
                .centerCrop()
                .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                .transition(DrawableTransitionOptions.withCrossFade(140))
                .into(holder.image);

        holder.itemView.setOnClickListener(v -> listener.onPinClick(pin));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @Override
    public long getItemId(int position) {
        return items.get(position).id.hashCode();
    }

    public static class PinHolder extends RecyclerView.ViewHolder {
        final ShapeableImageView image;
        final TextView title;
        final TextView source;

        PinHolder(@NonNull View itemView) {
            super(itemView);
            image = itemView.findViewById(R.id.pin_image);
            title = itemView.findViewById(R.id.pin_title);
            source = itemView.findViewById(R.id.pin_source);
        }
    }

    private static int dp(int value) {
        return (int) (value * android.content.res.Resources.getSystem().getDisplayMetrics().density);
    }
}
