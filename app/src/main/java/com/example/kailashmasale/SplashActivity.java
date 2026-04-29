package com.example.kailashmasale;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

public class SplashActivity extends AppCompatActivity {

    private static final int SPLASH_DURATION_MS = 3000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getWindow() != null) {
            getWindow().setStatusBarColor(ContextCompat.getColor(this, android.R.color.white));
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
        setContentView(R.layout.activity_splash);

        View logo = findViewById(R.id.splash_logo);
        View company = findViewById(R.id.splash_company);
        View divider = findViewById(R.id.splash_divider);
        View developer = findViewById(R.id.splash_developer);

        logo.setAlpha(0f);

        ObjectAnimator logoFade = ObjectAnimator.ofFloat(logo, View.ALPHA, 0f, 1f);
        logoFade.setDuration(1200);
        logoFade.start();

        ObjectAnimator companyFade = ObjectAnimator.ofFloat(company, View.ALPHA, 0f, 1f);
        companyFade.setDuration(600);
        companyFade.setStartDelay(800);

        ObjectAnimator dividerFade = ObjectAnimator.ofFloat(divider, View.ALPHA, 0f, 1f);
        dividerFade.setDuration(500);
        dividerFade.setStartDelay(1100);

        ObjectAnimator devFade = ObjectAnimator.ofFloat(developer, View.ALPHA, 0f, 1f);
        devFade.setDuration(600);
        devFade.setStartDelay(1300);

        AnimatorSet textSet = new AnimatorSet();
        textSet.playTogether(companyFade, dividerFade, devFade);
        textSet.start();

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            startActivity(new Intent(SplashActivity.this, MainActivity.class));
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            finish();
        }, SPLASH_DURATION_MS);
    }
}
