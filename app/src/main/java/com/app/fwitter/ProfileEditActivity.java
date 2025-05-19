package com.app.fwitter;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.app.fwitter.modal.User;
import com.app.fwitter.task.ImageUploader;
import com.bumptech.glide.Glide;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class ProfileEditActivity extends AppCompatActivity {

    private ImageButton backButton, editCoverPhotoButton, editProfilePhotoButton;
    private Button saveProfileButton;
    private ImageView coverPhotoImageView, profileImageView;
    private TextInputEditText displayNameEditText, usernameEditText, bioEditText,
            locationEditText, websiteEditText, dobEditText;

    private FirebaseFirestore mFirestore;
    private StorageReference mStorage;
    private FirebaseUser currentUser;

    private Uri profileImageUri;
    private Uri coverImageUri;
    private boolean isProfileImageChanged = false;
    private boolean isCoverImageChanged = false;

    private final Calendar calendar = Calendar.getInstance();
    private View deleteButton;
    private final ActivityResultLauncher<Intent> profileImagePicker =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    profileImageUri = result.getData().getData();
                    isProfileImageChanged = true;
                    Glide.with(this).load(profileImageUri).into(profileImageView);
                }
            });

    private final ActivityResultLauncher<Intent> coverImagePicker =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    coverImageUri = result.getData().getData();
                    isCoverImageChanged = true;
                    Glide.with(this).load(coverImageUri).into(coverPhotoImageView);
                }
            });


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.profile_edit_screen);

        FirebaseAuth mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();
        mFirestore = FirebaseFirestore.getInstance();
        mStorage = FirebaseStorage.getInstance().getReference();

        if (currentUser == null) {
            //ha nincs belépve
            Intent intent = new Intent(ProfileEditActivity.this, LoginActivity.class);
            intent.putExtra("SOME_KEY", 34);
            startActivity(intent);
            finish();
            return;
        }

        initializeViews();

        setupClickListeners();

        loadUserData();
    }

    private void initializeViews() {
        backButton = findViewById(R.id.backButton);
        saveProfileButton = findViewById(R.id.saveProfileButton);
        deleteButton = findViewById(R.id.delete_button);
        coverPhotoImageView = findViewById(R.id.coverPhotoImageView);
        profileImageView = findViewById(R.id.profileView);
        editCoverPhotoButton = findViewById(R.id.editCoverPhotoButton);
        editProfilePhotoButton = findViewById(R.id.editProfilePhotoButton);

        displayNameEditText = findViewById(R.id.displayNameEditText);
        usernameEditText = findViewById(R.id.usernameEditText);
        bioEditText = findViewById(R.id.bioEditText);
        locationEditText = findViewById(R.id.locationEditText);
        websiteEditText = findViewById(R.id.websiteEditText);
        dobEditText = findViewById(R.id.dobEditText);
    }

    private void setupClickListeners() {
        backButton.setOnClickListener(v -> goToFeed());
        deleteButton.setOnClickListener(v -> {
            confirmAndDeleteProfile();
        });

        saveProfileButton.setOnClickListener(v -> saveUserProfile());

        editProfilePhotoButton.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            profileImagePicker.launch(intent);
        });

        editCoverPhotoButton.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            coverImagePicker.launch(intent);
        });

        dobEditText.setOnClickListener(v -> showDatePickerDialog());
    }

    private void goToFeed()
    {
        Intent intent = new Intent(this, FeedActivity.class);
        intent.putExtra("SOME_KEY", 34);
        startActivity(intent);
        finish();
    }

    private void confirmAndDeleteProfile() {
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle("Delete Profile");
        builder.setMessage("Are you sure you want to delete your profile? This will permanently remove your account and all your posts. This action cannot be undone.");

        builder.setPositiveButton("Delete", (dialog, which) -> {
            deleteUserProfile();
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> {
            dialog.dismiss();
        });

        androidx.appcompat.app.AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void deleteUserProfile() {
        Toast.makeText(this, "Deleting profile...", Toast.LENGTH_SHORT).show();

        String userId = currentUser.getUid();

        mFirestore.collection("posts")
                .whereEqualTo("userId", userId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        com.google.firebase.firestore.WriteBatch batch = mFirestore.batch();
                        for (DocumentSnapshot document : queryDocumentSnapshots.getDocuments()) {
                            batch.delete(document.getReference());
                        }

                        batch.commit()
                                .addOnSuccessListener(aVoid -> deleteUserData(userId))
                                .addOnFailureListener(e -> {
                                    Toast.makeText(ProfileEditActivity.this,
                                            "Failed to delete posts: " + e.getMessage(),
                                            Toast.LENGTH_LONG).show();
                                });
                    } else {
                        deleteUserData(userId);
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(ProfileEditActivity.this,
                            "Failed to retrieve posts: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
    }

    private void deleteUserData(String userId) {
        mFirestore.collection("userdata").document(userId)
                .delete()
                .addOnSuccessListener(aVoid -> {

                    currentUser.delete()
                            .addOnSuccessListener(aVoid1 -> {
                                Toast.makeText(ProfileEditActivity.this,
                                        "Profile deleted successfully",
                                        Toast.LENGTH_SHORT).show();

                                FirebaseAuth.getInstance().signOut();
                                Intent intent = new Intent(this, MainActivity.class);
                                intent.putExtra("SOME_KEY", 34);
                                startActivity(intent);
                                finishAffinity();
                            })
                            .addOnFailureListener(e -> {
                                Toast.makeText(ProfileEditActivity.this,
                                        "Failed to delete authentication account: " + e.getMessage(),
                                        Toast.LENGTH_LONG).show();
                            });
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(ProfileEditActivity.this,
                            "Failed to delete user data: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
    }

    private void showDatePickerDialog() {

        DatePickerDialog datePickerDialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    calendar.set(Calendar.YEAR, year);
                    calendar.set(Calendar.MONTH, month);
                    calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                    updateDateInView();
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
        );
        datePickerDialog.show();
    }

    private void updateDateInView() {
        String dateFormat = "dd/mm/yyyy";
        SimpleDateFormat sdf = new SimpleDateFormat(dateFormat, Locale.US);
        dobEditText.setText(sdf.format(calendar.getTime()));
    }

    private void loadUserData() {
        String userId = currentUser.getUid();
        mFirestore.collection("userdata").document(userId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        populateUserData(documentSnapshot);
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(ProfileEditActivity.this,
                            "Failed to load user data: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
    }


    private void populateUserData(DocumentSnapshot snapshot) {
        User user = snapshot.toObject(User.class);
        if (user != null) {
            displayNameEditText.setText(user.getDisplayName());
            usernameEditText.setText(user.getUsername());
            bioEditText.setText(user.getBio());
            locationEditText.setText(user.getLocation());
            websiteEditText.setText(user.getWebsite());

            DateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            String formattedDate = dateFormat.format(user.getDateOfBirth().toDate());
            dobEditText.setText(formattedDate);

            if (user.getProfileImageUrl() != null && !user.getProfileImageUrl().isEmpty()) {
                Glide.with(this).load(user.getProfileImageUrl()).into(profileImageView);
            }

            if (user.getCoverImageUrl() != null && !user.getCoverImageUrl().isEmpty()) {
                Glide.with(this).load(user.getCoverImageUrl()).into(coverPhotoImageView);
            }
        }
    }

    private void saveUserProfile() {
        saveProfileButton.setEnabled(false);

        final String userId = currentUser.getUid();
        final String displayName = displayNameEditText.getText().toString().trim();
        final String username = usernameEditText.getText().toString().trim();
        final String bio = bioEditText.getText().toString().trim();
        final String location = locationEditText.getText().toString().trim();
        final String website = websiteEditText.getText().toString().trim();
        final String dob = dobEditText.getText().toString().trim();

        if(!User.isValidUsername(username))
        {
            usernameEditText.setError("Username must be at least 4 characters and can only contain letters, numbers, and underscores");
            saveProfileButton.setEnabled(true);
            return;
        }
        if (displayName.isEmpty()) {
            displayNameEditText.setError("Display name is required");
            saveProfileButton.setEnabled(true);
            return;
        }

        if (username.isEmpty()) {
            usernameEditText.setError("Username is required");
            saveProfileButton.setEnabled(true);
            return;
        }

        ImageUploader imageUploader = new ImageUploader();

        final boolean[] profileUploaded = {!isProfileImageChanged};
        final boolean[] coverUploaded = {!isCoverImageChanged};
        final String[] profileImageUrl = {null};
        final String[] coverImageUrl = {null};

        Runnable checkAndSaveProfile = () -> {
            if (profileUploaded[0] && coverUploaded[0]) {
                saveProfileData(userId, displayName, username, bio, location, website, dob,
                        profileImageUrl[0], coverImageUrl[0]);
                imageUploader.cleanup();
            }
        };

        if (isProfileImageChanged && profileImageUri != null) {
            imageUploader.uploadImageFromUri(this, profileImageUri, new ImageUploader.ImageUploadListener() {
                @Override
                public void onUploadSuccess(String imageUrl) {
                    profileImageUrl[0] = imageUrl;
                    profileUploaded[0] = true;
                    checkAndSaveProfile.run();
                }

                @Override
                public void onUploadFailure(String errorMessage) {
                    Toast.makeText(ProfileEditActivity.this,
                            "Failed to upload profile image: " + errorMessage, Toast.LENGTH_SHORT).show();
                    saveProfileButton.setEnabled(true);
                    imageUploader.cleanup();
                }
            });
        }

        if (isCoverImageChanged && coverImageUri != null) {
            imageUploader.uploadImageFromUri(this, coverImageUri, new ImageUploader.ImageUploadListener() {
                @Override
                public void onUploadSuccess(String imageUrl) {
                    coverImageUrl[0] = imageUrl;
                    coverUploaded[0] = true;
                    checkAndSaveProfile.run();
                }

                @Override
                public void onUploadFailure(String errorMessage) {
                    Toast.makeText(ProfileEditActivity.this,
                            "Failed to upload cover image: " + errorMessage, Toast.LENGTH_SHORT).show();
                    saveProfileButton.setEnabled(true);
                    imageUploader.cleanup();
                }
            });
        }

        checkAndSaveProfile.run();
    }

    private void saveProfileData(String userId, String displayName, String username,
                                 String bio, String location, String website, String dob,
                                 String profileImageUrl, String coverImageUrl) {

        Map<String, Object> userUpdates = new HashMap<>();
        userUpdates.put("displayName", displayName);
        // userUpdates.put("username", username);
        userUpdates.put("bio", bio);
        userUpdates.put("location", location);
        userUpdates.put("website", website);

        try {
            String[] parts = dob.split("/");
            int day = Integer.parseInt(parts[0]);
            int month = Integer.parseInt(parts[1]) - 1;
            int year = Integer.parseInt(parts[2]);
            Calendar calendar = Calendar.getInstance();
            calendar.set(year, month, day, 0, 0, 0);

            Timestamp dobTimestamp = new Timestamp(calendar.getTime());
            userUpdates.put("dateOfBirth", dobTimestamp);
        } catch (Exception e) {
            Toast.makeText(ProfileEditActivity.this,
                    "Invalid date format. Please use dd/mm/yyyy", Toast.LENGTH_SHORT).show();
            saveProfileButton.setEnabled(true);
            return;
        }

        if (profileImageUrl != null) {
            userUpdates.put("profileImageUrl", profileImageUrl);
        }

        if (coverImageUrl != null) {
            userUpdates.put("coverImageUrl", coverImageUrl);
        }

        mFirestore.collection("userdata").document(userId)
                .update(userUpdates)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(ProfileEditActivity.this,
                            "Profile updated successfully", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(ProfileEditActivity.this,
                            "Failed to update profile: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    saveProfileButton.setEnabled(true);
                });
    }


}