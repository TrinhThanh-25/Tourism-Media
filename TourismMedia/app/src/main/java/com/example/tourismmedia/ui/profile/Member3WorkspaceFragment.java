package com.example.tourismmedia.ui.profile;

import android.Manifest;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.bumptech.glide.Glide;
import com.example.tourismmedia.R;
import com.example.tourismmedia.data.AppRepository;
import com.example.tourismmedia.data.model.AppModels.Challenge;
import com.example.tourismmedia.data.model.AppModels.Location;
import com.example.tourismmedia.data.model.AppModels.PointTransaction;
import com.example.tourismmedia.data.model.AppModels.Profile;
import com.example.tourismmedia.data.model.AppModels.Reward;
import com.example.tourismmedia.data.model.AppModels.Voucher;
import com.example.tourismmedia.ui.location.LocationDetailFragment;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Member3WorkspaceFragment extends Fragment {
    private static final int INK = Color.rgb(23, 51, 43), MUTED = Color.rgb(113, 128, 120), FOREST = Color.rgb(18, 55, 42);
    private static final String CHALLENGE_IMAGE = "https://media-cdn-v2.laodong.vn/Storage/NewsPortal/2023/3/16/1158477/IMG_8725-2.jpg";
    private LinearLayout content, actions;
    private TextView title, subtitle;
    private MaterialButton primary, secondary, top;
    private AppRepository repo;
    private String mode;
    private long id;
    private Uri selectedAvatar;
    private ImageView avatarPreview;
    private final ActivityResultLauncher<String[]> avatarPicker = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri == null) return;
                selectedAvatar = uri;
                try { requireContext().getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); }
                catch (SecurityException ignored) { }
                if (avatarPreview != null) Glide.with(this).load(uri).centerCrop().into(avatarPreview);
            });
    private final ActivityResultLauncher<String[]> imagePermission = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean allowed = Boolean.TRUE.equals(result.get(Manifest.permission.READ_MEDIA_IMAGES))
                        || Boolean.TRUE.equals(result.get(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED));
                if (allowed) avatarPicker.launch(new String[]{"image/*"});
                else toast("Cần quyền chia sẻ ảnh để chọn ảnh đại diện");
            });

    public Member3WorkspaceFragment() { super(R.layout.fragment_member3_workspace); }

    @Override public void onViewCreated(@NonNull View view, Bundle state) {
        repo = AppRepository.get(requireContext());
        mode = getArguments() == null ? "points" : getArguments().getString("mode", "points");
        id = getArguments() == null ? 0 : getArguments().getLong("id");
        content = view.findViewById(R.id.member3_content); actions = view.findViewById(R.id.member3_actions);
        title = view.findViewById(R.id.member3_title); subtitle = view.findViewById(R.id.member3_subtitle);
        primary = view.findViewById(R.id.member3_primary); secondary = view.findViewById(R.id.member3_secondary); top = view.findViewById(R.id.member3_top_action);
        view.findViewById(R.id.member3_back).setOnClickListener(v -> Navigation.findNavController(view).navigateUp());
        secondary.setOnClickListener(v -> Navigation.findNavController(view).navigateUp());
        render();
    }

    private void render() {
        content.removeAllViews(); actions.setVisibility(View.GONE); top.setVisibility(View.GONE);
        switch (mode) {
            case "challenge-detail": challengeDetail(); break;
            case "rewards": rewards(); break;
            case "reward-detail": rewardDetail(); break;
            case "redeem-confirm": redeemConfirm(); break;
            case "points-history": pointsHistory(); break;
            case "vouchers": vouchers(); break;
            case "voucher-detail": voucherDetail(); break;
            case "edit-profile": editProfile(); break;
            case "change-password": changePassword(); break;
            default: points();
        }
    }

    private void header(String heading, String caption) { title.setText(heading); subtitle.setText(caption); }
    private TextView text(String value, int size, boolean bold) { TextView v = new TextView(requireContext()); v.setText(value); v.setTextSize(size); v.setTextColor(INK); if (bold) v.setTypeface(null, Typeface.BOLD); return v; }
    private GradientDrawable background(int color, int radius) { GradientDrawable g = new GradientDrawable(); g.setColor(color); g.setCornerRadius(dp(radius)); return g; }
    private LinearLayout card() { LinearLayout v = new LinearLayout(requireContext()); v.setOrientation(LinearLayout.VERTICAL); v.setPadding(dp(18), dp(16), dp(18), dp(16)); v.setBackground(background(Color.WHITE, 20)); LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.setMargins(0, 0, 0, dp(12)); content.addView(v, p); return v; }
    private TextView section(String value) { TextView v = text(value, 21, true); LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.setMargins(dp(4), dp(10), 0, dp(12)); content.addView(v, p); return v; }
    private TextView muted(String value) { TextView v = text(value, 14, false); v.setTextColor(MUTED); return v; }
    private void showActions(String secondaryText, String primaryText) { actions.setVisibility(View.VISIBLE); secondary.setText(secondaryText); primary.setText(primaryText); }
    private void open(String next, long targetId) { Bundle args = new Bundle(); args.putString("mode", next); args.putLong("id", targetId); Navigation.findNavController(requireView()).navigate(R.id.member3WorkspaceFragment, args); }

    private void challengeDetail() {
        header("Chi tiết thử thách", "Theo dõi hành trình của bạn");
        repo.challengeInfo(id, (info, error, stale) -> {
            if (!viewActive()) return;
            if (info == null) { empty(error); return; }
            repo.challenge(id, (progress, progressError, sample) -> {
                if (viewActive()) bindChallenge(info, progress == null ? info : progress);
            });
        });
    }

    private void bindChallenge(Challenge info, Challenge progress) {
        ImageView hero = new ImageView(requireContext()); hero.setScaleType(ImageView.ScaleType.CENTER_CROP);
        String image = info.locations != null && !info.locations.isEmpty() ? info.locations.get(0).imageUrl : CHALLENGE_IMAGE;
        Glide.with(this).load(image).centerCrop().placeholder(R.drawable.bg_hero).into(hero);
        content.addView(hero, new LinearLayout.LayoutParams(-1, dp(210)));
        TextView name = text(info.name, 28, true); LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(-1, -2); np.setMargins(dp(4), dp(16), 0, dp(4)); content.addView(name, np);
        int target = progress.target > 0 ? progress.target : info.requiredCheckins;
        String progressLabel = progress.eligible ? "Sẵn sàng nhận thưởng" : progress.progress + "/" + target + " hoàn thành";
        TextView reward = text("+" + info.rewardPoint + " điểm · " + progressLabel, 14, true); reward.setTextColor(Color.rgb(47, 107, 80)); content.addView(reward);
        LinearLayout progressCard = card();
        progressCard.addView(text("Tiến độ của bạn                         " + progress.progress + "/" + target, 15, true));
        ProgressBar bar = new ProgressBar(requireContext(), null, android.R.attr.progressBarStyleHorizontal); bar.setMax(100); bar.setProgress(target > 0 ? Math.min(100, progress.progress * 100 / target) : 0); bar.setProgressTintList(ColorStateList.valueOf(Color.rgb(47,107,80))); LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, dp(9)); bp.setMargins(0, dp(12), 0, 0); progressCard.addView(bar, bp);
        section("Về thử thách"); content.addView(muted(safe(info.description)));
        LinearLayout detail = card(); detail.addView(text("Thời gian", 14, true)); detail.addView(muted(date(info.startDate) + " – " + date(info.endDate))); TextView spacer = text("Phần thưởng", 14, true); spacer.setPadding(0, dp(12), 0, 0); detail.addView(spacer); detail.addView(muted(info.rewardPoint + " điểm thành viên"));
        if (info.locations != null && !info.locations.isEmpty()) { section("Địa điểm gợi ý"); for (Location location : info.locations) locationRow(location); }
        boolean claimed = "claimed".equalsIgnoreCase(progress.status);
        String actionLabel = claimed ? "Đã nhận thưởng"
                : !progress.joined ? "Tham gia thử thách"
                : progress.eligible ? "Nhận " + info.rewardPoint + " điểm" : "Tiếp tục thử thách";
        showActions("Quay lại", actionLabel);
        primary.setEnabled(!claimed && progress.active);
        primary.setOnClickListener(v -> {
            if (!progress.joined) {
                repo.joinChallenge(id, (message, error, stale) -> {
                    if (!viewActive()) return;
                    toast(error == null ? "Đã tham gia thử thách" : error);
                    if (error == null) render();
                });
            } else if (progress.eligible) {
                repo.completeChallenge(id, (message, error, stale) -> {
                    if (!viewActive()) return;
                    toast(error == null ? "Đã nhận thưởng thử thách" : error);
                    if (error == null) render();
                });
            } else {
                Navigation.findNavController(requireView()).navigate(R.id.exploreFragment);
            }
        });
    }

    private void locationRow(Location location) {
        LinearLayout row = card(); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL);
        ImageView image = new ImageView(requireContext()); image.setScaleType(ImageView.ScaleType.CENTER_CROP); row.addView(image, new LinearLayout.LayoutParams(dp(78), dp(72))); Glide.with(this).load(location.imageUrl).centerCrop().placeholder(R.drawable.bg_hero).into(image);
        LinearLayout labels = new LinearLayout(requireContext()); labels.setOrientation(LinearLayout.VERTICAL); labels.setPadding(dp(12), 0, 0, 0); labels.addView(text(location.name, 16, true)); labels.addView(muted(safe(location.city))); row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        row.setOnClickListener(v -> Navigation.findNavController(requireView()).navigate(
                R.id.locationDetailFragment, LocationDetailFragment.argsFor(location.id, location.name)));
    }

    private void rewards() {
        header("Phần thưởng", "Voucher dành riêng cho thành viên"); top.setVisibility(View.VISIBLE); top.setOnClickListener(v -> open("vouchers", 0));
        repo.rewards((catalog, error, stale) -> { if (!viewActive()) return; if (catalog == null) { empty(error); return; } balanceCard("Số dư của bạn", catalog.points, null); section("Đổi điểm lấy niềm vui"); content.addView(muted("Chọn phần thưởng phù hợp với hành trình của bạn.")); for (Reward reward : catalog.rewards) rewardRow(reward); });
    }

    private void balanceCard(String label, int value, View.OnClickListener listener) {
        LinearLayout box = card(); box.setBackground(background(FOREST, 24)); box.setOrientation(LinearLayout.HORIZONTAL); box.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout labels = new LinearLayout(requireContext()); labels.setOrientation(LinearLayout.VERTICAL); TextView l = muted(label); l.setTextColor(Color.rgb(201,222,211)); labels.addView(l); TextView p = text(String.format("%,d", value), 32, true); p.setTextColor(Color.WHITE); labels.addView(p); box.addView(labels, new LinearLayout.LayoutParams(0,-2,1));
        if (listener != null) { MaterialButton button = new MaterialButton(requireContext()); button.setText("Đổi quà"); button.setTextColor(Color.rgb(58,43,19)); button.setBackgroundTintList(ColorStateList.valueOf(Color.rgb(255,184,77))); button.setOnClickListener(listener); box.addView(button); }
    }

    private void rewardRow(Reward reward) {
        LinearLayout row = card(); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL);
        TextView icon = text(reward.percent > 0 ? "🎟" : "🎁", 30, false); icon.setGravity(Gravity.CENTER); icon.setBackground(background(Color.rgb(223,242,231), 17)); row.addView(icon, new LinearLayout.LayoutParams(dp(72), dp(72)));
        LinearLayout labels = new LinearLayout(requireContext()); labels.setOrientation(LinearLayout.VERTICAL); labels.setPadding(dp(13),0,dp(8),0); labels.addView(text(reward.name,16,true)); labels.addView(muted(safe(reward.description))); row.addView(labels,new LinearLayout.LayoutParams(0,-2,1)); MaterialButton cost = new MaterialButton(requireContext()); cost.setText(reward.cost + " điểm"); cost.setTextColor(Color.rgb(58,43,19)); cost.setBackgroundTintList(ColorStateList.valueOf(Color.rgb(255,184,77))); row.addView(cost); row.setOnClickListener(v -> open("reward-detail", reward.id));
    }

    private void rewardDetail() {
        header("Chi tiết phần thưởng", "Ưu đãi dành riêng cho Explorer"); repo.reward(id, (reward,error,stale)->{if(!viewActive())return;if(reward==null){empty(error);return;}TextView icon=text(reward.percent>0?"☕":"🎁",42,false);icon.setGravity(Gravity.CENTER);icon.setBackground(background(Color.rgb(223,242,231),22));LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(dp(92),dp(92));ip.gravity=Gravity.CENTER_HORIZONTAL;content.addView(icon,ip);TextView name=text(reward.name,25,true);name.setGravity(Gravity.CENTER);LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,-2);np.setMargins(0,dp(12),0,dp(4));content.addView(name,np);TextView desc=muted(safe(reward.description));desc.setGravity(Gravity.CENTER);content.addView(desc);LinearLayout box=card();stat(box,"Chi phí",reward.cost+" điểm");stat(box,"Mức giảm",reward.percent+"%");stat(box,"Hiệu lực",date(reward.expiresAt));stat(box,"Giới hạn",Math.max(1,reward.perUserLimit)+" lần/người");section("Điều kiện sử dụng");content.addView(muted("Không áp dụng đồng thời với ưu đãi khác. Voucher không thể hoàn lại sau khi đổi."));showActions("Quay lại","Đổi với "+reward.cost+" điểm");primary.setOnClickListener(v->open("redeem-confirm",reward.id));});
    }

    private void redeemConfirm() {
        header("Xác nhận đổi quà", "Kiểm tra trước khi tiếp tục"); repo.reward(id,(reward,error,stale)->{if(!viewActive())return;repo.points((balance,pointsError,sample)->{if(!viewActive())return;if(reward==null||balance==null){empty(error!=null?error:pointsError);return;}voucherCard(reward.name,reward.percent+"%","Hiệu lực sau khi đổi");LinearLayout box=card();stat(box,"Điểm hiện có",String.format("%,d",balance.points));stat(box,"Chi phí đổi","−"+reward.cost);stat(box,"Số dư sau khi đổi",String.format("%,d",balance.points-reward.cost));CheckBox agree=new CheckBox(requireContext());agree.setChecked(true);agree.setText("Tôi đã đọc và đồng ý với điều kiện sử dụng.");agree.setTextColor(MUTED);content.addView(agree);showActions("Quay lại","Xác nhận đổi thưởng");primary.setOnClickListener(v->{if(!agree.isChecked()){toast("Vui lòng đồng ý điều kiện sử dụng");return;}repo.redeem(id,(message,redeemError,s)->{if(!viewActive())return;toast(redeemError==null?"Đổi thưởng thành công":redeemError);if(redeemError==null)open("vouchers",0);});});});});
    }

    private void points() {
        header("Điểm thành viên", "Mỗi hành trình đều được ghi nhận"); repo.points((balance,error,stale)->{if(!viewActive())return;if(balance==null){empty(error);return;}balanceCard("Số dư hiện tại",balance.points,v->open("rewards",0));repo.pointTransactions((items,e,s)->{if(!viewActive())return;int earned=0,used=0;for(PointTransaction item:items){if(item.points>=0)earned+=item.points;else used+=-item.points;}LinearLayout summary=new LinearLayout(requireContext());summary.setOrientation(LinearLayout.HORIZONTAL);LinearLayout left=summaryBox("Đã tích lũy","+"+earned,Color.rgb(47,107,80));LinearLayout right=summaryBox("Đã sử dụng","−"+used,Color.rgb(237,106,90));LinearLayout.LayoutParams half=new LinearLayout.LayoutParams(0,-2,1);half.setMargins(0,0,dp(5),0);summary.addView(left,half);LinearLayout.LayoutParams half2=new LinearLayout.LayoutParams(0,-2,1);half2.setMargins(dp(5),0,0,0);summary.addView(right,half2);content.addView(summary);section("Giao dịch gần đây").setOnClickListener(v->open("points-history",0));for(int i=0;i<Math.min(3,items.size());i++)transactionRow(items.get(i));});});
    }

    private LinearLayout summaryBox(String label,String amount,int color){LinearLayout box=new LinearLayout(requireContext());box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(16),dp(14),dp(16),dp(14));box.setBackground(background(Color.WHITE,18));box.addView(muted(label));TextView value=text(amount,23,true);value.setTextColor(color);box.addView(value);return box;}
    private void pointsHistory() {
        header("Lịch sử điểm", "Tất cả giao dịch trong tài khoản");
        TextView[] tabs = segmented("Tất cả", "Đã nhận", "Đã dùng");
        repo.pointTransactions((items, error, stale) -> {
            if (!viewActive()) return;
            if (items.isEmpty()) { empty(error == null ? "Chưa có giao dịch điểm" : error); return; }
            List<View> rows = new ArrayList<>();
            for (PointTransaction item : items) rows.add(transactionRow(item));
            for (int i = 0; i < tabs.length; i++) {
                final int selected = i;
                tabs[i].setOnClickListener(v -> {
                    selectSegment(tabs, selected);
                    for (int index = 0; index < items.size(); index++) {
                        PointTransaction item = items.get(index);
                        boolean visible = selected == 0 || (selected == 1 && item.points >= 0) || (selected == 2 && item.points < 0);
                        rows.get(index).setVisibility(visible ? View.VISIBLE : View.GONE);
                    }
                });
            }
        });
    }

    private LinearLayout transactionRow(PointTransaction item) {
        LinearLayout row = card(); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL);
        TextView icon = text(item.points >= 0 ? "◇" : "🎟", 22, false); icon.setGravity(Gravity.CENTER); icon.setBackground(background(Color.rgb(223,242,231),14)); row.addView(icon,new LinearLayout.LayoutParams(dp(48),dp(48)));
        LinearLayout labels = new LinearLayout(requireContext()); labels.setOrientation(LinearLayout.VERTICAL); labels.setPadding(dp(12),0,0,0); labels.addView(text(safe(item.description),15,true)); labels.addView(muted(date(item.createdAt))); row.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
        TextView amount = text((item.points >= 0 ? "+" : "") + item.points,16,true); amount.setTextColor(item.points >= 0 ? Color.rgb(47,107,80) : Color.rgb(237,106,90)); row.addView(amount);
        return row;
    }

    private void vouchers() {
        header("Voucher của tôi", "Ưu đãi sẵn sàng sử dụng");
        TextView[] tabs = segmented("Đang dùng", "Đã dùng", "Hết hạn");
        repo.vouchers((items, error, stale) -> {
            if (!viewActive()) return;
            if (items.isEmpty()) { empty(error == null ? "Bạn chưa có voucher nào" : error); return; }
            List<View> rows = new ArrayList<>();
            for (Voucher voucher : items) rows.add(voucherInventoryRow(voucher));
            for (int i = 0; i < tabs.length; i++) {
                final int selected = i;
                tabs[i].setOnClickListener(v -> {
                    selectSegment(tabs, selected);
                    for (int index = 0; index < items.size(); index++) {
                        String status = safe(items.get(index).status).toLowerCase();
                        boolean visible = selected == 0 ? status.equals("active") : selected == 1 ? status.equals("used") : status.equals("expired");
                        rows.get(index).setVisibility(visible ? View.VISIBLE : View.GONE);
                    }
                });
            }
            tabs[0].performClick();
        });
    }

    private LinearLayout voucherInventoryRow(Voucher voucher) {
        LinearLayout row = card(); row.setPadding(0,0,0,0); row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout main = new LinearLayout(requireContext()); main.setOrientation(LinearLayout.VERTICAL); main.setPadding(dp(17),dp(15),dp(12),dp(15));
        TextView status = text(voucher.status == null ? "ĐANG HOẠT ĐỘNG" : voucher.status.toUpperCase(),11,true); status.setTextColor(Color.rgb(47,107,80)); main.addView(status); main.addView(text(voucher.name,17,true)); main.addView(muted("Hết hạn "+date(voucher.expiresAt))); row.addView(main,new LinearLayout.LayoutParams(0,-2,1));
        TextView side = text(voucher.percent+"%\nOFF",18,true); side.setTextColor(Color.WHITE); side.setGravity(Gravity.CENTER); side.setBackgroundColor(FOREST); row.addView(side,new LinearLayout.LayoutParams(dp(82),-1));
        row.setOnClickListener(v->open("voucher-detail",voucher.voucherId));
        return row;
    }
    private void voucherDetail(){header("Chi tiết voucher","Sẵn sàng để sử dụng");repo.vouchers((items,error,stale)->{if(!viewActive())return;Voucher found=null;for(Voucher voucher:items)if(voucher.voucherId==id)found=voucher;if(found==null){empty(error==null?"Không tìm thấy voucher":error);return;}Voucher voucher=found;TextView icon=text("🎟",42,false);icon.setGravity(Gravity.CENTER);LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(dp(90),dp(90));ip.gravity=Gravity.CENTER_HORIZONTAL;content.addView(icon,ip);TextView name=text(voucher.name,24,true);name.setGravity(Gravity.CENTER);content.addView(name);LinearLayout code=card();TextView label=muted("MÃ VOUCHER");label.setGravity(Gravity.CENTER);code.addView(label);TextView codeValue=text(voucher.code,24,true);codeValue.setGravity(Gravity.CENTER);codeValue.setLetterSpacing(.08f);code.addView(codeValue);LinearLayout info=card();stat(info,"Ngày đổi",date(voucher.createdAt));stat(info,"Hết hạn",date(voucher.expiresAt));stat(info,"Trạng thái",safe(voucher.status));showActions("Quay lại","Sử dụng voucher");primary.setEnabled("active".equalsIgnoreCase(voucher.status));primary.setOnClickListener(v->repo.useVoucher(voucher.voucherId,(message,useError,s)->{if(!viewActive())return;toast(useError==null?"Đã sử dụng voucher":useError);if(useError==null)Navigation.findNavController(requireView()).navigateUp();}));});}

    private void editProfile() {
        header("Chỉnh sửa hồ sơ", "Thông tin hiển thị trong cộng đồng");
        repo.profile((profile, error, stale) -> {
            if (!viewActive()) return;
            if (profile == null) { empty(error); return; }
            LinearLayout form = card();
            avatarPreview = new ImageView(requireContext());
            avatarPreview.setScaleType(ImageView.ScaleType.CENTER_CROP);
            avatarPreview.setBackground(background(Color.rgb(223,242,231), 44));
            if (profile.avatar != null && !profile.avatar.isBlank()) Glide.with(this).load(profile.avatar).centerCrop().into(avatarPreview);
            LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(dp(92), dp(92));
            avatarParams.gravity = Gravity.CENTER_HORIZONTAL; form.addView(avatarPreview, avatarParams);
            MaterialButton chooseAvatar = new MaterialButton(requireContext()); chooseAvatar.setText("Chọn ảnh đại diện từ thư viện");
            chooseAvatar.setOnClickListener(v -> requestAvatarPermission()); form.addView(chooseAvatar);
            EditText username=field(form,"Tên hiển thị",profile.username,false); EditText email=field(form,"Email",profile.email,false);
            EditText phone=field(form,"Số điện thoại",profile.phone,false); EditText dob=field(form,"Ngày sinh",profile.dob,false);
            EditText gender=field(form,"Giới tính (male/female/other)",profile.gender,false);
            MaterialButton password=new MaterialButton(requireContext()); password.setText("⌘  Đổi mật khẩu  ›"); password.setOnClickListener(v->open("change-password",0)); content.addView(password,new LinearLayout.LayoutParams(-1,dp(54)));
            showActions("Hủy","Lưu thay đổi");
            primary.setOnClickListener(v->{Map<String,String>body=new HashMap<>(); body.put("username",username.getText().toString().trim()); body.put("email",email.getText().toString().trim()); body.put("phone",phone.getText().toString().trim()); body.put("dob",dob.getText().toString().trim()); body.put("gender",gender.getText().toString().trim()); if(selectedAvatar!=null)body.put("avatar_url",selectedAvatar.toString()); repo.updateProfile(body,(message,updateError,s)->{if(!viewActive())return;toast(updateError==null?message:updateError);if(updateError==null)Navigation.findNavController(requireView()).navigateUp();});});
        });
    }

    private void requestAvatarPermission() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_MEDIA_IMAGES) == android.content.pm.PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            avatarPicker.launch(new String[]{"image/*"});
        } else {
            imagePermission.launch(new String[]{Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED});
        }
    }
    private void changePassword(){header("Đổi mật khẩu","Bảo vệ tài khoản của bạn");section("Mật khẩu mới");content.addView(muted("Nên sử dụng ít nhất 8 ký tự, gồm chữ và số."));LinearLayout form=card();EditText current=field(form,"Mật khẩu hiện tại","",true);EditText next=field(form,"Mật khẩu mới","",true);EditText confirm=field(form,"Xác nhận mật khẩu","",true);TextView rule=muted("✓ Ít nhất 8 ký tự\n○ Có chữ hoa, chữ thường và số");form.addView(rule);showActions("Hủy","Cập nhật mật khẩu");primary.setOnClickListener(v->{String password=next.getText().toString();if(password.length()<8){next.setError("Cần ít nhất 8 ký tự");return;}if(!password.equals(confirm.getText().toString())){confirm.setError("Mật khẩu chưa khớp");return;}repo.changePassword(current.getText().toString(),password,(message,error,s)->{if(!viewActive())return;toast(error==null?"Đổi mật khẩu thành công":error);if(error==null)Navigation.findNavController(requireView()).navigateUp();});});}

    private EditText field(LinearLayout parent,String label,String value,boolean password){TextView caption=text(label,13,true);caption.setTextColor(MUTED);parent.addView(caption);EditText input=new EditText(requireContext());input.setText(value==null?"":value);input.setHint("Nhập "+label.toLowerCase());input.setSingleLine(true);input.setTextColor(INK);input.setHintTextColor(Color.rgb(160,170,164));GradientDrawable bg=background(Color.WHITE,14);bg.setStroke(dp(1),Color.rgb(228,232,227));input.setBackground(bg);input.setPadding(dp(13),0,dp(13),0);if(password)input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(52));p.setMargins(0,dp(6),0,dp(14));parent.addView(input,p);return input;}
    private TextView[] segmented(String... labels) {
        LinearLayout row = new LinearLayout(requireContext()); row.setPadding(dp(4),dp(4),dp(4),dp(4)); row.setBackground(background(Color.rgb(233,238,233),15));
        TextView[] tabs = new TextView[labels.length];
        for (int i = 0; i < labels.length; i++) { tabs[i] = text(labels[i],14,i==0); tabs[i].setGravity(Gravity.CENTER); row.addView(tabs[i],new LinearLayout.LayoutParams(0,dp(42),1)); }
        selectSegment(tabs, 0);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1,-2); p.setMargins(0,0,0,dp(16)); content.addView(row,p);
        return tabs;
    }

    private void selectSegment(TextView[] tabs, int selected) {
        for (int i = 0; i < tabs.length; i++) {
            boolean active = i == selected;
            tabs[i].setTypeface(null, active ? Typeface.BOLD : Typeface.NORMAL);
            tabs[i].setTextColor(active ? FOREST : MUTED);
            tabs[i].setBackground(active ? background(Color.WHITE,12) : null);
        }
    }
    private void voucherCard(String name,String amount,String caption){LinearLayout row=card();row.setOrientation(LinearLayout.HORIZONTAL);LinearLayout main=new LinearLayout(requireContext());main.setOrientation(LinearLayout.VERTICAL);main.addView(text(name,19,true));main.addView(muted(caption));row.addView(main,new LinearLayout.LayoutParams(0,-2,1));TextView side=text(amount+"\nOFF",18,true);side.setGravity(Gravity.CENTER);side.setTextColor(Color.WHITE);side.setBackgroundColor(FOREST);row.addView(side,new LinearLayout.LayoutParams(dp(82),dp(90)));}
    private void stat(LinearLayout box,String label,String value){LinearLayout row=new LinearLayout(requireContext());row.setPadding(0,dp(7),0,dp(7));TextView left=muted(label);row.addView(left,new LinearLayout.LayoutParams(0,-2,1));row.addView(text(value,14,true));box.addView(row);}
    private void empty(String message){TextView v=text("🧭\n\n"+(message==null?"Không tải được dữ liệu":message),17,true);v.setTextColor(MUTED);v.setGravity(Gravity.CENTER);content.addView(v,new LinearLayout.LayoutParams(-1,dp(360)));}
    private String safe(String value){return value==null||value.isBlank()?"Chưa có thông tin":value;}
    private String date(String value){if(value==null||value.isBlank())return "Chưa rõ";String date=value.replace('T',' ').replace("Z","");return date.length()>10?date.substring(0,10):date;}
    private boolean viewActive(){return isAdded()&&getView()!=null;}
    private void toast(String message){if(viewActive())Toast.makeText(requireContext(),message,Toast.LENGTH_LONG).show();}
    private int dp(int value){return(int)(value*getResources().getDisplayMetrics().density+.5f);}
}
