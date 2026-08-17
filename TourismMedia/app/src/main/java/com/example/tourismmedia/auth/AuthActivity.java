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
        switchMode.setOnClickListener(v->toggleMode());submit.setOnClickListener(v->authenticate());findViewById(R.id.google_button).setOnClickListener(v->social("Google"));findViewById(R.id.facebook_button).setOnClickListener(v->social("Facebook"));
    }
    private void toggleMode(){registering=!registering;usernameLayout.setVisibility(registering?View.VISIBLE:View.GONE);((TextView)findViewById(R.id.auth_title)).setText(registering?"Create an account":"Welcome back");submit.setText(registering?"Sign up":"Sign in");switchMode.setText(registering?"Already have an account? Sign in":"No account yet? Sign up");}
    private void authenticate(){String mail=text(email),pass=text(password),name=text(username);if(!Patterns.EMAIL_ADDRESS.matcher(mail).matches()){email.setError("Enter a valid email address");return;}if(pass.length()<8){password.setError("Password must be at least 8 characters");return;}if(registering&&name.length()<3){username.setError("Username must be at least 3 characters");return;}loading(true);AppRepository.Result<com.example.tourismmedia.data.model.AppModels.AuthResponse> callback=(data,error,sample)->{loading(false);if(error!=null)Toast.makeText(this,error,Toast.LENGTH_LONG).show();else openApp();};if(registering)repository.register(name,mail,pass,callback);else repository.login(mail,pass,callback);}
    private void social(String provider){new AlertDialog.Builder(this).setTitle("Sign in with "+provider).setMessage("OAuth is simulated for now: the app creates or signs in to a real backend demo account, so every screen and action still uses the real API.").setNegativeButton("Cancel",null).setPositiveButton("Continue",(d,w)->{loading(true);repository.socialAccount(provider,(data,error,sample)->{loading(false);if(error!=null)Toast.makeText(this,error,Toast.LENGTH_LONG).show();else openApp();});}).show();}
    private String text(TextInputEditText input){return input.getText()==null?"":input.getText().toString().trim();}
    private void loading(boolean value){progress.setVisibility(value?View.VISIBLE:View.GONE);submit.setEnabled(!value);}
    private void openApp(){startActivity(new Intent(this,MainActivity.class));finish();}
}
