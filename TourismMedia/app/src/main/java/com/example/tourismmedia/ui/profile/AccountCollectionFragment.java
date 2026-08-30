package com.example.tourismmedia.ui.profile;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.tourismmedia.R;
import com.example.tourismmedia.data.AppRepository;
import com.example.tourismmedia.data.model.AppModels.Location;
import com.example.tourismmedia.data.model.AppModels.Trip;
import com.example.tourismmedia.data.model.AppModels.Voucher;
import com.example.tourismmedia.ui.common.SimpleCardAdapter;
import com.example.tourismmedia.ui.location.LocationDetailFragment;
import com.example.tourismmedia.ui.location.LocationFormatter;
import com.example.tourismmedia.ui.trips.TripCardAdapter;
import com.google.android.material.button.MaterialButton;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class AccountCollectionFragment extends Fragment {
    private AppRepository repo;
    private SimpleCardAdapter simpleAdapter;
    private TripCardAdapter tripAdapter;
    private TextView title, subtitle, status, emptyText;
    private View empty;
    private RecyclerView list;
    private MaterialButton newest, rating, name;
    private String mode;
    private int sortMode;
    private final List<Location> locationItems = new ArrayList<>();
    private final List<Trip> tripItems = new ArrayList<>();
    private final List<Voucher> voucherItems = new ArrayList<>();

    public AccountCollectionFragment() { super(R.layout.fragment_account_collection); }

    @Override public void onViewCreated(@NonNull View view, Bundle state) {
        repo = AppRepository.get(requireContext());
        title = view.findViewById(R.id.collection_title); subtitle = view.findViewById(R.id.collection_subtitle);
        status = view.findViewById(R.id.collection_status); empty = view.findViewById(R.id.collection_empty);
        emptyText = view.findViewById(R.id.collection_empty_text); list = view.findViewById(R.id.collection_list);
        newest = view.findViewById(R.id.collection_sort_new); rating = view.findViewById(R.id.collection_sort_rating); name = view.findViewById(R.id.collection_sort_name);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        simpleAdapter = new SimpleCardAdapter(this::openSimple); tripAdapter = new TripCardAdapter(this::openTrip, this::toggleTripFavorite);
        newest.setOnClickListener(v -> sort(0)); rating.setOnClickListener(v -> sort(1)); name.setOnClickListener(v -> sort(2));
        view.findViewById(R.id.collection_back).setOnClickListener(v -> Navigation.findNavController(view).navigateUp());
        mode = getArguments() == null ? "favorite_locations" : getArguments().getString("mode", "favorite_locations");
        load();
    }

    private void load() {
        switch (mode) {
            case "favorite_trips":
                title.setText("Chuyến đi đã lưu"); subtitle.setText("Hành trình bạn muốn khám phá");
                repo.favoriteTrips((data,error,stale)->{tripItems.clear();tripItems.addAll(data);showTrips(error);}); break;
            case "checkins":
                title.setText("Lịch sử check-in"); subtitle.setText("Dấu chân trên hành trình của bạn");
                repo.checkIns((data,error,stale)->{locationItems.clear();locationItems.addAll(data);showLocations(error,true);}); break;
            case "vouchers":
                title.setText("Voucher của tôi"); subtitle.setText("Phần thưởng đã đổi từ điểm");
                repo.vouchers((data,error,stale)->{voucherItems.clear();voucherItems.addAll(data);showVouchers(error);}); break;
            default:
                title.setText("Địa điểm đã lưu"); subtitle.setText("Bộ sưu tập điểm đến yêu thích");
                repo.favoriteLocations((data,error,stale)->{locationItems.clear();locationItems.addAll(data);showLocations(error,false);});
        }
    }

    private void sort(int selected) {
        sortMode = selected; styleSort();
        if (!tripItems.isEmpty()) showTrips(null);
        else if (!locationItems.isEmpty()) showLocations(null, "checkins".equals(mode));
        else if (!voucherItems.isEmpty()) showVouchers(null);
    }

    private void styleSort() {
        MaterialButton[] buttons = {newest,rating,name};
        for (int i=0;i<buttons.length;i++) { boolean active=i==sortMode; buttons[i].setBackgroundTintList(ColorStateList.valueOf(Color.parseColor(active?"#12372A":"#FFFFFF"))); buttons[i].setTextColor(Color.parseColor(active?"#FFFFFF":"#718078")); }
    }

    private void showLocations(String error, boolean checkin) {
        List<Location> data = new ArrayList<>(locationItems);
        if (sortMode==1) data.sort(Comparator.comparingDouble((Location x)->x.rating).reversed());
        else if (sortMode==2) data.sort(Comparator.comparing(x->safe(x.name),String.CASE_INSENSITIVE_ORDER));
        else if (checkin) data.sort(Comparator.comparing((Location x)->safe(x.checkedInAt)).reversed());
        List<SimpleCardAdapter.CardItem> cards = new ArrayList<>();
        for (Location x:data) {
            String cardMeta = checkin ? "⌖ " + date(x.checkedInAt)
                    : x.reviewCount > 0 ? "★ " + LocationFormatter.rating(x.rating) + " · Đã lưu" : "Đã lưu";
            cards.add(new SimpleCardAdapter.CardItem("⌖",x.name,safe(x.category)+" · "+safe(x.city),cardMeta,x.imageUrl,x,!checkin,!checkin));
        }
        list.setAdapter(simpleAdapter); simpleAdapter.submit(cards); showState(cards.size(),error,checkin?"Bạn chưa check-in địa điểm nào":"Bạn chưa lưu địa điểm nào");
    }

    private void showTrips(String error) {
        List<Trip> data = new ArrayList<>(tripItems);
        if (sortMode==1) data.sort(Comparator.comparingDouble((Trip x)->x.rating).reversed());
        else if (sortMode==2) data.sort(Comparator.comparing(x->safe(x.title),String.CASE_INSENSITIVE_ORDER));
        else data.sort(Comparator.comparingLong((Trip x)->x.id).reversed());
        list.setAdapter(tripAdapter); tripAdapter.submit(data); showState(data.size(),error,"Bạn chưa lưu chuyến đi nào");
    }

    private void showVouchers(String error) {
        List<Voucher> data = new ArrayList<>(voucherItems);
        if (sortMode==2) data.sort(Comparator.comparing(x->safe(x.name),String.CASE_INSENSITIVE_ORDER));
        List<SimpleCardAdapter.CardItem> cards=new ArrayList<>();
        for(Voucher x:data)cards.add(new SimpleCardAdapter.CardItem("🎟",x.name,safe(x.description),x.percent+"% · "+safe(x.status),x));
        list.setAdapter(simpleAdapter);simpleAdapter.submit(cards);showState(cards.size(),error,"Bạn chưa có voucher nào");
    }

    private void showState(int count,String error,String emptyMessage){status.setText(error!=null?error:count+" mục trong bộ sưu tập");boolean none=count==0;empty.setVisibility(none?View.VISIBLE:View.GONE);list.setVisibility(none?View.GONE:View.VISIBLE);emptyText.setText(error!=null&&error.contains("401")?"Phiên đăng nhập đã hết hạn":emptyMessage);}
    private void openSimple(SimpleCardAdapter.CardItem item){if(item.value instanceof Location){Location location=(Location)item.value;Navigation.findNavController(requireView()).navigate(R.id.locationDetailFragment,LocationDetailFragment.argsFor(location.id,location.name));}else if(item.value instanceof Voucher){Bundle args=new Bundle();args.putString("mode","voucher-detail");args.putLong("id",((Voucher)item.value).voucherId);Navigation.findNavController(requireView()).navigate(R.id.member3WorkspaceFragment,args);}}
    private void openTrip(Trip trip){Bundle args=new Bundle();args.putLong("id",trip.id);Navigation.findNavController(requireView()).navigate(R.id.tripDetailFragment,args);}
    private void toggleTripFavorite(Trip trip,int position){repo.favoriteTrip(trip.id,true,(message,error,stale)->{if(!isAdded())return;if(error!=null){status.setText(error);return;}tripItems.removeIf(item->item.id==trip.id);showTrips(null);});}
    private String safe(String value){return value==null||value.isBlank()?"Chưa có thông tin":value;}
    private String date(String value){if(value==null||value.isBlank())return "Chưa rõ";String result=value.replace('T',' ').replace("Z","");return result.length()>16?result.substring(0,16):result;}
}
