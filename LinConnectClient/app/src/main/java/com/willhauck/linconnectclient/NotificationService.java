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

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Build;
import android.preference.PreferenceManager;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class NotificationService extends NotificationListenerService {
    public static final String ACTION_TOGGLE_NOTIF = "com.willhauck.linconnectclient.TOGGLE_NOTIF";
    private NotificationManager nm;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i("LinConnect", "Service Started.");
        showNotification();
    }
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i("LinConnect", "Received start id " + startId + ": " + intent);
        if(intent.getAction() == NotificationService.ACTION_TOGGLE_NOTIF)
            this.toogleNotificationDisplay();

        return START_STICKY; // run until explicitly stopped.
    }
	@Override
	public void onNotificationPosted(StatusBarNotification arg0) {
		NotificationUtilities.sendData(getApplicationContext(),
				arg0.getNotification(), arg0.getPackageName());
	}

	@Override
	public void onNotificationRemoved(StatusBarNotification arg0) {
	}

    private void showNotification() {
        nm = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);


        // In this sample, we'll use the same text for the ticker and the expanded notification
        CharSequence text = getText(R.string.service_started);
        // The PendingIntent to launch our activity if the user selects this notification

        Intent toggleIntent = new Intent(this, NotificationService.class).setAction(NotificationService.ACTION_TOGGLE_NOTIF);
        PendingIntent togglePendingIntent = PendingIntent.getService(this, 0, toggleIntent, 0);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, new Intent(this, SettingsActivity.class), 0);
        // Set the icon, scrolling text and timestamp

        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(this);

        Notification notification = null;

        if(prefs.getBoolean("pref_toggle",false)) {
            notification = new Notification.Builder(this)
                    .setSmallIcon(R.drawable.ic_launcher)
                    .setContentInfo(text)
                    .setContentTitle(getText(R.string.app_name))
                    .setContentIntent(contentIntent)
                    .addAction(R.drawable.ic_refresh, getText(R.string.service_stop_notif), togglePendingIntent)
                    .setTicker(text)
                    .build();

        }
        else{
            notification = new Notification.Builder(this)
                    .setSmallIcon(R.drawable.ic_computer)
                    .setContentInfo(text)
                    .setContentTitle(getText(R.string.app_name))
                    .setContentIntent(contentIntent)
                    .addAction(R.drawable.ic_refresh, getText(R.string.service_start_notif), togglePendingIntent)
                    .setTicker(text)
                    .build();
        }


        // Send the notification.
        // We use a layout id because it is a unique number.  We use it later to cancel.
        nm.notify(R.string.service_started, notification);
    }

    private void toogleNotificationDisplay(){
        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(this);
        Boolean toggle = prefs.getBoolean("pref_toggle", false);
        prefs.edit().putBoolean("pref_toggle", !toggle).apply();
        showNotification();
    }
}
