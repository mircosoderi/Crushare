package it.mircosoderi.crushare;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;

import java.util.UUID;

public class SettingsFragment extends PreferenceFragment implements SharedPreferences.OnSharedPreferenceChangeListener {

    public static final String INLINE_IMAGES = "inline_images";
    public static final String EXECUTE_JAVASCRIPT = "execute_javascript";
    public static final String SAFE_BROWSING = "safe_browsing";
    public static final String MIN_FRAME_DURATION = "min_frame_duration";
    public static final String DELIVERY_ATTEMPTS = "delivery_attempts_per_message";
    public static final String DELIVERY_INTERVAL = "delivery_attempts_interval";
    public static final String INSTANCE_ID = "instance_id";
    public static final String SIGNATURE = "signature";
    public static final String INLINE_VIDEOS = "inline_videos";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);
        findPreference(MIN_FRAME_DURATION).setTitle(
                getString(R.string.min_frame_duration_title) +
                        ": " + getPreferenceScreen().getSharedPreferences().getString(MIN_FRAME_DURATION, null) + " " +getString(R.string.seconds)
        );
        findPreference(DELIVERY_ATTEMPTS).setTitle(
                getString(R.string.delivery_attempts_per_message_title) +
                        ": " + getPreferenceScreen().getSharedPreferences().getString(DELIVERY_ATTEMPTS, null)
        );
        findPreference(DELIVERY_INTERVAL).setTitle(
                getString(R.string.delivery_attempts_interval_title) +
                        ": " + getPreferenceScreen().getSharedPreferences().getString(DELIVERY_INTERVAL, null) + " " +getString(R.string.seconds)
        );
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(getActivity());
        String uuid = sharedPref.getString(SettingsFragment.INSTANCE_ID, UUID.randomUUID().toString());
        findPreference(INSTANCE_ID).setSummary(uuid);

    }

    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(MIN_FRAME_DURATION)) {
            findPreference(MIN_FRAME_DURATION).setTitle(
                    getString(R.string.min_frame_duration_title) +
                            ": " + getPreferenceScreen().getSharedPreferences().getString(MIN_FRAME_DURATION, null) + " " + getString(R.string.seconds)
            );
        }
        if(key.equals(DELIVERY_ATTEMPTS)) {
            findPreference(DELIVERY_ATTEMPTS).setTitle(
                    getString(R.string.delivery_attempts_per_message_title) +
                            ": " + getPreferenceScreen().getSharedPreferences().getString(DELIVERY_ATTEMPTS, null)
            );
        }
        if(key.equals(DELIVERY_INTERVAL)) {
            findPreference(DELIVERY_INTERVAL).setTitle(
                    getString(R.string.delivery_attempts_interval_title) +
                            ": " + getPreferenceScreen().getSharedPreferences().getString(DELIVERY_INTERVAL, null) + " " +getString(R.string.seconds)
            );
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        getPreferenceScreen().getSharedPreferences()
                .registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        getPreferenceScreen().getSharedPreferences()
                .unregisterOnSharedPreferenceChangeListener(this);
    }

}
