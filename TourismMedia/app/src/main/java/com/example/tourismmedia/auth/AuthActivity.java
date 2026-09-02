package com.example.tourismmedia.auth;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.example.tourismmedia.MainActivity;
import com.example.tourismmedia.R;
import com.example.tourismmedia.data.AppRepository;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class AuthActivity extends AppCompatActivity {
    private TextInputEditText username,email,password;
    private TextInputLayout usernameLayout;
    private Button submit,switchMode;
    private ProgressBar progress;
    private boolean registering;
    private AppRepository repository;

    @Override protected void onCreate(Bundle state){super.onCreate(state);repository=AppRepository.get(this);if(repository.session().isLoggedIn()){openApp();return;}setContentView(R.layout.activity_auth);
        username=findViewById(R.id.username_input);email=findViewById(R.id.email_input);password=findViewById(R.id.password_input);usernameLayout=findViewById(R.id.username_layout);submit=findViewById(R.id.auth_submit);switchMode=findViewById(R.id.auth_switch);progress=findViewById(R.id.auth_progress);
        switchMode.setOnClickListener(v->toggleMode());submit.setOnClickListener(v->authenticate());findViewById(R.id.forgot_password).setOnClickListener(v->forgotPassword());
    }
    private void toggleMode(){registering=!registering;usernameLayout.setVisibility(registering?View.VISIBLE:View.GONE);((TextView)findViewById(R.id.auth_title)).setText(registering?"Tạo tài khoản":"Chào mừng trở lại");submit.setText(registering?"Đăng ký":"Đăng nhập");switchMode.setText(registering?"Đã có tài khoản? Đăng nhập":"Chưa có tài khoản? Đăng ký");}
    private void authenticate(){String mail=text(email),pass=text(password),name=text(username);if(!Patterns.EMAIL_ADDRESS.matcher(mail).matches()){email.setError("Vui lòng nhập email hợp lệ");return;}if(pass.length()<8){password.setError("Mật khẩu phải có ít nhất 8 ký tự");return;}if(registering&&name.length()<3){username.setError("Tên hiển thị phải có ít nhất 3 ký tự");return;}loading(true);AppRepository.Result<com.example.tourismmedia.data.model.AppModels.AuthResponse> callback=(data,error)->{loading(false);if(error!=null)Toast.makeText(this,error,Toast.LENGTH_LONG).show();else openApp();};if(registering)repository.register(name,mail,pass,callback);else repository.login(mail,pass,callback);}
    private void forgotPassword(){
        LinearLayout form=new LinearLayout(this);form.setOrientation(LinearLayout.VERTICAL);int pad=(int)(20*getResources().getDisplayMetrics().density);form.setPadding(pad,0,pad,0);
        EditText mail=new EditText(this);mail.setHint("Email tài khoản");mail.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);mail.setText(text(email));
        EditText next=new EditText(this);next.setHint("Mật khẩu mới (ít nhất 8 ký tự)");next.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        form.addView(mail);form.addView(next);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Đặt lại mật khẩu").setMessage("Chế độ demo: đặt mật khẩu mới trực tiếp bằng email.").setView(form).setNegativeButton("Hủy",null).setPositiveButton("Đặt lại",null).create();
        dialog.setOnShowListener(ignored->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String address=mail.getText().toString().trim();String newPassword=next.getText().toString();if(!Patterns.EMAIL_ADDRESS.matcher(address).matches()){mail.setError("Email không hợp lệ");return;}if(newPassword.length()<8){next.setError("Cần ít nhất 8 ký tự");return;}dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);repository.forgotPassword(address,newPassword,(message,error)->{dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);if(error!=null)Toast.makeText(this,error,Toast.LENGTH_LONG).show();else{email.setText(address);password.setText("");Toast.makeText(this,message==null?"Đã đặt lại mật khẩu":message.message,Toast.LENGTH_LONG).show();dialog.dismiss();}});}));dialog.show();
    }
    private String text(TextInputEditText input){return input.getText()==null?"":input.getText().toString().trim();}
    private void loading(boolean value){progress.setVisibility(value?View.VISIBLE:View.GONE);submit.setEnabled(!value);}
    private void openApp(){startActivity(new Intent(this,MainActivity.class));finish();}
}
