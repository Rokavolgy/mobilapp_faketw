package com.app.fwitter;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.app.fwitter.modal.CurrentUser;
import com.app.fwitter.modal.User;
import com.app.fwitter.task.ImageUploader;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

public class RegisterFinishActivity extends AppCompatActivity {

    private TextInputEditText displayNameEditText;
    private TextInputEditText usernameEditText;
    private TextInputEditText bioEditText;
    private TextInputEditText locationEditText;
    private TextInputEditText dobEditText;
    private ImageView profileImageView;
    private Button completeRegistrationButton;
    private Uri selectedImageUri = null;

    private ActivityResultLauncher<String> imagePickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.finalize_register);
        initializeViews();
        if (savedInstanceState != null) {
            restoreUIState(savedInstanceState);
        }
        setupDatePicker();
        completeRegistrationButton.setOnClickListener(v -> {
            if (validateInputs()) {
                saveUserProfile();
            }
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("displayName", displayNameEditText.getText().toString());
        outState.putString("username", usernameEditText.getText().toString());
        outState.putString("bio", bioEditText.getText().toString());
        outState.putString("location", locationEditText.getText().toString());
        outState.putString("dob", dobEditText.getText().toString());
    }

    private void restoreUIState(Bundle savedInstanceState) {
        String displayName = savedInstanceState.getString("displayName", "");
        String username = savedInstanceState.getString("username", "");
        String bio = savedInstanceState.getString("bio", "");
        String location = savedInstanceState.getString("location", "");
        String dob = savedInstanceState.getString("dob", "");

        displayNameEditText.setText(displayName);
        usernameEditText.setText(username);
        bioEditText.setText(bio);
        locationEditText.setText(location);
        dobEditText.setText(dob);
    }

    @Override
    protected void onResume() {
        super.onResume();
        //ez az animáció annyira laggol hogy hihetetlen, néha meg se jelenik...
        findViewById(R.id.registerTitleText).post(() -> {
            Animation slideIn = AnimationUtils.loadAnimation(this, R.anim.slide_in_top);
            findViewById(R.id.registerTitleText).startAnimation(slideIn);

            Animation subtitleAnim = AnimationUtils.loadAnimation(this, R.anim.slide_in_top);
            subtitleAnim.setStartOffset(100);
            findViewById(R.id.registerSubtitleText).startAnimation(subtitleAnim);
        });
    }


    private void initializeViews() {
        displayNameEditText = findViewById(R.id.displayNameEditText);
        usernameEditText = findViewById(R.id.usernameEditText);
        bioEditText = findViewById(R.id.bioEditText);
        locationEditText = findViewById(R.id.locationEditText);
        dobEditText = findViewById(R.id.dobEditText);
        profileImageView = findViewById(R.id.profileView);
        completeRegistrationButton = findViewById(R.id.completeRegistrationButton);
        setupImagePicker();

        ImageButton editProfilePhotoButton = findViewById(R.id.editProfilePhotoButton);
        editProfilePhotoButton.setOnClickListener(v -> openImagePicker());
    }

    private void setupImagePicker() {
        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        selectedImageUri = uri;
                        profileImageView.setImageURI(uri);
                    }
                }
        );
    }

    private void openImagePicker() {
        imagePickerLauncher.launch("image/*");
    }

    private void setupDatePicker() {
        dobEditText.setOnClickListener(v -> {
            Calendar calendar = Calendar.getInstance();
            int year = calendar.get(Calendar.YEAR) - 18;
            int month = calendar.get(Calendar.MONTH);
            int day = calendar.get(Calendar.DAY_OF_MONTH);

            DatePickerDialog datePickerDialog = new DatePickerDialog(
                    RegisterFinishActivity.this,
                    (view, selectedYear, selectedMonth, selectedDay) -> {
                        String formattedDate = selectedDay + "/" + (selectedMonth + 1) + "/" + selectedYear;
                        dobEditText.setText(formattedDate);
                    },
                    year, month, day);
            datePickerDialog.show();
        });
    }

    private boolean validateInputs() {
        boolean isValid = true;

        String displayName = displayNameEditText.getText().toString().trim();
        if (displayName.isEmpty()) {
            displayNameEditText.setError("Display name cannot be empty");
            isValid = false;
        }

        String username = usernameEditText.getText().toString().trim();
        if (username.isEmpty()) {
            usernameEditText.setError("Username cannot be empty");
            isValid = false;
        } else if (!User.isValidUsername(username)) {
            usernameEditText.setError("Username can only contain letters, numbers, and underscores");
            isValid = false;
        }

        String dob = dobEditText.getText().toString().trim();
        if (dob.isEmpty()) {
            dobEditText.setError("Date of birth is required");
            isValid = false;
        }

        return isValid;
    }


    private void saveUserProfile() {
        String displayName = displayNameEditText.getText().toString().trim();
        String username = usernameEditText.getText().toString().trim();
        String bio = bioEditText.getText().toString().trim();
        String location = locationEditText.getText().toString().trim();
        String dateOfBirth = dobEditText.getText().toString().trim();

        //további ellenörzések
        if (!User.isValidUsername(username)) {
            usernameEditText.setError("Username can only contain letters, numbers, and underscores and must be minimum 4 characters long");
            return;
        }
        if (displayName.length() < 4) {
            displayNameEditText.setError("Display name must be at least 4 characters");
            return;
        }
        if (username.length() < 4) {
            usernameEditText.setError("Username must be at least 4 characters");
            return;
        }
        if (bio.length() > 160) {
            bioEditText.setError("Bio must be less than 160 characters");
            return;
        }
        if (location.length() > 30) {
            locationEditText.setError("Location must be less than 30 characters");
            return;
        }
        if (dateOfBirth.length() > 10) {
            dobEditText.setError("Date of birth must be in dd/mm/yyyy format");
            return;
        }
        Timestamp dobTimestamp;
        try {
            // (format is dd/MM/yyyy) ez így nyomi de most így marad :(
            String[] parts = dateOfBirth.split("/");
            int day = Integer.parseInt(parts[0]);
            int month = Integer.parseInt(parts[1]) - 1;
            int year = Integer.parseInt(parts[2]);

            Calendar calendar = Calendar.getInstance();
            calendar.set(year, month, day, 0, 0, 0);

            dobTimestamp = new Timestamp(calendar.getTime());

            FirebaseAuth mAuth = FirebaseAuth.getInstance();
            String userId = mAuth.getCurrentUser().getUid();
            String email = mAuth.getCurrentUser().getEmail();


            User newuser = new User();
            newuser.setBio(bio);
            newuser.setUsername(username);
            newuser.setDisplayName(displayName);
            newuser.setLocation(location);
            newuser.setDateOfBirth(dobTimestamp);
            newuser.setCreatedAt(Timestamp.now());

            completeRegistrationButton.setEnabled(false);
            completeRegistrationButton.setText("Creating account...");

            if(selectedImageUri != null) {
                uploadProfilePicture(userId,newuser);
            } else {
                saveUserToFirestore(newuser,"");
            }


            //Log.d("RegisterFinishActivity", "Data being sent: " + newuser.toString());


        } catch (Exception e) {
            Toast.makeText(RegisterFinishActivity.this, "Sorry, the date is invalid. Please reenter and make sure format is DD/MM/YYYY" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }

    }
    private void uploadProfilePicture(String userId, User user) {
        completeRegistrationButton.setText("Uploading image...");

        ImageUploader imageUploader = new ImageUploader();
        imageUploader.uploadImageFromUri(this, selectedImageUri, new ImageUploader.ImageUploadListener() {
            @Override
            public void onUploadSuccess(String imageUrl) {
                user.setProfileImageUrl(imageUrl);
                saveUserToFirestore(user,imageUrl);
                imageUploader.cleanup();
            }

            @Override
            public void onUploadFailure(String errorMessage) {
                Toast.makeText(RegisterFinishActivity.this,
                        "Failed to upload profile picture: " + errorMessage,
                        Toast.LENGTH_SHORT).show();
                completeRegistrationButton.setEnabled(true);
                completeRegistrationButton.setText("Complete Registration");
                imageUploader.cleanup();
            }
        });
    }

    private void saveUserToFirestore(User user,String imageUrl) {
        FirebaseAuth mAuth = FirebaseAuth.getInstance();
        String userId = mAuth.getCurrentUser().getUid();

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("userdata")
                .document(userId)
                .set(user)
                .addOnSuccessListener(aVoid -> {
                    fixImage(user,imageUrl);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(RegisterFinishActivity.this,
                            "Registration failed: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                    completeRegistrationButton.setEnabled(true);
                    completeRegistrationButton.setText("Complete Registration");
                });

    }

    private void fixImage(User user, String imageUrl) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        FirebaseAuth mAuth = FirebaseAuth.getInstance();
        String userId = mAuth.getCurrentUser().getUid();
            Map<String, Object> userUpdates = new HashMap<>();
            userUpdates.put("profileImageUrl", imageUrl);
            db.collection("userdata").document(userId)
                    .update(userUpdates)
                    .addOnSuccessListener(aVoid -> {
                        onRegistrationSuccess();
                        Log.d("RegisterFinishActivity", "User profile updated successfully");
                    })
                    .addOnFailureListener(e -> {
                    });

        CurrentUser.getInstance().setCurrentUser(user);
    }

    private void onRegistrationSuccess() {
        //Toast.makeText(this, "Registration completed successfully!", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(RegisterFinishActivity.this, ProfileEditActivity.class);
        startActivity(intent);
        finish();
    }

}