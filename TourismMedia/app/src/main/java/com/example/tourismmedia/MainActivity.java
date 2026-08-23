package com.example.tourismmedia;

import android.os.Bundle;
import android.content.Intent;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.example.tourismmedia.auth.AuthActivity;
import com.example.tourismmedia.data.SessionManager;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!new SessionManager(this).isLoggedIn()) {
            startActivity(new Intent(this, AuthActivity.class));
            finish();
            return;
        }
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (view, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars());
            view.setPadding(0, bars.top, 0, 0);
            return windowInsets;
        });

        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        if (navHostFragment == null) {
            throw new IllegalStateException("NavHostFragment is missing from activity_main.xml");
        }

        NavController navController = navHostFragment.getNavController();
        BottomNavigationView bottomNavigation = findViewById(R.id.bottom_navigation);
        NavigationUI.setupWithNavController(bottomNavigation, navController);
        bottomNavigation.setOnItemSelectedListener(item -> {
            int destinationId = item.getItemId();
            if (navController.getCurrentDestination() != null
                    && navController.getCurrentDestination().getId() == destinationId) {
                return true;
            }
            NavOptions options = new NavOptions.Builder()
                    .setPopUpTo(R.id.homeFragment, false)
                    .setLaunchSingleTop(true)
                    .build();
            navController.navigate(destinationId, null, options);
            return true;
        });
        navController.addOnDestinationChangedListener((controller, destination, arguments) ->
                bottomNavigation.setVisibility(isFullScreenDestination(destination.getId()) ? View.GONE : View.VISIBLE));
    }

    /** Destinations that own the whole screen and hide the bottom bar. */
    private static boolean isFullScreenDestination(int destinationId) {
        return destinationId == R.id.detailFragment
                || destinationId == R.id.locationDetailFragment
                || destinationId == R.id.accountCollectionFragment
                || destinationId == R.id.tripDetailFragment
                || destinationId == R.id.tripWorkspaceFragment
                || destinationId == R.id.member3WorkspaceFragment;
    }
}
