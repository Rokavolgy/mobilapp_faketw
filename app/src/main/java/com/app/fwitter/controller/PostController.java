package com.app.fwitter.controller;

import com.app.fwitter.modal.Post;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class PostController {

    private static PostController instance;
    private final FirebaseFirestore db;

    private final Map<String,Boolean> likedPosts = new HashMap<>();

    private PostController() {
        db = FirebaseFirestore.getInstance();
        loadLikedPosts();
    }

    public static synchronized PostController getInstance() {
        if (instance == null) {
            instance = new PostController();
        }
        return instance;
    }


    public void loadLikedPosts() {
        String userId = FirebaseAuth.getInstance().getUid();
        db.collectionGroup("likes").whereEqualTo("userId", userId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    for (DocumentSnapshot document : queryDocumentSnapshots.getDocuments()) {
                        String postId = document.getId();
                        likedPosts.put(postId, true);
                    }
                })
                .addOnFailureListener(e -> {

                });
    }


    public boolean isPostLiked(String postId) {
        String userId = FirebaseAuth.getInstance().getUid();
        if (likedPosts.containsKey(postId)) {
            return likedPosts.get(postId);
        } else {
            db.collection("posts").document(postId)
                    .collection("likes").document(userId)
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            likedPosts.put(postId, true);
                        } else {
                            likedPosts.put(postId, false);
                        }
                    });
            return false;
        }
        }

    public void toggleLike(Post post, boolean currentLikeStatus) {
        String userId = FirebaseAuth.getInstance().getUid();

        if (!currentLikeStatus) {
            db.collection("posts").document(post.getId())
                    .collection("likes").document(userId)
                    .set(new HashMap<String, Object>() {{
                        put("timestamp", new Date());
                    }});

            db.collection("userdata").document(userId)
                    .collection("likes").document(post.getId())
                    .set(new HashMap<String, Object>() {{
                        put("timestamp", new Date());
                    }});

            db.runTransaction(transaction -> {
                DocumentSnapshot snapshot = transaction.get(db.collection("posts").document(post.getId()));
                long newLikesCount = snapshot.getLong("likesCount") + 1;
                transaction.update(db.collection("posts").document(post.getId()), "likesCount", newLikesCount);
                return null;
            });
            likedPosts.put(post.getId(), true);
        } else {
            db.collection("posts").document(post.getId())
                    .collection("likes").document(userId)
                    .delete();

            db.collection("userdata").document(userId)
                    .collection("likes").document(post.getId())
                    .delete();

            db.runTransaction(transaction -> {
                DocumentSnapshot snapshot = transaction.get(db.collection("posts").document(post.getId()));
                long newLikesCount = snapshot.getLong("likesCount") - 1;
                transaction.update(db.collection("posts").document(post.getId()), "likesCount", newLikesCount);
                return null;
            });
            likedPosts.put(post.getId(), false);
        }
    }
}