package com.example.tourismmedia.ui.explore;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.tourismmedia.R;
import com.example.tourismmedia.data.AppRepository;
import com.example.tourismmedia.data.model.AppModels.Location;
import com.example.tourismmedia.ui.common.SimpleCardAdapter;
import java.util.*;

public class ExploreFragment extends Fragment {
    private static final String[] SORT_LABELS={"Đánh giá cao","Nhiều đánh giá","Giá thấp trước","Giá cao trước","Tên A–Z","Tên Z–A"};
    private static final String[] SORT_VALUES={"rating-desc","review_count-desc","price-asc","price-desc","name-asc","name-desc"};
    private AppRepository repo; private SimpleCardAdapter adapter; private TextView status; private Button filterButton,sortButton;
    private String query="",category=null,type=null,sort="rating-desc"; private Double minPrice=null,maxPrice=null;
    private final LinkedHashSet<String> categories=new LinkedHashSet<>(),types=new LinkedHashSet<>();

    public ExploreFragment(){super(R.layout.fragment_explore);}
    @Override public void onViewCreated(@NonNull View v,Bundle b){
        repo=AppRepository.get(requireContext()); status=v.findViewById(R.id.explore_status); filterButton=v.findViewById(R.id.explore_filter);sortButton=v.findViewById(R.id.explore_sort);
        RecyclerView list=v.findViewById(R.id.explore_list);list.setLayoutManager(new LinearLayoutManager(requireContext()));adapter=new SimpleCardAdapter(i->details((Location)i.value));list.setAdapter(adapter);
        ((EditText)v.findViewById(R.id.explore_search)).addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int d){}public void onTextChanged(CharSequence s,int a,int b,int c){query=s.toString();load();}public void afterTextChanged(Editable e){}});
        filterButton.setOnClickListener(x->filter());sortButton.setOnClickListener(x->sort());v.findViewById(R.id.explore_clear).setOnClickListener(x->{category=null;type=null;minPrice=null;maxPrice=null;filterButton.setText("⚙ Bộ lọc");load();});load();
    }
    private void load(){repo.locations(query,category,type,minPrice,maxPrice,sort,(data,error,sample)->{for(Location x:data){if(x.category!=null&&!x.category.isBlank())categories.add(x.category);if(x.type!=null&&!x.type.isBlank())types.add(x.type);}List<SimpleCardAdapter.CardItem> cards=new ArrayList<>();for(Location x:data)cards.add(new SimpleCardAdapter.CardItem("⌖",x.name,safe(x.category)+" · "+safe(x.city),"★ "+x.rating+"   "+(x.price==0?"Miễn phí":String.format("%,.0fđ",x.price)),x.imageUrl,x));adapter.submit(cards);status.setText(error!=null?error:data.size()+" địa điểm · dữ liệu từ backend");});}
    private void filter(){
        LinearLayout form=new LinearLayout(requireContext());form.setOrientation(LinearLayout.VERTICAL);form.setPadding(44,8,44,0);
        Spinner categorySpinner=spinner(form,withAll(categories),category);Spinner typeSpinner=spinner(form,withAll(types),type);
        EditText min=new EditText(requireContext());min.setHint("Giá tối thiểu");min.setInputType(2);if(minPrice!=null)min.setText(String.valueOf(minPrice.longValue()));form.addView(min);
        EditText max=new EditText(requireContext());max.setHint("Giá tối đa");max.setInputType(2);if(maxPrice!=null)max.setText(String.valueOf(maxPrice.longValue()));form.addView(max);
        new AlertDialog.Builder(requireContext()).setTitle("Lọc địa điểm").setView(form).setNegativeButton("Hủy",null).setPositiveButton("Áp dụng",(d,w)->{category=value(categorySpinner);type=value(typeSpinner);minPrice=number(min);maxPrice=number(max);filterButton.setText("⚙ Đang lọc");load();}).show();
    }
    private void sort(){new AlertDialog.Builder(requireContext()).setTitle("Sắp xếp địa điểm").setSingleChoiceItems(SORT_LABELS,indexOf(SORT_VALUES,sort),(d,i)->{sort=SORT_VALUES[i];sortButton.setText("⇅ "+SORT_LABELS[i]);d.dismiss();load();}).show();}
    private Spinner spinner(LinearLayout parent,List<String> values,String selected){Spinner s=new Spinner(requireContext());s.setAdapter(new ArrayAdapter<>(requireContext(),android.R.layout.simple_spinner_dropdown_item,values));int i=values.indexOf(selected);s.setSelection(Math.max(0,i));parent.addView(s);return s;}
    private List<String> withAll(Set<String> source){List<String> out=new ArrayList<>();out.add("Tất cả");out.addAll(source);return out;}
    private String value(Spinner s){String x=String.valueOf(s.getSelectedItem());return "Tất cả".equals(x)?null:x;}
    private Double number(EditText e){try{return e.getText().toString().isBlank()?null:Double.valueOf(e.getText().toString());}catch(Exception ignored){return null;}}
    private int indexOf(String[] a,String x){for(int i=0;i<a.length;i++)if(a[i].equals(x))return i;return 0;}
    private void details(Location x){Bundle args=new Bundle();args.putString("type","location");args.putLong("id",x.id);Navigation.findNavController(requireView()).navigate(R.id.detailFragment,args);}
    private String safe(String s){return s==null?"":s;}
}
