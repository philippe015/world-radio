package fr.cyberpunk.worldradio;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class StationAdapter extends BaseAdapter {

    private final Context ctx;
    private final List<RadioStation> items = new ArrayList<>();
    private String playingUrl;

    public StationAdapter(Context ctx) { this.ctx = ctx; }

    public void setItems(List<RadioStation> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    public void setPlaying(String url) {
        playingUrl = url;
        notifyDataSetChanged();
    }

    @Override public int getCount() { return items.size(); }
    @Override public Object getItem(int i) { return items.get(i); }
    @Override public long getItemId(int i) { return i; }
    public RadioStation station(int i) { return items.get(i); }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View v = convertView;
        if (v == null) {
            v = LayoutInflater.from(ctx).inflate(R.layout.item_station, parent, false);
            Holder h = new Holder();
            h.num = v.findViewById(R.id.num);
            h.logo = v.findViewById(R.id.logo);
            h.name = v.findViewById(R.id.name);
            h.sub = v.findViewById(R.id.sub);
            h.dot = v.findViewById(R.id.dot);
            v.setTag(h);
        }
        Holder h = (Holder) v.getTag();
        RadioStation s = items.get(position);
        h.num.setText((position + 1) + ".");
        h.name.setText(s.name);
        h.sub.setText(s.subtitle());
        ImageLoader.load(s.favicon, h.logo);
        h.dot.setVisibility(s.url.equals(playingUrl) ? View.VISIBLE : View.INVISIBLE);
        return v;
    }

    static class Holder {
        TextView num;
        ImageView logo;
        TextView name;
        TextView sub;
        View dot;
    }
}
