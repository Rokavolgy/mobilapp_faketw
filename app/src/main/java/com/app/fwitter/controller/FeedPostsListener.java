package com.app.fwitter.controller;

import android.util.Log;

import com.app.fwitter.controller.PostController;
import com.app.fwitter.modal.Post;
import com.app.fwitter.modal.User;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FeedPostsListener {
    private static final String TAG = "FeedPostsListener";
    private final FirebaseFirestore db;
    private ListenerRegistration postsListener;
    private final PostsUpdateListener listener;
    private final Map<String, User> userDataCache = new HashMap<>();

    public interface PostsUpdateListener {
        void onPostsUpdated(List<Post> updatedPosts);
        void onError(Exception e);
    }

    public FeedPostsListener(PostsUpdateListener listener) {
        this.db = FirebaseFirestore.getInstance();
        this.listener = listener;
    }

    public void startListening() {
        if (postsListener != null) {
            return;
        }

        postsListener = db.collection("posts")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) {
                        Log.e(TAG, "Listen failed.", e);
                        listener.onError(e);
                        return;
                    }

                    List<Post> tempPosts = new ArrayList<>();
                    List<Task<Void>> tasks = new ArrayList<>();

                    assert snapshots != null;
                    for (DocumentSnapshot document : snapshots.getDocuments()) {
                        Post post = document.toObject(Post.class);
                        PostController pc = PostController.getInstance();

                        if (post != null) {
                            TaskCompletionSource<Void> taskSource = new TaskCompletionSource<>();
                            tasks.add(taskSource.getTask());

                            fetchUserFromFirestore(post.getUserId(), user -> {
                                if (user != null) {
                                    post.setUserName(user.getDisplayName());
                                    post.setUserProfilePicUrl(user.getProfileImageUrl());
                                    post.setLikedByCurrentUser(pc.isPostLiked(post.getId()));
                                }
                                tempPosts.add(post);
                                taskSource.setResult(null);
                            });
                        }
                    }

                    Tasks.whenAll(tasks).addOnSuccessListener(aVoid -> {
                        tempPosts.sort((post1, post2) -> post2.getTimestamp().compareTo(post1.getTimestamp()));
                        listener.onPostsUpdated(tempPosts);
                    });
                });
    }

    public void stopListening() {
        if (postsListener != null) {
            postsListener.remove();
            postsListener = null;
        }
    }

    private void fetchUserFromFirestore(String userId, OnUserFetchedListener listener) {
        if (userId == null || userId.isEmpty()) {
            listener.onUserFetched(null);
            return;
        }

        if (userDataCache.containsKey(userId)) {
            Log.d(TAG, "fetchUserFromFirestore: cached");
            listener.onUserFetched(userDataCache.get(userId));
            return;
        }

        db.collection("userdata").document(userId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    User user = documentSnapshot.toObject(User.class);
                    if (user != null) {
                        userDataCache.put(userId, user);
                    } else {
                        Log.d(TAG, "User not found: " + userId);
                    }
                    listener.onUserFetched(user);
                })
                .addOnFailureListener(e -> {
                    Log.d(TAG, "Failed to get user: " + userId, e);
                    listener.onUserFetched(null);
                });
    }

    private interface OnUserFetchedListener {
        void onUserFetched(User user);
    }
}