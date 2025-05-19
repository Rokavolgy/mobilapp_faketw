package com.app.fwitter;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.app.fwitter.modal.CurrentUser;
import com.app.fwitter.modal.User;
import com.google.firebase.BuildConfig;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        //FirebaseFirestore.getInstance().useEmulator( "10.0.2.2", 8080);
        //FirebaseAuth.getInstance().useEmulator("10.0.2.2", 9099);
        super.onCreate(savedInstanceState);
        FirebaseApp.initializeApp(this);
        FirebaseAuth mAuth = FirebaseAuth.getInstance();
        if(mAuth.getCurrentUser() != null)
        {
            checkUserProfileExists(mAuth.getCurrentUser().getUid());
        }
        Log.i("nem", "oops");
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    private void checkUserProfileExists(String userId) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("userdata").document(userId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists() && documentSnapshot.contains("displayName")) {
                        Intent intent = new Intent(this, FeedActivity.class);
                        User user = documentSnapshot.toObject(User.class);
                        CurrentUser.getInstance().setCurrentUser(user);
                        com.app.fwitter.notification.PostNotificationManager.initialize(this.getApplication());
                        if(user == null)
                        {
                            Log.d("oh no","ohn o");
                            return;
                        }
                        intent.putExtra("SOME_KEY", 34);
                        startActivity(intent);
                        finish();
                    } else {
                        Intent intent = new Intent(this, RegisterFinishActivity.class);
                        intent.putExtra("SOME_KEY", 34);
                        startActivity(intent);
                        finish();
                    }
                })
                .addOnFailureListener(e -> {
                    //Log.e("MainActivity", "Error checking user profile existence", e);
                    //Toast.makeText(this, "Error checking user profile existence", Toast.LENGTH_SHORT).show();
                });
    }

    @Override
    protected void onStart() {
        super.onStart();

        FirebaseAuth mAuth = FirebaseAuth.getInstance();


        if (mAuth.getCurrentUser() != null) {
            mAuth.signOut();
            if(CurrentUser.getInstance().getCurrentUser() != null) {
                Toast.makeText(this, "Logged out of your account. Goodbye " + CurrentUser.getInstance().getCurrentUser().getDisplayName() + "!", Toast.LENGTH_SHORT).show();
            }
            CurrentUser.getInstance().clearCurrentUser();

            Log.i("MainActivity", "User logged out on return to MainActivity");
        }
    }

    public void startLoginActivity(View view) {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.putExtra("SOME_KEY", 34);
        startActivity(intent);
    }

    public void startRegisterActivity(View view) {
        Intent intent = new Intent(this, RegisterActivity.class);
        intent.putExtra("SOME_KEY", 34);
        startActivity(intent);
    }
}
