package com.rscoders.v2ray;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.rscoders.v2ray.model.ProxyProfile;

import java.util.List;

public class ProfileAdapter extends RecyclerView.Adapter<ProfileAdapter.VH> {

    public interface Listener {
        void onClick(ProxyProfile p, int pos);
        void onLongClick(ProxyProfile p, int pos);
    }

    private List<ProxyProfile> items;
    private String activeId;
    private Listener listener;

    public ProfileAdapter(List<ProxyProfile> items, String activeId, Listener l) {
        this.items = items;
        this.activeId = activeId;
        this.listener = l;
    }

    public void update(List<ProxyProfile> list, String activeId) {
        this.items = list;
        this.activeId = activeId;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_profile, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        ProxyProfile p = items.get(pos);
        h.tvName.setText(p.getDisplayName());
        h.tvDetail.setText(p.address + ":" + p.port);

        String proto = p.protocol != null ? p.protocol.toLowerCase() : "vmess";
        String label;
        int color;
        switch (proto) {
            case "vless":
                label = "VL";
                color = Color.parseColor("#1976D2");
                break;
            case "trojan":
                label = "TJ";
                color = Color.parseColor("#E53935");
                break;
            case "shadowsocks":
            case "ss":
                label = "SS";
                color = Color.parseColor("#6A1B9A");
                break;
            default:
                label = "VM";
                color = Color.parseColor("#7dc037");
                break;
        }
        h.tvIcon.setText(label);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(color);
        h.tvIcon.setBackground(bg);

        boolean active = p.id.equals(activeId);
        h.tvBadge.setVisibility(active ? View.VISIBLE : View.GONE);

        h.itemView.setOnClickListener(v -> listener.onClick(p, pos));
        h.itemView.setOnLongClickListener(v -> {
            listener.onLongClick(p, pos);
            return true;
        });
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvName, tvDetail, tvBadge, tvIcon;
        VH(View v) {
            super(v);
            tvName = v.findViewById(R.id.tvProfileName);
            tvDetail = v.findViewById(R.id.tvProfileDetail);
            tvBadge = v.findViewById(R.id.tvActiveBadge);
            tvIcon = v.findViewById(R.id.tvProtocolIcon);
        }
    }
}
