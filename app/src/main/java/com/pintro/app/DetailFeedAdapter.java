package com.pintro.app;

import android.content.res.ColorStateList;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.google.android.material.color.MaterialColors;

import java.util.ArrayList;
import java.util.List;

public class DetailFeedAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_PIN = 1;
    private static final int TYPE_FOOTER = 2;

    private final View header;
    private final List<Pin> items = new ArrayList<>();
    private final PinAdapter.Listener listener;
    private final int columnWidth;
    private boolean loading;

    public DetailFeedAdapter(View header, PinAdapter.Listener listener, int spanCount, int screenWidth) {
        this.header = header;
        this.listener = listener;
        int columns = Math.max(1, spanCount);
        int padding = dp(20);
        this.columnWidth = Math.max(dp(80), (screenWidth - padding) / columns - dp(10));
    }

    public void clear() {
        items.clear();
        notifyDataSetChanged();
    }

    public void append(List<Pin> pins) {
        if (pins == null || pins.isEmpty()) {
            return;
        }
        int start = items.size() + 1;
        items.addAll(pins);
        notifyItemRangeInserted(start, pins.size());
    }

    public int pinCount() {
        return items.size();
    }

    public void setLoading(boolean value) {
        if (loading == value) {
            return;
        }
        loading = value;
        if (getItemCount() > 0) {
            notifyItemChanged(getItemCount() - 1);
        }
    }

    @Override
    public int getItemViewType(int position) {
        if (position == 0) {
            return TYPE_HEADER;
        }
        if (position == getItemCount() - 1) {
            return TYPE_FOOTER;
        }
        return TYPE_PIN;
    }

    @Override
    public int getItemCount() {
        return items.size() + 2;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_HEADER) {
            StaggeredGridLayoutManager.LayoutParams params = new StaggeredGridLayoutManager.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setFullSpan(true);
            header.setLayoutParams(params);
            return new SimpleHolder(header);
        }
        if (viewType == TYPE_FOOTER) {
            FrameLayout frame = new FrameLayout(parent.getContext());
            StaggeredGridLayoutManager.LayoutParams params = new StaggeredGridLayoutManager.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setFullSpan(true);
            frame.setLayoutParams(params);

            int size = dp(26);
            ProgressBar bar = new ProgressBar(parent.getContext());
            FrameLayout.LayoutParams barParams = new FrameLayout.LayoutParams(size, size);
            barParams.gravity = Gravity.CENTER_HORIZONTAL;
            barParams.topMargin = dp(16);
            barParams.bottomMargin = dp(20);
            bar.setLayoutParams(barParams);
            bar.setIndeterminateTintList(ColorStateList.valueOf(MaterialColors.getColor(
                    parent, androidx.appcompat.R.attr.colorPrimary, 0xFF5A5F5F)));
            frame.addView(bar);

            return new FooterHolder(frame, bar);
        }

        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_pin, parent, false);
        return new PinAdapter.PinHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof FooterHolder) {
            FooterHolder footer = (FooterHolder) holder;
            footer.bar.setVisibility(loading ? View.VISIBLE : View.GONE);
            return;
        }
        if (position == 0 || !(holder instanceof PinAdapter.PinHolder)) {
            return;
        }

        Pin pin = items.get(position - 1);
        PinAdapter.PinHolder pinHolder = (PinAdapter.PinHolder) holder;

        float ratio = pin.height / (float) Math.max(1, pin.width);
        ratio = Math.max(0.65f, Math.min(1.8f, ratio));
        int height = Math.max((int) (columnWidth * 0.65f), Math.min((int) (columnWidth * 1.8f), (int) (columnWidth * ratio)));

        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) pinHolder.image.getLayoutParams();
        params.height = height;
        pinHolder.image.setLayoutParams(params);

        pinHolder.image.setContentDescription(pin.title);
        pinHolder.title.setText(pin.title);
        pinHolder.title.setVisibility(pin.title.isEmpty() ? View.GONE : View.VISIBLE);

        String source = pin.domain.isEmpty() ? pin.author : pin.domain;
        pinHolder.source.setText(source);
        pinHolder.source.setVisibility(source.isEmpty() ? View.GONE : View.VISIBLE);

        Glide.with(pinHolder.image)
                .load(pin.gridUrl)
                .placeholder(new ColorDrawable(pin.colorInt()))
                .thumbnail(0.2f)
                .override(columnWidth, height)
                .centerCrop()
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .transition(DrawableTransitionOptions.withCrossFade(140))
                .into(pinHolder.image);

        pinHolder.itemView.setOnClickListener(v -> listener.onPinClick(pin));
    }

    @Override
    public long getItemId(int position) {
        if (position == 0) {
            return -1;
        }
        if (position == getItemCount() - 1) {
            return -2;
        }
        return items.get(position - 1).id.hashCode();
    }

    private static class SimpleHolder extends RecyclerView.ViewHolder {
        SimpleHolder(@NonNull View itemView) {
            super(itemView);
        }
    }

    private static class FooterHolder extends RecyclerView.ViewHolder {
        final ProgressBar bar;

        FooterHolder(@NonNull View itemView, ProgressBar bar) {
            super(itemView);
            this.bar = bar;
        }
    }

    private static int dp(int value) {
        return (int) (value * android.content.res.Resources.getSystem().getDisplayMetrics().density);
    }
}
