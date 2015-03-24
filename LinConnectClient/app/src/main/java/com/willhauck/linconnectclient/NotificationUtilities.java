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
import android.app.Notification;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceCategory;
import android.preference.PreferenceManager;
import android.util.Base64;
import android.util.Log;
import android.widget.RemoteViews;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.InputStreamBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@SuppressWarnings("deprecation")
public class NotificationUtilities {
    public static final UUID uuid = UUID.fromString("ef466c25-8211-438a-9f39-b8ddecf1fbb8");
    public static BluetoothAdapter BA = BluetoothAdapter.getDefaultAdapter();

    public static boolean sendData(Context c, Notification n, String packageName) {
        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(c);

        if (prefs.getBoolean("pref_toggle", false)
                && prefs.getBoolean(packageName, true)) {

            LinconnectNotification linNotif = new LinconnectNotification(n,c,packageName);

            if (prefs.getBoolean("pref_method_wifi", false))
                sendDataWifi(c,linNotif);

            if (prefs.getBoolean("pref_method_bt", false))
                sendDataBluetooth(c,linNotif);
        }
        return false;
    }

    public static boolean sendDataBluetooth(Context c, LinconnectNotification linNotif) {


        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(c);
        Set<String> bt_devices = prefs.getStringSet("header_bluetooth", new HashSet<String>());
        bt_devices.add(prefs.getString("bt_device", "None"));
        // Use PackageManager to get application name and icon
        final PackageManager pm = c.getPackageManager();
        for (String deviceStr : bt_devices ){
            //String deviceStr = prefs.getString("bt_device", "None");

            Log.i("LINCONNECT-BT", "bt device : " + deviceStr);

            if (BA.isEnabled() && deviceStr != "None") {
                BluetoothDevice BD = BA.getRemoteDevice(deviceStr);


                try {
                    BluetoothSocket BS = BD.createInsecureRfcommSocketToServiceRecord(uuid);

                    // Create socket and get streams
                    BA.cancelDiscovery();
                    BS.connect();
                    InputStream in = null;
                    OutputStream out = null;
                    in = BS.getInputStream();
                    out = BS.getOutputStream();
                    InputStream is = linNotif.getIconInputStream(c);
                    // Create icon buffer
                    byte[] iconBytes = new byte[is.available()];
                    is.read(iconBytes,0,is.available());

                    // Build notification header (without icon).
                    // JSON, because it's easy for logging on the server side.
                    JSONObject json = new JSONObject();
                    json.put("notificationheader",linNotif.getHeader());
                    json.put("notificationdescription",linNotif.getDescription());
                    json.put("iconsize",Integer.toString(iconBytes.length));
                    String dataStr = Base64.encodeToString(json.toString().getBytes("UTF-8"), Base64.URL_SAFE | Base64.NO_WRAP);

                    // Send header
                    out.write(dataStr.getBytes());

                    // Read answer to header
                    byte[] headerAnswerData = new byte[1024];
                    in.read(headerAnswerData);

                    // Answer was OK. Handle icon now.
                    String headerAnswer = new String(Base64.decode(headerAnswerData, Base64.URL_SAFE | Base64.NO_WRAP));
                    if (headerAnswer.equals("HEADEROK")) {
                        // Send the icon
                        out.write(iconBytes);

                        // Read answer to icon
                        byte[] iconAnswerData = new byte[1024];
                        in.read(iconAnswerData);

                        // Close socket
                        BS.close();

                        // Check if the answer was OK
                        String iconAnswer = new String(Base64.decode(iconAnswerData, Base64.URL_SAFE | Base64.NO_WRAP));
                        if (iconAnswer.equals("ICONOK"))
                            return true;
                        else
                            return false;
                    }
                    else {
                        // Close socket
                        BS.close();

                        return false;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }


        return false;
    }

    public static boolean sendDataWifi(Context c, LinconnectNotification linNotif) {
        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(c);

        ConnectivityManager connManager = (ConnectivityManager) c
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo mWifi = connManager
                .getNetworkInfo(ConnectivityManager.TYPE_WIFI);

        // Check Wifi state, whether notifications are enabled globally, and
        // whether notifications are enabled for specific application
        if (mWifi.isConnected()) {
            String ip = prefs.getString("pref_ip", "0.0.0.0:9090");

            HttpPost post = new HttpPost("http://" + ip + "/notif");
            // Create HTTP request
            MultipartEntity entity = new MultipartEntity();
            entity.addPart(
                    "notificon",
                    new InputStreamBody(ImageUtilities
                            .bitmapToInputStream(linNotif.getIcon()),
                            "drawable.png"));

            post.setEntity(entity);
            post.addHeader("notifheader", linNotif.getHeaderB64());
            post.addHeader("notifdescription", linNotif.getDescriptionB64());

            // Send HTTP request
            HttpClient client = new DefaultHttpClient();
            HttpResponse response;
            try {
                response = client.execute(post);
                String html = EntityUtils.toString(response.getEntity());
                if (html.contains("true")) {
                    return true;
                }
            } catch (ClientProtocolException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
        return false;
    }

    @SuppressLint("DefaultLocale")
    public static ArrayList<String> getNotificationText(
            Notification notification) {

        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            Bundle extras = notification.extras;

            ArrayList<String> notificationData = new ArrayList<String>();

            if (extras.get("android.title") != null) notificationData.add(extras.get("android.title").toString());
            if (extras.get("android.text") != null) notificationData.add(extras.get("android.text").toString());
            if (extras.get("android.subText") != null) notificationData.add(extras.get("android.subText").toString());

            return notificationData;
        }
        else {

            RemoteViews views = notification.contentView;
            Class<?> secretClass = views.getClass();

            try {
                ArrayList<String> notificationData = new ArrayList<String>();

                Field outerFields[] = secretClass.getDeclaredFields();
                for (int i = 0; i < outerFields.length; i++) {
                    if (!outerFields[i].getName().equals("mActions"))
                        continue;

                    outerFields[i].setAccessible(true);

                    @SuppressWarnings("unchecked")
                    ArrayList<Object> actions = (ArrayList<Object>) outerFields[i]
                            .get(views);
                    for (Object action : actions) {
                        Field innerFields[] = action.getClass().getDeclaredFields();

                        Object value = null;
                        for (Field field : innerFields) {
                            field.setAccessible(true);
                            // Value field could possibly contain text
                            if (field.getName().equals("value")) {
                                value = field.get(action);
                            }
                        }

                        // Check if value is a String
                        if (value != null
                                && value.getClass().getName().toUpperCase()
                                .contains("STRING")) {

                            notificationData.add(value.toString());
                        }
                    }

                    return notificationData;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return null;
    }


}


