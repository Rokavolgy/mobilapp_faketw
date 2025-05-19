package com.app.fwitter;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.app.fwitter.controller.PostController;
import com.app.fwitter.modal.Following;
import com.app.fwitter.modal.Post;
import com.app.fwitter.modal.User;
import com.bumptech.glide.Glide;
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

public class ProfileActivity extends AppCompatActivity {

    private ImageButton backButton;
    private Button editProfileButton;
    private ImageView coverPhotoImageView, profileImageView;
    private TextView usernameTextView, handleTextView, bioTextView;
    private TextView followingCountTextView, followersCountTextView;
    private TabLayout profileTabLayout;
    private RecyclerView postsRecyclerView;

    private FirebaseAuth mAuth;
    private FirebaseUser currentUser;
    private FirebaseFirestore mFirestore;
    private String userId;

    private User user;

    private boolean isFollowing = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.profile_screen);

        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();
        mFirestore = FirebaseFirestore.getInstance();

        userId = getIntent().getStringExtra("userId");
        if (userId == null && currentUser != null) {
            userId = currentUser.getUid();
        }

        initializeViews();
        setupClickListeners();
        loadUserData();

    }

    private void initializeViews() {
        backButton = findViewById(R.id.backButton);
        editProfileButton = findViewById(R.id.editProfileButton);
        coverPhotoImageView = findViewById(R.id.coverPhotoImageView);
        profileImageView = findViewById(R.id.imageView2); // imageView2: profile image
        usernameTextView = findViewById(R.id.usernameTextView);
        handleTextView = findViewById(R.id.handleTextView);
        bioTextView = findViewById(R.id.bioTextView);
        followingCountTextView = findViewById(R.id.websiteText);
        followersCountTextView = findViewById(R.id.LocationText);
        profileTabLayout = findViewById(R.id.profileTabLayout);
        postsRecyclerView = findViewById(R.id.postsRecyclerView);
    }

    private void followUser()
    {
        if (currentUser != null && userId != null) {
            mFirestore.collection("followers")
                    .document(currentUser.getUid())
                    .set(new Following(currentUser.getUid(),userId))
                    .addOnSuccessListener(aVoid -> {
                        isFollowing = true;
                        Toast.makeText(ProfileActivity.this, "Followed", Toast.LENGTH_SHORT).show();
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(ProfileActivity.this, "Failed to follow: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
        }
    }

    private void unfollowUser(){
        if (currentUser != null && userId != null) {
            mFirestore.collection("followers")
                    .document(currentUser.getUid())
                    .delete()
                    .addOnSuccessListener(aVoid -> {
                        isFollowing = false;
                        //Toast.makeText(ProfileActivity.this, "Unfollowed", Toast.LENGTH_SHORT).show();
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(ProfileActivity.this, "Failed to unfollow: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
        }
    }

    private void checkIfFollowing() {
        if (currentUser != null && userId != null && !userId.equals(currentUser.getUid())) {
            mFirestore.collection("followers")
                    .document(currentUser.getUid())
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            editProfileButton.setText("Unfollow");
                            isFollowing = true;
                        } else {
                            editProfileButton.setText("Follow");
                            isFollowing = false;
                        }
                    })
                    .addOnFailureListener(e -> {
                        //Toast.makeText(ProfileActivity.this, "Failed to check follow status: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
        }
    }

    private void setupClickListeners() {
        backButton.setOnClickListener(v -> onBackPressed());

        editProfileButton.setOnClickListener(v -> {
            if (currentUser != null && userId.equals(currentUser.getUid())) {
                Intent intent = new Intent(ProfileActivity.this, ProfileEditActivity.class);
                startActivity(intent);
            } else {
                if(isFollowing)
                {
                    unfollowUser();
                    editProfileButton.setText("Follow");
                    loadUserStats();
                }
                else
                {
                    followUser();
                    editProfileButton.setText("Unfollow");
                    loadUserStats();
                }
            }
        });

        profileTabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                loadTabContent(tab.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {

            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                // ok
            }
        });
    }

    private void loadUserData() {
        if (userId != null) {
            mFirestore.collection("userdata").document(userId)
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            displayUserData(documentSnapshot);
                            loadUserStats(); //tenyleges betoltes (main oncreate)
                            setupRecyclerView(); //+ loaduserposts!!
                        } else {
                            Toast.makeText(ProfileActivity.this, "User profile not found", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(ProfileActivity.this,
                                "Failed to load profile: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    });
        }
    }

    private void displayUserData(DocumentSnapshot snapshot) {
        User user = snapshot.toObject(User.class);
        if (user != null) {
            this.user = user;
        }
        if (user != null) {
            usernameTextView.setText(user.getDisplayName());
            handleTextView.setText("@" + user.getUsername());
            String bio = user.getBio();
            String website = user.getWebsite();
            String location = user.getLocation();
            if (bio == null || bio.isEmpty()) {
                bio = "No bio available.";
            }
            if(website != null && !website.isEmpty() || location != null && !location.isEmpty()){
                bio += "\n";
            }

            if (website != null && !website.isEmpty()) {
                bio += "\n" + "🌍 " + website;
            }

            if (location != null && !location.isEmpty()) {
                bio += "\n" + "🗺️ " + location;
            }
            bioTextView.setText(bio);

            if (user.getProfileImageUrl() != null && !user.getProfileImageUrl().isEmpty()) {
                Glide.with(this)
                        .load(user.getProfileImageUrl())
                        .placeholder(R.mipmap.default_profile_image)
                        .error(R.mipmap.default_profile_image)
                        .into(profileImageView);
            }

            if (user.getCoverImageUrl() != null && !user.getCoverImageUrl().isEmpty()) {
                Glide.with(this)
                        .load(user.getCoverImageUrl())
                        .into(coverPhotoImageView);
            }

            if (currentUser == null || !userId.equals(currentUser.getUid())) {
                editProfileButton.setText("Follow");
            }
        }
    }

    private void loadUserStats() {
        mFirestore.collectionGroup("followers").whereEqualTo("userId", userId).get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    int followingCount = queryDocumentSnapshots.size();
                    followingCountTextView.setText(followingCount + " Following");
                });

        mFirestore.collectionGroup("followers").whereEqualTo("followingUserId", userId).get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    int followingCount = queryDocumentSnapshots.size();
                    followersCountTextView.setText(followingCount + " Followers");
                });
        checkIfFollowing();
    }

    private void setupRecyclerView() {
        postsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        loadTabContent(0);
    }

    private void loadTabContent(int tabPosition) {
        switch (tabPosition) {
            case 0:

                loadUserPosts();
                break;
            case 1:
                break;
            case 2:
                break;
        }
    }

    private void loadUserPosts() {
        mFirestore.collection("posts")
                .whereEqualTo("userId", userId)
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<Post> userPosts = new ArrayList<>();
                    for (DocumentSnapshot document : queryDocumentSnapshots) {
                        Post post = document.toObject(Post.class);
                        if (post != null) {
                            post.setUserName(this.user.getDisplayName());
                            post.setUserProfilePicUrl(this.user.getProfileImageUrl());
                            userPosts.add(post);
                        }
                    }

                    if (userPosts.isEmpty()) {
                        //Toast.makeText(ProfileActivity.this, "No posts found", Toast.LENGTH_SHORT).show();
                    } else {
                        PostAdapter adapter = new PostAdapter(userPosts);
                        adapter.setPostInteractionListener(new PostAdapter.PostInteractionListener() {
                            @Override
                            public void onLikeClicked(Post post, int position) {
                                Log.i("ok", "onLikeClicked: toewk");

                                boolean currentLikeStatus = post.isLikedByCurrentUser();
                                post.setLikedByCurrentUser(!currentLikeStatus);

                                int likesCount = post.getLikesCount();
                                if (currentLikeStatus) {
                                    likesCount -= 1;
                                } else {
                                    likesCount += 1;
                                }
                                post.setLikesCount(likesCount);

                                adapter.notifyItemChanged(position);
                                PostController pc = PostController.getInstance();
                                pc.toggleLike(post,currentLikeStatus);

                            }


                            @Override
                            public void onProfileClicked(Post post) {
                            }

                            @Override
                            public void onDeleteClicked(Post post) {
                                deletePost(post);
                            }
                        });

                        postsRecyclerView.setAdapter(adapter);
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(ProfileActivity.this, "Failed to load posts: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void deletePost(Post post) {
        if (post.getUserId().equals(currentUser.getUid())) {
            mFirestore.collection("posts").document(post.getId())
                    .delete()
                    .addOnSuccessListener(aVoid -> {
                        loadUserPosts();
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(ProfileActivity.this, "Failed to delete post: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
        }
    }



    @Override
    protected void onResume() {
        super.onResume();
        loadUserData();
    }
}