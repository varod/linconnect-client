package com.willhauck.linconnectclient;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;

import java.util.Set;

/**
 * Created by vincent on 23/03/15.
 */
public class BluetoothSettingsActivity extends PreferenceActivity {
    PreferenceCategory bluetoothCategory;

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.pref_bluetooth);

        bluetoothCategory = (PreferenceCategory)findPreference("header_bluetooth");
        BluetoothAdapter BA;
        BA = BluetoothAdapter.getDefaultAdapter();
        if (BA != null && BA.isEnabled()) {

            Set<BluetoothDevice> pairedDevices;
            pairedDevices = BA.getBondedDevices();
            for(BluetoothDevice btDevice : pairedDevices ) {
                CheckBoxPreference c = new CheckBoxPreference(BluetoothSettingsActivity.this);
                c.setTitle(btDevice.getName().toString());
                c.setSummary(btDevice.getAddress().toString() );
                c.setChecked(false);

                bluetoothCategory.addPreference(c);
            }
        }

    }


}
