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

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.MulticastLock;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceManager;
import android.util.Base64;
import android.widget.EditText;
import android.widget.Toast;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.InputStreamBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Set;
import java.util.UUID;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceListener;

@SuppressWarnings("deprecation")
public class SettingsActivity extends PreferenceActivity {
	private String jmDnsServiceType = "_linconnect._tcp.local.";

	private JmDNS mJmDNS;
	private ServiceListener ServerListener;
	private WifiManager mWifiManager;
	public MulticastLock mMulticastLock;
	private Handler serverFoundHandler;
	private static SharedPreferences sharedPreferences;

	// Preferences
	Preference refreshPreference;
	Preference loadingPreference;
	Preference testBluetoothPreference;
    Preference launchBluetoothPreference;
	// Preference Categories
	static PreferenceCategory serverCategory;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mWifiManager = (android.net.wifi.WifiManager) getSystemService(android.content.Context.WIFI_SERVICE);
	}

	@Override
	protected void onDestroy() {
		// Remove Wifi Multicast lock
		if (mMulticastLock != null)
			mMulticastLock.release();
		try {
			mJmDNS.removeServiceListener(jmDnsServiceType, ServerListener);
			mJmDNS.close();
		} catch (Exception e) {
		}
		super.onDestroy();
	}

	@Override
	protected void onPostCreate(Bundle savedInstanceState) {
		super.onPostCreate(savedInstanceState);
		setupSimplePreferencesScreen();
	}

	@SuppressLint("SimpleDateFormat")
	private void setupSimplePreferencesScreen() {
		// Load preferences
		sharedPreferences = PreferenceManager
				.getDefaultSharedPreferences(SettingsActivity.this);

		// Add preferences from XML
		addPreferencesFromResource(R.xml.pref_general);
		bindPreferenceSummaryToValue(findPreference("pref_ip"));

		// Preference Categories
		serverCategory = ((PreferenceCategory) findPreference("cat_servers"));

		// Preferences
		refreshPreference = ((Preference) findPreference("pref_refresh"));
		serverCategory.removePreference(refreshPreference);

		loadingPreference = ((Preference) findPreference("pref_loading"));
		serverCategory.removePreference(loadingPreference);

		Preference prefEnable = findPreference("pref_enable");
		prefEnable
				.setOnPreferenceClickListener(new OnPreferenceClickListener() {
					@Override
					public boolean onPreferenceClick(Preference arg0) {
						// If Android 4.3+, open Notification Listener settings,
						// otherwise open accessibility settings
						if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
							startActivityForResult(
									new Intent(
											android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS),
									0);
						} else {
							Intent intent = new Intent(
									"android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS");
							startActivity(intent);
						}
						return true;
					}
				});

		((Preference) findPreference("pref_ip"))
				.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
					@Override
					public boolean onPreferenceChange(Preference arg0,
							Object arg1) {
						// Update Custom IP address summary
						arg0.setSummary((String) arg1);

						refreshServerList();

						// Create and send test notification
						SimpleDateFormat sf = new SimpleDateFormat("HH:mm:ss");
						Object[] notif = new Object[3];
						notif[0] = "Hello from Android!";
						notif[1] = "Test succesful @ " + sf.format(new Date());
						notif[2] = SettingsActivity.this.getResources()
								.getDrawable(R.drawable.ic_launcher);
						new TestTask().execute(notif);

						return true;
					}
				});

        launchBluetoothPreference = findPreference("bt_launch_settings");
        launchBluetoothPreference
                .setOnPreferenceClickListener(new OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        // Open bluetooth settings screen
                       Intent intent = new Intent(getApplicationContext(),
                                BluetoothSettingsActivity.class);

                        startActivity(intent);
                        return true;
                    }
                });
		testBluetoothPreference = findPreference("pref_test_bt");
		testBluetoothPreference
				.setOnPreferenceClickListener(new OnPreferenceClickListener() {
					@Override
					public boolean onPreferenceClick(Preference arg0) {
						// Create and send test notification
                        SimpleDateFormat sf = new SimpleDateFormat("HH:mm:ss");
                        Object[] notif = new Object[3];
                        notif[0] = "Hello from Android!";
                        notif[1] = "Test succesful @ " + sf.format(new Date());
                        notif[2] = SettingsActivity.this.getResources().getDrawable(R.drawable.ic_launcher);
                        new BluetoothTestTask().execute(notif);

                        return true;
					}
				});

		Preference prefDownload = findPreference("pref_download");
		prefDownload
				.setOnPreferenceClickListener(new OnPreferenceClickListener() {
					@Override
					public boolean onPreferenceClick(Preference arg0) {
						// Create share dialog with server download URL
						Intent sendIntent = new Intent();
						sendIntent.setAction(Intent.ACTION_SEND);
						sendIntent
								.putExtra(Intent.EXTRA_TEXT,
										"Download LinConnect server @ https://github.com/hauckwill/linconnect-server");
						sendIntent.setType("text/plain");
						startActivity(sendIntent);
						return true;
					}
				});

		Preference prefApplication = findPreference("pref_application");
		prefApplication
				.setOnPreferenceClickListener(new OnPreferenceClickListener() {
					@Override
					public boolean onPreferenceClick(Preference arg0) {
						// Open application settings screen
						Intent intent = new Intent(getApplicationContext(),
								ApplicationSettingsActivity.class);

						startActivity(intent);
						return true;
					}
				});

		Preference prefDonateBitcoin = findPreference("pref_donate_btc");
		prefDonateBitcoin
				.setOnPreferenceClickListener(new OnPreferenceClickListener() {
					@Override
					public boolean onPreferenceClick(Preference arg0) {
						try {
							// Open installed Bitcoin wallet if possible
							Intent intent = new Intent(Intent.ACTION_VIEW);
							intent.setData(Uri
									.parse("bitcoin:1125MguyS1feaop99bCDPQG6ukUcMuvVBo?label=Will%20Hauck&message=Donation%20for%20LinConnect"));
							startActivity(intent);
						} catch (Exception e) {
							// Otherwise, show dialog with Bitcoin address
							EditText input = new EditText(SettingsActivity.this);
							input.setText("1125MguyS1feaop99bCDPQG6ukUcMuvVBo");
							input.setEnabled(false);

							new AlertDialog.Builder(SettingsActivity.this)
									.setTitle("Bitcoin Address")
									.setMessage(
											"Please donate to the following Bitcoin address. Thank you for the support.")
									.setView(input)
									.setPositiveButton(
											"Copy Address",
											new DialogInterface.OnClickListener() {
												public void onClick(
														DialogInterface dialog,
														int whichButton) {
													android.text.ClipboardManager clipboard = (android.text.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
													clipboard
															.setText("1125MguyS1feaop99bCDPQG6ukUcMuvVBo");
												}
											})
									.setNegativeButton(
											"Okay",
											new DialogInterface.OnClickListener() {
												public void onClick(
														DialogInterface dialog,
														int whichButton) {
												}
											}).show();
						}
						return true;
					}
				});
		
		Preference prefGooglePlus = findPreference("pref_google_plus");
		prefGooglePlus
				.setOnPreferenceClickListener(new OnPreferenceClickListener() {
					@Override
					public boolean onPreferenceClick(Preference arg0) {
						// Open Google Plus page
						Intent intent = new Intent(Intent.ACTION_VIEW);
						intent.setData(Uri
								.parse("https://plus.google.com/114633032648182423928/posts"));
						startActivity(intent);
						return true;
					}
				});

		Preference prefDonatePlay = findPreference("pref_donate_play");
		prefDonatePlay
				.setOnPreferenceClickListener(new OnPreferenceClickListener() {
					@Override
					public boolean onPreferenceClick(Preference arg0) {
						// Open Donation Key app on Play Store
						Intent intent = new Intent(Intent.ACTION_VIEW);
						intent.setData(Uri
								.parse("market://details?id=com.willhauck.donation"));
						startActivity(intent);
						return true;
					}
				});

		// Create handler to process a detected server
		serverFoundHandler = new Handler(new Handler.Callback() {
			@Override
			public boolean handleMessage(Message msg) {
				if (msg.obj != null) {

					javax.jmdns.ServiceInfo serviceInfo = mJmDNS
							.getServiceInfo(jmDnsServiceType, (String) msg.obj);

					// Get info about server
					String name = serviceInfo.getName();
					String port = String.valueOf(serviceInfo.getPort());
					String ip = serviceInfo.getHostAddresses()[0];

					// Create a preference representing the server
					Preference p = new Preference(SettingsActivity.this);
					p.setTitle(name);
					p.setSummary(ip + ":" + port);

					p.setOnPreferenceClickListener(new OnPreferenceClickListener() {
						@Override
						public boolean onPreferenceClick(Preference arg0) {
							refreshServerList();

							// Save IP address in preferences
							Editor e = sharedPreferences.edit();
							e.putString("pref_ip", arg0.getSummary().toString());
							e.apply();

							// Create and send test notification
							SimpleDateFormat sf = new SimpleDateFormat(
									"HH:mm:ss");

							Object[] notif = new Object[3];
							notif[0] = "Hello from Android!";
							notif[1] = "Test succesful @ "
									+ sf.format(new Date());
							notif[2] = SettingsActivity.this.getResources()
									.getDrawable(R.drawable.ic_launcher);

							new TestTask().execute(notif);

							return true;
						}

					});

					// Add preference to server list if it doesn't already exist
					boolean found = false;
					for (int i = 0; i < serverCategory.getPreferenceCount(); i++) {
						if (serverCategory.getPreference(i) != null
								&& serverCategory.getPreference(i).getTitle() != null
								&& serverCategory.getPreference(i).getTitle()
										.equals(p.getTitle())) {
							found = true;
						}
					}
					if (!found) {
						serverCategory.addPreference(p);
					}

					refreshServerList();

					// Remove loading indicator, add refresh indicator if it
					// isn't already there
					if (findPreference("pref_loading") != null)
						serverCategory
								.removePreference(findPreference("pref_loading"));
					if (findPreference("pref_refresh") == null)
						serverCategory.addPreference(refreshPreference);

				}
				return true;
			}
		});

		// Create task to scan for servers
		class ServerScanTask extends AsyncTask<String, ServiceEvent, Boolean> {

			@Override
			protected void onPreExecute() {
				// Remove refresh preference, add loading preference
				if (findPreference("pref_refresh") != null)
					serverCategory.removePreference(refreshPreference);
				serverCategory.addPreference(loadingPreference);

				try {
					mJmDNS.removeServiceListener(jmDnsServiceType,
							ServerListener);
				} catch (Exception e) {
				}

				refreshServerList();

			}

			@Override
			protected Boolean doInBackground(String... notif) {
				WifiInfo wifiinfo = mWifiManager.getConnectionInfo();
				int intaddr = wifiinfo.getIpAddress();

				// Ensure there is an active Wifi connection
				if (intaddr != 0) {
					byte[] byteaddr = new byte[] { (byte) (intaddr & 0xff),
							(byte) (intaddr >> 8 & 0xff),
							(byte) (intaddr >> 16 & 0xff),
							(byte) (intaddr >> 24 & 0xff) };
					InetAddress addr = null;
					try {
						addr = InetAddress.getByAddress(byteaddr);
					} catch (UnknownHostException e1) {
					}

					// Create Multicast lock (required for JmDNS)
					mMulticastLock = mWifiManager
							.createMulticastLock("LinConnect");
					mMulticastLock.setReferenceCounted(true);
					mMulticastLock.acquire();

					try {
						mJmDNS = JmDNS.create(addr, "LinConnect");
					} catch (IOException e) {
					}

					// Create listener for detected servers
					ServerListener = new ServiceListener() {

						@Override
						public void serviceAdded(ServiceEvent arg0) {
							final String name = arg0.getName();
							// Send the server data to the handler, delayed by
							// 500ms to ensure all information is read
							serverFoundHandler.sendMessageDelayed(Message
									.obtain(serverFoundHandler, -1, name), 500);
						}

						@Override
						public void serviceRemoved(ServiceEvent arg0) {
						}

						@Override
						public void serviceResolved(ServiceEvent arg0) {
							mJmDNS.requestServiceInfo(arg0.getType(),
									arg0.getName(), 1);
						}
					};
					mJmDNS.addServiceListener(jmDnsServiceType, ServerListener);

					return true;
				}
				return false;
			}

			@Override
			protected void onPostExecute(Boolean result) {
				if (!result) {
					// Notify user if there is no connection
					if (findPreference("pref_loading") != null) {
						serverCategory
								.removePreference(findPreference("pref_loading"));
						serverCategory.addPreference(refreshPreference);

					}
					Toast.makeText(getApplicationContext(),
							"Error: no connection.", Toast.LENGTH_LONG).show();
				}
			}
		}

		refreshPreference
				.setOnPreferenceClickListener(new OnPreferenceClickListener() {

					@Override
					public boolean onPreferenceClick(Preference arg0) {
						new ServerScanTask().execute();
						return true;
					}

				});

		// Start scanning for servers
		new ServerScanTask().execute();
	}

	private static Preference.OnPreferenceChangeListener sBindPreferenceSummaryToValueListener = new Preference.OnPreferenceChangeListener() {
		@Override
		public boolean onPreferenceChange(Preference preference, Object value) {
			String stringValue = value.toString();

			if (preference instanceof ListPreference) {
				ListPreference listPreference = (ListPreference) preference;
				int index = listPreference.findIndexOfValue(stringValue);

				preference
						.setSummary(index >= 0 ? listPreference.getEntries()[index]
								: null);
			} else {
				preference.setSummary(stringValue);
			}
			return true;
		}
	};

	private static void bindPreferenceSummaryToValue(Preference preference) {
		preference
				.setOnPreferenceChangeListener(sBindPreferenceSummaryToValueListener);

		sBindPreferenceSummaryToValueListener.onPreferenceChange(
				preference,
				PreferenceManager.getDefaultSharedPreferences(
						preference.getContext()).getString(preference.getKey(),
						""));
	}

	class TestTask extends AsyncTask<Object, Void, Boolean> {
		@SuppressLint("SimpleDateFormat")
		@Override
		protected Boolean doInBackground(Object... notif) {

            LinconnectNotification linNotif = new LinconnectNotification((String) notif[0], (String) notif[1], ImageUtilities.drawableToBitmap((Drawable) notif[2]));

            return NotificationUtilities.sendDataWifi(getApplicationContext(),linNotif);

		}

		@Override
		protected void onPostExecute(Boolean result) {
			if (result == true) {
				Toast.makeText(getApplicationContext(),
						"Test notification recieved.", Toast.LENGTH_SHORT)
						.show();
			} else {
				Toast.makeText(
						getApplicationContext(),
						"Test notification not recieved. Ensure the server is updated to the latest version.",
						Toast.LENGTH_LONG).show();
			}

		}
	}

	class BluetoothTestTask extends AsyncTask<Object, Void, Boolean> {
		@SuppressLint("SimpleDateFormat")
		@Override
		protected Boolean doInBackground(Object... notif) {
            LinconnectNotification linNotif = new LinconnectNotification((String) notif[0], (String) notif[1], ImageUtilities.drawableToBitmap((Drawable) notif[2]));

            return NotificationUtilities.sendDataBluetooth(getApplicationContext(),linNotif);

        }

		@Override
		protected void onPostExecute(Boolean result) {
			if (result == true) {
				Toast.makeText(getApplicationContext(),
						"Test notification received.", Toast.LENGTH_SHORT)
						.show();
			} else {
				Toast.makeText(
						getApplicationContext(),
						"Test notification not received. Ensure the server is updated to the latest version.",
						Toast.LENGTH_LONG).show();
			}

		}
	}

	public static void refreshServerList() {

		// Determine server list icons based on IP address
		String ip = sharedPreferences.getString("pref_ip", "0.0.0.0:9090");
		for (int i = 0; i < serverCategory.getPreferenceCount() - 1; i++) {
			Preference p = serverCategory.getPreference(i);
			if (ip.equals(p.getSummary().toString())) {
				p.setIcon(R.drawable.ic_checkmark);
			} else {
				p.setIcon(R.drawable.ic_computer);
			}
		}
	}
}
