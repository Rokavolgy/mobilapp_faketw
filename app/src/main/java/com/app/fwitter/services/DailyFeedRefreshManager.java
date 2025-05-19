package com.app.fwitter.services;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.app.fwitter.FeedActivity;
import com.app.fwitter.LoginActivity;
import com.app.fwitter.R;
import com.app.fwitter.modal.Post;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class DailyFeedRefreshManager {
    private static final String TAG = "DailyFeedRefreshManager";
    private static final int REQUEST_CODE = 123;
    private static final String CHANNEL_ID = "daily_feed_channel";
    private static final String CHANNEL_NAME = "Daily Feed Updates";
    private static final int NOTIFICATION_ID = 1001;

    private Context context;
    private AlarmManager alarmManager;

    public DailyFeedRefreshManager(Context context) {
        this.context = context;
        this.alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        createNotificationChannel();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT);

            channel.setDescription("Daily updates from your feed");

            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    public void scheduleDailyRefresh() {

        //50 billió engedély kérése+%!%+"!+"!
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                requestExactAlarmPermission(context);
                return;
            }
        }
        Intent intent = new Intent(context, FeedRefreshReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 10);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);

        if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.getTimeInMillis(),
                    pendingIntent
            );
        } else {
            alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    calendar.getTimeInMillis(),
                    pendingIntent
            );
        }

        Log.d(TAG, "scheduled for " + calendar.getTime());
    }

    public void cancelScheduledRefresh() {
        Intent intent = new Intent(context, FeedRefreshReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        alarmManager.cancel(pendingIntent);
        Log.d(TAG, "nuuuuuuuuuuuuuuuuuu canceled");
    }

    public static class FeedRefreshReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            FirebaseFirestore db = FirebaseFirestore.getInstance();
            long twentyFourHoursAgo = System.currentTimeMillis() - (24 * 60 * 60 * 1000);

            db.collection("posts")
                    .whereGreaterThan("timestamp", twentyFourHoursAgo)
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .limit(10)
                    .get()
                    .addOnSuccessListener(queryDocumentSnapshots -> {
                        List<Post> recentPosts = new ArrayList<>();
                        queryDocumentSnapshots.forEach(doc -> recentPosts.add(doc.toObject(Post.class)));

                        if (!recentPosts.isEmpty()) {
                            showDailySummaryNotification(context, recentPosts);
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to fetch recent posts: " + e.getMessage());
                    });
        }

        private void showDailySummaryNotification(Context context, List<Post> recentPosts) {
            Intent intent = new Intent(context, LoginActivity.class);
            intent.putExtra("FRESH_FEED", true);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

            PendingIntent pendingIntent = PendingIntent.getActivity(
                    context,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            String title = "Your Daily Fwitter Update";
            StringBuilder contentText = new StringBuilder();

            int postCount = recentPosts.size();
            contentText.append(postCount).append(" new posts today. ");

            if (postCount > 0) {
                contentText.append("Latest from ");
                contentText.append(recentPosts.get(0).getUserName());
                if (postCount > 1) {
                    contentText.append(" and others");
                }
                contentText.append(".");
            }

            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle(title)
                    .setContentText(contentText.toString())
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true);

            if (postCount > 1) {
                NotificationCompat.InboxStyle inboxStyle = new NotificationCompat.InboxStyle()
                        .setBigContentTitle(title)
                        .setSummaryText(postCount + " new posts");

                for (int i = 0; i < Math.min(5, postCount); i++) {
                    Post post = recentPosts.get(i);
                    String preview = post.getUserName() + ": " +
                            (post.getContent().length() > 30 ?
                                    post.getContent().substring(0, 27) + "..." :
                                    post.getContent());
                    inboxStyle.addLine(preview);
                }

                builder.setStyle(inboxStyle);
            }

            NotificationManager notificationManager = (NotificationManager)
                    context.getSystemService(Context.NOTIFICATION_SERVICE);

            notificationManager.notify(NOTIFICATION_ID, builder.build());
            Log.d(TAG, "daily feed summary sent");
        }
    }

    public static void testNotificationNow(Context context) {
        Intent intent = new Intent(context, FeedRefreshReceiver.class);
        context.sendBroadcast(intent);
    }

    private void requestExactAlarmPermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent intent = new Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
            intent.setData(Uri.parse("package:" + context.getPackageName()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        }
    }

    public static void initialize(Context context) {
        DailyFeedRefreshManager manager = new DailyFeedRefreshManager(context);
        manager.scheduleDailyRefresh();
    }
}