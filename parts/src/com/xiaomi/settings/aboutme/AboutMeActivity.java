package com.xiaomi.settings.aboutme;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import com.xiaomi.settings.R;
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity;

public class AboutMeActivity extends CollapsingToolbarBaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // A cím megmarad a toolbarban
        setTitle(R.string.about_me_title);

        // Saját layout használata, így a menüpontok a cím alatt jelennek meg
        setContentView(R.layout.activity_about_me);
    }

    public static class AboutMeFragment extends PreferenceFragmentCompat {

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.about_me_settings, rootKey);

            // Contact preference
            Preference contactPreference = findPreference("about_me_contact");
            if (contactPreference != null) {
                contactPreference.setOnPreferenceClickListener(preference -> {
                    Intent intent = new Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://t.me/Aarchivee404"));
                    startActivity(intent);
                    return true;
                });
            }

            // Donate preference
            Preference donatePreference = findPreference("about_me_donate");
            if (donatePreference != null) {
                donatePreference.setOnPreferenceClickListener(preference -> {
                    Intent intent = new Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://sociabuzz.com/mnzzprjkt/tribe"));
                    startActivity(intent);
                    return true;
                });
            }
            
            // Donate2 preference
            Preference donate2Preference = findPreference("about_me_donate2");
            if (donate2Preference != null) {
                donate2Preference.setOnPreferenceClickListener(preference -> {
                    Intent intent = new Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://saweria.co/MnzzProject"));
                    startActivity(intent);
                    return true;
                });
            }
            
        }
    }
}
