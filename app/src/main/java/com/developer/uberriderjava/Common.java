package com.developer.uberriderjava;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import com.developer.uberriderjava.models.DriverGeoModel;
import com.developer.uberriderjava.models.RiderModel;
import com.google.android.gms.maps.model.Marker;
import java.util.HashMap;
import java.util.HashSet;

public class Common {
    public static final String RIDER_INFO_REFERENCE = "Riders";
    public static final String DRIVERS_LOCATION_REFERENCES = "DriversLocation";
    public static final String DRIVER_INFO_REFERENCE = "DriverInfo";
    public static RiderModel currentRider;
    public static final String TOKEN_REFERENCE = "Token";
    public static final String NOTIFICATION_TITLE = "title";
    public static final String NOTIFICATION_CONTENT = "body";
    public static HashSet<DriverGeoModel> driversFound = new HashSet<>();
    public static HashMap<String, Marker> markerList = new HashMap<>();

    public static String buildWelcomeMessage() {
        if (Common.currentRider != null) {
            return "Welcome " +
                    Common.currentRider.getFirstName() +
                    " " +
                    Common.currentRider.getLastName();
        } else {
            return "";
        }
    }

    public static void showNotification(Context context, int id, String title, String body, Intent intent) {
        PendingIntent pendingIntent;
        if (intent != null) {
            pendingIntent = PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT);
            String NOTIFICATION_CHANNEL_ID = "uber_remake_channel";
            NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel notificationChannel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, "uber_remake", NotificationManager.IMPORTANCE_HIGH);
                notificationChannel.setDescription("Uber remake");
                notificationChannel.enableLights(true);
                notificationChannel.setLightColor(Color.RED);
                notificationChannel.setVibrationPattern(new long[]{0, 100, 500, 1000});
                notificationManager.createNotificationChannel(notificationChannel);
            }
            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID);
            builder.setContentTitle(title)
                    .setContentText(body)
                    .setAutoCancel(false)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setDefaults(Notification.DEFAULT_VIBRATE)
                    .setSmallIcon(R.drawable.ic_baseline_directions_car_24)
                    .setLargeIcon(BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_baseline_directions_car_24));
            if (pendingIntent != null) {
                builder.setContentIntent(pendingIntent);
            }
            Notification notification = builder.build();
            notificationManager.notify(id, notification);
        }
    }

    public static String buildName(String firstName, String lastName) {
        return firstName + " " + lastName;
    }
}
