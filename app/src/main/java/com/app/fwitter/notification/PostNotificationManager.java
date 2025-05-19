package com.app.fwitter.notification;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.app.fwitter.FeedActivity;
import com.app.fwitter.R;
import com.app.fwitter.controller.FeedPostsListener;
import com.app.fwitter.modal.Post;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PostNotificationManager implements FeedPostsListener.PostsUpdateListener {
    private static final String TAG = "PostNotificationManager";
    private static final String CHANNEL_ID = "new_post_channel";
    private static final String CHANNEL_NAME = "New Post Notifications";

    private static PostNotificationManager instance;

    private Context context;
    private FirebaseUser currentUser;
    private Set<String> processedPostIds;
    private FeedPostsListener feedPostsListener;
    private boolean isListening = false;
    private FirebaseAuth.AuthStateListener authStateListener;

    private PostNotificationManager(Context context) {
        this.context = context.getApplicationContext();
        this.processedPostIds = new HashSet<>();
        createNotificationChannel();

        authStateListener = firebaseAuth -> {
            FirebaseUser user = firebaseAuth.getCurrentUser();
            if (user != null && (currentUser == null || !isListening)) {
                currentUser = user;
                startListening();
            } else if (user == null && isListening) {
                stopListening();
                currentUser = null;
            }
        };

        FirebaseAuth.getInstance().addAuthStateListener(authStateListener);

        currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            startListening();
        }
    }

    public static synchronized PostNotificationManager getInstance(Context context) {
        if (instance == null) {
            instance = new PostNotificationManager(context);
        }
        return instance;
    }

    public void startListening() {
        if (isListening || currentUser == null) return;

        feedPostsListener = new FeedPostsListener(this);
        feedPostsListener.startListening();
        isListening = true;

        Log.d(TAG, "Post notification manager registered with user: " + currentUser.getUid());
    }

    public void stopListening() {
        if (feedPostsListener != null) {
            feedPostsListener.stopListening();
            feedPostsListener = null;
            isListening = false;
            Log.d(TAG, "Post notification listener stopped");
        }
    }

    public void cleanup() {
        stopListening();
        if (authStateListener != null) {
            FirebaseAuth.getInstance().removeAuthStateListener(authStateListener);
        }
        instance = null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT);

            channel.setDescription("Notifications for new posts");

            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    @Override
    public void onPostsUpdated(List<Post> updatedPosts) {
        if (currentUser == null) return;

        for (Post post : updatedPosts) {
            if (!processedPostIds.contains(post.getId()) && !post.getUserId().equals(currentUser.getUid())) {
                processedPostIds.add(post.getId());
                showNotification(post);
                break;
            }
        }
    }

    @Override
    public void onError(Exception e) {
        Log.e(TAG, "Error in post listener", e);
    }

    private void showNotification(Post post) {
        Intent intent = new Intent(context, FeedActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String title = post.getUserName() + " posted";
        String content = post.getContent();
        if (content.length() > 100) {
            content = content.substring(0, 97) + "...";
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(content)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        NotificationManager notificationManager = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);

        int notificationId = post.getId().hashCode();
        notificationManager.notify(notificationId, builder.build());

        Log.d(TAG, "Notification shown for post: " + post.getId());
    }



    public static void initialize(Application app) {
        getInstance(app);
    }
}