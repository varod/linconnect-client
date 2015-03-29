/*
 	LinConnect: Mirror Android notifications on Linux Desktop

    Copyright (C) 2013  Will Hauck

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package com.willhauck.linconnectclient;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceManager;

import java.util.Set;

public class BluetoothSettingsActivity extends PreferenceActivity {
    PreferenceCategory bluetoothCategory;

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(BluetoothSettingsActivity.this);

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
                c.setKey(btDevice.getAddress());

                c.setChecked(false);

                c.setOnPreferenceChangeListener(
                        new Preference.OnPreferenceChangeListener() {
                                @Override
                                public boolean onPreferenceChange(Preference pref,
                                                                  Object obj) {
                                    return true;
                                }
                            });

                 bluetoothCategory.addPreference(c);
            }
        }

    }


}
