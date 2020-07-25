package it.mircosoderi.crushare;

import android.graphics.Color;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.webkit.WebView;

public class AboutActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);
        WebView aboutView = findViewById(R.id.about);
        aboutView.loadDataWithBaseURL("file:///android_asset/", getString(R.string.about_notice),"text/html","utf-8", null);
        aboutView.setBackgroundColor(Color.TRANSPARENT);
    }
}
