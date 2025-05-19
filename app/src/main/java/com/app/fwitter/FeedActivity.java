package com.app.fwitter;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;
import android.widget.Button;
import android.widget.EditText;
import android.Manifest;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.app.fwitter.controller.FeedPostsListener;
import com.app.fwitter.controller.PostController;
import com.app.fwitter.modal.CurrentUser;
import com.app.fwitter.modal.Post;
import com.app.fwitter.modal.User;
import com.app.fwitter.notification.PostNotificationManager;
import com.app.fwitter.services.DailyFeedRefreshManager;
import com.app.fwitter.task.ImageUploader;
import com.bumptech.glide.Glide;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

interface OnUserFetchedListener {
    void onUserFetched(User user);
}
public class FeedActivity extends AppCompatActivity{

    private FeedPostsListener postsListener;
    private RecyclerView recyclerView;
    private PostAdapter adapter;
    private List<Post> posts;
    private FloatingActionButton createPostFab;

    private ImageView profileImageView;

    private static final int CAMERA_PERMISSION_CODE = 2;
    private static final int CAMERA_REQUEST_CODE = 3;

    private static final Map<String,User> userDataCache = new HashMap<String,User>();
    private Uri cameraImageUri;
    private static final int PICK_IMAGE_REQUEST = 1;
    private Uri selectedImageUri;
    private ImageView imagePreview;

    private void fetchPostsFromFirestore() {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("posts")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    posts.clear();
                    List<Post> tempPosts = new ArrayList<>();
                    List<Task<Void>> tasks = new ArrayList<>();

                    for (DocumentSnapshot document : queryDocumentSnapshots) {
                        Post post = document.toObject(Post.class);
                        if (post != null) {
                            TaskCompletionSource<Void> taskSource = new TaskCompletionSource<>();
                            tasks.add(taskSource.getTask());

                            fetchUserFromFirestore(post.getUserId(), user -> {
                                if (user != null) {
                                    post.setUserName(user.getDisplayName());
                                    post.setUserProfilePicUrl(user.getProfileImageUrl());
                                }
                                tempPosts.add(post);
                                taskSource.setResult(null);
                            });
                        }
                    }

                    Tasks.whenAll(tasks).addOnSuccessListener(aVoid -> {
                        tempPosts.sort((post1, post2) -> post2.getTimestamp().compareTo(post1.getTimestamp()));
                        posts.addAll(tempPosts);
                        adapter.notifyDataSetChanged();
                        fetchLikedPostsForCurrentUser();
                    });
                })
                .addOnFailureListener(e -> {
                    Log.e("FeedActivity", "Error fetching posts", e);
                    Toast.makeText(FeedActivity.this, "Failed to load posts", Toast.LENGTH_SHORT).show();
                });
    }


    private void fetchLikedPostsForCurrentUser() {
        String userId = FirebaseAuth.getInstance().getUid();
        if (userId == null) {
            return;
        }

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collectionGroup("likes").whereEqualTo("userId", userId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    Set<String> likedPostIds = new HashSet<>();
                    for (DocumentSnapshot document : queryDocumentSnapshots) {
                        String postId = document.getString("postId");
                        if (postId != null) {
                            likedPostIds.add(postId);
                        }
                    }

                    for (Post post : posts) {
                        if (likedPostIds.contains(post.getId())) {
                            post.setLikedByCurrentUser(true);
                        }
                    }
                    adapter.notifyDataSetChanged();
                })
                .addOnFailureListener(e -> {
                    Log.e("FeedActivity", "Error fetching liked posts", e);
                });
    }
    private void fetchUserFromFirestore(String userId, OnUserFetchedListener listener) {
        if (userId == null || userId.isEmpty()) {
            listener.onUserFetched(null);
            return;
        }

        if (userDataCache.containsKey(userId)) {
            Log.d("a", "fetchUserFromFirestore: cached");
            listener.onUserFetched(userDataCache.get(userId));
            return;
        }



        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("userdata").document(userId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    User user = documentSnapshot.toObject(User.class);
                    if (user != null) {
                        userDataCache.put(userId, user);
                    } else {
                        Log.d("FeedActivity", "User not found: " + userId);
                    }
                    listener.onUserFetched(user);
                })
                .addOnFailureListener(e -> {
                    Log.d("FeedActivity", "Failed to get user: " + userId, e);
                    listener.onUserFetched(null);
                });
    }




    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.feed_layout);


        posts = new ArrayList<>();

        postsListener = new FeedPostsListener(new FeedPostsListener.PostsUpdateListener() {
            @Override
            public void onPostsUpdated(List<Post> updatedPosts) {
                posts.clear();
                posts.addAll(updatedPosts);
                adapter.notifyDataSetChanged();
                fetchLikedPostsForCurrentUser();
            }

            @Override
            public void onError(Exception e) {
                Toast.makeText(FeedActivity.this, "Failed to load posts", Toast.LENGTH_SHORT).show();
            }
        });
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        1001);
            }
        }

        recyclerView = findViewById(R.id.postsRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new PostAdapter(posts);
        adapter.setPostInteractionListener(createPostInteractionListener());

        profileImageView = findViewById(R.id.profileView);
        profileImageView.setOnClickListener(v -> {
            Intent intent = new Intent(FeedActivity.this, ProfileActivity.class);
            startActivity(intent);
        });
        String profileImageUrl = CurrentUser.getInstance().getCurrentUser().getProfileImageUrl();
        if (profileImageUrl != null && !profileImageUrl.isEmpty()) {
            Glide.with(this)
                    .load(profileImageUrl)
                    .placeholder(R.mipmap.default_profile_image)
                    .into(profileImageView);
        } else {
            profileImageView.setImageResource(R.mipmap.default_profile_image);
        }

        recyclerView.setAdapter(adapter);

        createPostFab = findViewById(R.id.createPostFab);
        createPostFab.setOnClickListener(v -> showPostCreationDialog());

        postsListener.startListening();

    }
    @Override
    protected void onPause()
    {
        super.onPause();
        if (postsListener != null) {
            PostNotificationManager.initialize(this.getApplication());
            postsListener.stopListening();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (postsListener != null) {
            PostNotificationManager.initialize(this.getApplication());
            postsListener.startListening();
        }
    }



    //wow ondestroy!!!!
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (postsListener != null) {
            PostNotificationManager.initialize(this.getApplication());
            postsListener.stopListening();
        }
    }
private PostAdapter.PostInteractionListener createPostInteractionListener() {
        return new PostAdapter.PostInteractionListener() {
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
                    Intent intent = new Intent(FeedActivity.this, ProfileActivity.class);
                    intent.putExtra("userId", post.getUserId());
                    startActivity(intent);
            }

            @Override
            public void onDeleteClicked(Post post) {
                FirebaseFirestore db = FirebaseFirestore.getInstance();
                db.collection("posts")
                        .document(post.getId())
                        .delete()
                        .addOnSuccessListener(aVoid -> {
                            onPostDeleteSuccess(post);
                        })
                        .addOnFailureListener(e -> {
                            Toast.makeText(FeedActivity.this, "Sorry, unsuccessful post deletion please try again." + e.getMessage(), Toast.LENGTH_LONG).show();
                        });
            }
        };
    }

    private void requestCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
        } else {
            openCamera();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera();
            } else {
                Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void showPostCreationDialog() {
        DailyFeedRefreshManager.testNotificationNow(this);
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View sheetView = getLayoutInflater().inflate(R.layout.new_post_bottom_sheet, null);
        dialog.setContentView(sheetView);

        EditText postContentEditText = sheetView.findViewById(R.id.postContentEditText);
        Button postButton = sheetView.findViewById(R.id.postButton);
        ImageButton addImageButton = sheetView.findViewById(R.id.addImageButton);
        ImageButton cameraButton = sheetView.findViewById(R.id.cameraButton);
        ImageView profileImageView = sheetView.findViewById(R.id.profileView);

        LinearLayout mediaPreviewContainer = sheetView.findViewById(R.id.mediaPreviewContainer);

        String profileImageUrl = CurrentUser.getInstance().getCurrentUser().getProfileImageUrl();
        if (profileImageUrl != null && !profileImageUrl.isEmpty()) {
            Glide.with(this)
                    .load(profileImageUrl)
                    .placeholder(R.mipmap.default_profile_image)
                    .into(profileImageView);
        } else {
            profileImageView.setImageResource(R.mipmap.default_profile_image);
        }

        imagePreview = new ImageView(this);
        imagePreview.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                300));
        imagePreview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        mediaPreviewContainer.addView(imagePreview);

        addImageButton.setOnClickListener(v -> {
            Intent intent = new Intent();
            intent.setType("image/*");
            intent.setAction(Intent.ACTION_GET_CONTENT);
            startActivityForResult(Intent.createChooser(intent, "Select Image"), PICK_IMAGE_REQUEST);
        });
        cameraButton.setOnClickListener(v ->
        {
            requestCameraPermission();
        });;

        postButton.setOnClickListener(v -> {
            String content = postContentEditText.getText().toString().trim();
            if (content.isEmpty() && selectedImageUri == null) {
                postContentEditText.setError("Post must contain text or image");
                return;
            }

            if (selectedImageUri != null) {
                postButton.setEnabled(false);
                postButton.setText("Uploading...");

                ImageUploader uploader = new ImageUploader();
                uploader.uploadImageFromUri(FeedActivity.this, selectedImageUri, new ImageUploader.ImageUploadListener() {
                    @Override
                    public void onUploadSuccess(String imageUrl) {
                        ArrayList<String> mediaUrls = new ArrayList<>();
                        mediaUrls.add(imageUrl);
                        createNewPost(content, mediaUrls);
                        dialog.dismiss();
                        uploader.cleanup();
                    }

                    @Override
                    public void onUploadFailure(String errorMessage) {
                        Toast.makeText(FeedActivity.this, "Image upload failed: " + errorMessage, Toast.LENGTH_SHORT).show();
                        postButton.setEnabled(true);
                        postButton.setText(R.string.post);
                        uploader.cleanup();
                    }
                });
            } else {
                createNewPost(content, new ArrayList<>());
                dialog.dismiss();
            }
        });

        dialog.show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {
            try {
                selectedImageUri = data.getData();
                Glide.with(this)
                        .load(selectedImageUri)
                        .into(imagePreview);

                if (imagePreview.getParent() instanceof LinearLayout) {
                    ((LinearLayout)imagePreview.getParent()).setVisibility(View.VISIBLE);
                }

                Log.d("FeedActivity", "Gallery image selected: " + selectedImageUri);
            } catch (Exception e) {
                Toast.makeText(this, "Error processing image: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                Log.e("FeedActivity", "Error processing selected image", e);
                selectedImageUri = null;
            }
        } else if (requestCode == CAMERA_REQUEST_CODE && resultCode == RESULT_OK) {
            try {
                selectedImageUri = cameraImageUri;

                Glide.with(this)
                        .load(selectedImageUri)
                        .into(imagePreview);

                if (imagePreview.getParent() instanceof LinearLayout) {
                    ((LinearLayout)imagePreview.getParent()).setVisibility(View.VISIBLE);
                }

                Log.d("FeedActivity", "Camera image captured: " + selectedImageUri);
            } catch (Exception e) {
                Toast.makeText(this, "Error processing camera image: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                Log.e("FeedActivity", "Error processing camera image", e);
                selectedImageUri = null;
            }
        }
    }
    private void createNewPost(String content, ArrayList<String> mediaUrls) {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        Post newPost = new Post();
        newPost.setId(java.util.UUID.randomUUID().toString());
        newPost.setUserId(auth.getUid());
        newPost.setUserName(CurrentUser.getInstance().getCurrentUser().getDisplayName());
        newPost.setUserProfilePicUrl("");
        newPost.setContent(content);
        newPost.setMediaUrls(mediaUrls);
        newPost.setLikesCount(0);
        newPost.setCommentsCount(0);
        newPost.setTimestamp(new Date());
        newPost.setLikedByCurrentUser(false);

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("posts")
                .document(newPost.getId())
                .set(newPost)
                .addOnSuccessListener(aVoid -> {
                    onPostCreateSuccess(newPost);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(FeedActivity.this, "Sorry, unsuccessful post creation please try again." + e.getMessage(), Toast.LENGTH_LONG).show();
                    Log.d("a", "createNewPost: " + e.getMessage());
                });


    }

    private void openCamera() {
        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

        File photoFile = null;
        try {
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String imageFileName = "JPEG_" + timeStamp + "_";
            File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            photoFile = File.createTempFile(imageFileName, ".jpg", storageDir);
        } catch (IOException ex) {
            Toast.makeText(this, "Error creating image file", Toast.LENGTH_SHORT).show();
            return;
        }

        if (photoFile != null) {
            cameraImageUri = Uri.fromFile(photoFile);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                cameraImageUri = androidx.core.content.FileProvider.getUriForFile(
                        this,
                        getApplicationContext().getPackageName() + ".provider",
                        photoFile);
            }

            cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, cameraImageUri);
            startActivityForResult(cameraIntent, CAMERA_REQUEST_CODE);
        }
    }

    private void onPostCreateSuccess(Post newPost) {
        //posts.add(0, newPost);
        //adapter.notifyItemInserted(0);
        //recyclerView.scrollToPosition(0);
        selectedImageUri = null;
        //Toast.makeText(this, "Post created successfully!", Toast.LENGTH_SHORT).show();
    }
    private void onPostDeleteSuccess(Post post) {
        //Toast.makeText(this, "Post deleted successfully!", Toast.LENGTH_SHORT).show();
    }

    protected void dummy() {
        Post post1 = new Post();
        post1.setId("1");
        post1.setUserId("user1_id");
        post1.setUserName("User1");
        post1.setUserProfilePicUrl("https://tse4.mm.bing.net/th/id/OIP.U1DF6lM_O0pj_gN3dBpsCwHaFM?rs=1&pid=ImgDetMain"); // Set appropriate URL or leave empty
        post1.setContent("This is my first post!");
        post1.setMediaUrls(new ArrayList<>());
        post1.setLikesCount(10);
        post1.setCommentsCount(5);
        post1.setTimestamp(new Date());
        post1.setLikedByCurrentUser(false);

        Post post2 = new Post();
        post2.setId("2");
        post2.setUserId("user2_id");
        post2.setUserName("User2");
        post2.setUserProfilePicUrl("");
        post2.setContent("Hello world from social app");
        post2.setMediaUrls(new ArrayList<>());
        post2.setLikesCount(15);
        post2.setCommentsCount(3);
        post2.setTimestamp(new Date());
        post2.setLikedByCurrentUser(false);

        posts.add(post1);
        posts.add(post2);
    }
}