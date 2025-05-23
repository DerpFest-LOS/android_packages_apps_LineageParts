/*
 * SPDX-FileCopyrightText: 2016 The CyanogenMod Project
 * SPDX-FileCopyrightText: 2017-2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.lineageparts.sounds;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.ArraySet;
import android.util.Log;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.preference.Preference;

import org.lineageos.lineageparts.R;
import org.lineageos.lineageparts.SettingsPreferenceFragment;
import org.lineageos.lineageparts.search.BaseSearchIndexProvider;
import org.lineageos.lineageparts.search.Searchable;

import java.util.Set;

public class ChargingSoundsSettings
        extends SettingsPreferenceFragment implements Searchable {
    private static final String TAG = "ChargingSoundsSettings";

    private static final String KEY_CHARGING_VIBRATION_ENABLED = "charging_vibration_enabled";
    private static final String KEY_WIRED_CHARGING_SOUNDS = "charging_sounds";
    private static final String KEY_WIRELESS_CHARGING_SOUNDS = "wireless_charging_sounds";

    // Used for power notification uri string if set to silent
    private static final String RINGTONE_SILENT_URI_STRING = "silent";

    private static final String DEFAULT_WIRED_CHARGING_SOUND =
            "/product/media/audio/ui/ChargingStarted.ogg";
    private static final String DEFAULT_WIRELESS_CHARGING_SOUND =
            "/product/media/audio/ui/WirelessChargingStarted.ogg";

    // Request code for charging notification ringtone picker
    private static final int REQUEST_CODE_WIRED_CHARGING_SOUND = 1;
    private static final int REQUEST_CODE_WIRELESS_CHARGING_SOUND = 2;

    private Preference mWiredChargingSounds;
    private Preference mWirelessChargingSounds;

    private Uri mDefaultWiredChargingSoundUri;
    private Uri mDefaultWirelessChargingSoundUri;

    private int mRequestCode;

    private final ActivityResultLauncher<Intent> mActivityResultLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() != Activity.RESULT_OK) {
                            return;
                        }
                        Intent data = result.getData();
                        if (data == null) {
                            return;
                        }
                        Uri uri = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI,
                                Uri.class);

                        if (uri == null) {
                            updateChargingSounds(RINGTONE_SILENT_URI_STRING,
                                    mRequestCode == REQUEST_CODE_WIRELESS_CHARGING_SOUND);
                            return;
                        }

                        String mimeType = requireContext().getContentResolver().getType(uri);
                        if (mimeType == null) {
                            Log.e(TAG, "call to updateChargingSounds for URI:" + uri
                                    + " ignored: failure to find mimeType "
                                    + "(no access from this context?)");
                            return;
                        }

                        if (!isSupportedMimeType(mimeType)) {
                            Log.e(TAG, "call to updateChargingSounds for URI:" + uri
                                    + " ignored: associated mimeType:" + mimeType
                                    + " is not an audio type");
                            return;
                        }

                        updateChargingSounds(uri.toString(),
                                mRequestCode == REQUEST_CODE_WIRELESS_CHARGING_SOUND);
            });

    private boolean isSupportedMimeType(String mimeType) {
        return mimeType.startsWith("audio/") || mimeType.equals("application/ogg");
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.charging_sounds_settings);

        Vibrator vibrator = requireActivity().getSystemService(Vibrator.class);
        if (vibrator == null || !vibrator.hasVibrator()) {
            removePreference(KEY_CHARGING_VIBRATION_ENABLED);
        }

        mWiredChargingSounds = findPreference(KEY_WIRED_CHARGING_SOUNDS);
        mWirelessChargingSounds = findPreference(KEY_WIRELESS_CHARGING_SOUNDS);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final String currentWiredChargingSound = Settings.Global.getString(getContentResolver(),
                Settings.Global.CHARGING_STARTED_SOUND);
        final String currentWirelessChargingSound = Settings.Global.getString(getContentResolver(),
                Settings.Global.WIRELESS_CHARGING_STARTED_SOUND);

        // Convert default sound file path to a media uri so that we can
        // set a proper default for the ringtone picker.
        mDefaultWiredChargingSoundUri = audioFileToUri(requireContext(),
                DEFAULT_WIRED_CHARGING_SOUND);
        mDefaultWirelessChargingSoundUri = audioFileToUri(requireContext(),
                DEFAULT_WIRELESS_CHARGING_SOUND);

        updateChargingSounds(currentWiredChargingSound, false /* wireless */);
        updateChargingSounds(currentWirelessChargingSound, true /* wireless */);
    }

    private Uri audioFileToUri(@NonNull Context context, String audioFile) {
        Cursor cursor = context.getContentResolver().query(
                MediaStore.Audio.Media.INTERNAL_CONTENT_URI,
                new String[] { MediaStore.Audio.Media._ID },
                MediaStore.Audio.Media.DATA + "=? ",
                new String[] { audioFile }, null);
        if (cursor == null) {
            return null;
        }
        if (!cursor.moveToFirst()) {
            cursor.close();
            return null;
        }
        int id = cursor.getInt(cursor.getColumnIndex(MediaStore.MediaColumns._ID));
        cursor.close();
        return Uri.withAppendedPath(MediaStore.Audio.Media.INTERNAL_CONTENT_URI,
                Integer.toString(id));
    }

    private void updateChargingSounds(String toneUriString, boolean wireless) {
        final String toneTitle;

        if (wireless) {
            if ((toneUriString == null || toneUriString.equals(DEFAULT_WIRELESS_CHARGING_SOUND))
                    && mDefaultWirelessChargingSoundUri != null) {
                toneUriString = mDefaultWirelessChargingSoundUri.toString();
            }
        } else {
            if ((toneUriString == null || toneUriString.equals(DEFAULT_WIRED_CHARGING_SOUND))
                    && mDefaultWiredChargingSoundUri != null) {
                toneUriString = mDefaultWiredChargingSoundUri.toString();
            }
        }

        if (toneUriString != null && !toneUriString.equals(RINGTONE_SILENT_URI_STRING)) {
            final Ringtone ringtone = RingtoneManager.getRingtone(getActivity(),
                    Uri.parse(toneUriString));
            if (ringtone != null) {
                toneTitle = ringtone.getTitle(getActivity());
            } else {
                // Unlikely to ever happen, but is possible if the ringtone
                // previously chosen is removed during an upgrade
                toneTitle = "";
                toneUriString = Settings.System.DEFAULT_NOTIFICATION_URI.toString();
            }
        } else {
            // Silent
            toneTitle = getString(R.string.charging_sounds_ringtone_silent);
            toneUriString = RINGTONE_SILENT_URI_STRING;
        }

        if (wireless) {
            mWirelessChargingSounds.setSummary(toneTitle);
            Settings.Global.putString(getContentResolver(),
                    Settings.Global.WIRELESS_CHARGING_STARTED_SOUND, toneUriString);
        } else {
            mWiredChargingSounds.setSummary(toneTitle);
            Settings.Global.putString(getContentResolver(),
                    Settings.Global.CHARGING_STARTED_SOUND, toneUriString);
        }
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (preference == mWiredChargingSounds) {
            launchNotificationSoundPicker(REQUEST_CODE_WIRED_CHARGING_SOUND,
                    Settings.Global.getString(getContentResolver(),
                            Settings.Global.CHARGING_STARTED_SOUND));
        } else if (preference == mWirelessChargingSounds) {
            launchNotificationSoundPicker(REQUEST_CODE_WIRELESS_CHARGING_SOUND,
                    Settings.Global.getString(getContentResolver(),
                            Settings.Global.WIRELESS_CHARGING_STARTED_SOUND));
        }
        return super.onPreferenceTreeClick(preference);
    }

    private void launchNotificationSoundPicker(int requestCode, String toneUriString) {
        final Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);

        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE,
                RingtoneManager.TYPE_NOTIFICATION);
        if (requestCode == REQUEST_CODE_WIRED_CHARGING_SOUND) {
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE,
                    getString(R.string.wired_charging_sounds_title));
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI,
                    mDefaultWiredChargingSoundUri);
        } else if (requestCode == REQUEST_CODE_WIRELESS_CHARGING_SOUND) {
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE,
                    getString(R.string.wireless_charging_sounds_title));
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI,
                    mDefaultWirelessChargingSoundUri);
        }
        if (toneUriString != null && !toneUriString.equals(RINGTONE_SILENT_URI_STRING)) {
            Uri uri = Uri.parse(toneUriString);
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, uri);
        }
        mRequestCode = requestCode;
        mActivityResultLauncher.launch(intent);
    }

    public static final Searchable.SearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider() {

        @Override
        public Set<String> getNonIndexableKeys(Context context) {
            final Set<String> result = new ArraySet<>();

            if (!context.getResources().getBoolean(org.lineageos.platform.internal.R.bool
                    .config_deviceSupportsWirelessCharging)) {
                result.add(KEY_WIRELESS_CHARGING_SOUNDS);
            }
            return result;
        }
    };
}
