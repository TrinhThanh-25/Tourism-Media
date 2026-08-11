package com.example.tourismmedia.ui.common;

import android.view.*;
import android.widget.TextView;import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.tourismmedia.R;
import com.bumptech.glide.Glide;
import java.util.*;

public class SimpleCardAdapter extends RecyclerView.Adapter<SimpleCardAdapter.Holder> {
    public static class CardItem { public final String icon,title,subtitle,meta,imageUrl; public final Object value; public CardItem(String i,String t,String s,String m,Object v){this(i,t,s,m,null,v);}public CardItem(String i,String t,String s,String m,String image,Object v){icon=i;title=t;subtitle=s;meta=m;imageUrl=image;value=v;} }
    public interface Listener { void onClick(CardItem item); }
    private final List<CardItem> items=new ArrayList<>(); private final Listener listener;
    public SimpleCardAdapter(Listener listener){this.listener=listener;}
    public void submit(List<CardItem> next){items.clear();items.addAll(next);notifyDataSetChanged();}
    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent,int type){return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_travel_card,parent,false));}
    @Override public void onBindViewHolder(@NonNull Holder h,int p){CardItem x=items.get(p);h.icon.setText(x.icon);h.title.setText(x.title);h.subtitle.setText(x.subtitle);h.meta.setText(x.meta);if(x.imageUrl!=null&&!x.imageUrl.isBlank()){h.image.setVisibility(View.VISIBLE);h.icon.setVisibility(View.GONE);Glide.with(h.image.getContext()).load(x.imageUrl).centerCrop().placeholder(R.drawable.bg_hero).error(R.drawable.bg_hero).into(h.image);}else{Glide.with(h.image.getContext()).clear(h.image);h.image.setVisibility(View.GONE);h.icon.setVisibility(View.VISIBLE);}h.itemView.setOnClickListener(v->listener.onClick(x));}
    @Override public int getItemCount(){return items.size();}
    static class Holder extends RecyclerView.ViewHolder{ImageView image;TextView icon,title,subtitle,meta;Holder(View v){super(v);image=v.findViewById(R.id.card_image);icon=v.findViewById(R.id.card_icon);title=v.findViewById(R.id.card_title);subtitle=v.findViewById(R.id.card_subtitle);meta=v.findViewById(R.id.card_meta);}}
}
