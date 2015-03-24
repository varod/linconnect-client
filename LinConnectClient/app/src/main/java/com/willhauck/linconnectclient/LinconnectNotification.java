package com.willhauck.linconnectclient;

import android.app.Notification;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.util.Base64;

import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;

/**
 * Created by vincent on 23/03/15.
 */
public class LinconnectNotification{


    private String mHeader;
    private String mDescription;
    private Bitmap mIcon;

    public LinconnectNotification(String header, String description, Bitmap icon){
        mHeader = header;
        mDescription = description;
        mIcon = icon;
    }

    public LinconnectNotification(Notification n, Context c, String packageName) {

        // Magically extract text from notification
        ArrayList<String> notificationData = NotificationUtilities
                .getNotificationText(n);

        // Use PackageManager to get application name and icon
        final PackageManager pm = c.getPackageManager();
        ApplicationInfo ai;
        try {
            ai = pm.getApplicationInfo(packageName, 0);
        } catch (final PackageManager.NameNotFoundException e) {
            ai = null;
        }

        String notificationBody = "";
        String notificationHeader = "";
        // Create header and body of notification
        if (notificationData.size() > 0) {
            notificationHeader = notificationData.get(0);
            if (notificationData.size() > 1) {
                notificationBody = notificationData.get(1);
            }
        }

        for (int i = 2; i < notificationData.size(); i++) {
            notificationBody += "\n" + notificationData.get(i);
        }

        // Append application name to body
        if (pm.getApplicationLabel(ai) != null) {
            if (notificationBody.isEmpty()) {
                notificationBody = "via " + pm.getApplicationLabel(ai);
            } else {
                notificationBody += " (via " + pm.getApplicationLabel(ai) + ")";
            }
        }

        mHeader = notificationHeader;
        mDescription = notificationBody;

        if(n.largeIcon != null)
            mIcon = n.largeIcon;
        else
            mIcon = ImageUtilities.drawableToBitmap(pm.getApplicationIcon(ai));

    }


    public String getHeader() {
        return mHeader;
    }

    public String getHeaderB64() { return getBase64(mHeader); }
    public String getDescriptionB64(){return getBase64(mDescription);}


    public void setHeader(String mHeader) {
        this.mHeader = mHeader;
    }

    public String getDescription() {
        return mDescription;
    }

    public void setDescription(String mDescription) {
        this.mDescription = mDescription;
    }

    public Bitmap getIcon() {
        return mIcon;
    }

    public void setIcon(Bitmap mIcon) {
        this.mIcon = mIcon;
    }

    public InputStream getIconInputStream(Context c){
        // If the notification contains an icon, use it
        InputStream is;
        if (mIcon != null) {
            is = ImageUtilities.bitmapToInputStream(mIcon);
        }
        // Otherwise, use the application's icon
        else {

            is = ImageUtilities.bitmapToInputStream(ImageUtilities
                    .drawableToBitmap(c.getResources()
                            .getDrawable(R.drawable.ic_launcher)));
        }
        return is;
    }

    public static String getBase64(String message) {
        String messageB64;
        try {
            messageB64 = Base64.encodeToString(message.getBytes("UTF-8"), Base64.URL_SAFE | Base64.NO_WRAP);
        }
        catch (UnsupportedEncodingException e){
            messageB64 = Base64.encodeToString(message.getBytes(), Base64.URL_SAFE | Base64.NO_WRAP);
        }
        return messageB64;
    }



}