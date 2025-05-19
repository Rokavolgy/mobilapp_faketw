package com.app.fwitter.modal;

import lombok.Getter;
import lombok.Setter;

import com.app.fwitter.task.ImageUploader;
import com.google.firebase.Timestamp;

import java.util.regex.Pattern;


@Getter
@Setter
public class User {
    private Timestamp createdAt;
    private String displayName = "";
    private String username = "";
    private String bio = "";
    private String location = "";
    private String website = "";
    private Timestamp dateOfBirth;
    private String profileImageUrl = "";
    private String coverImageUrl = "";

    public User() {
    }

    public String getProfileImageUrl() {
        if (profileImageUrl == null || profileImageUrl.isEmpty()) {
            return "";
        }
        return ImageUploader.STORAGE_URL + profileImageUrl;
    }

    public String getCoverImageUrl() {
        if (coverImageUrl == null || coverImageUrl.isEmpty()) {
            return "";
        }
        return ImageUploader.STORAGE_URL + coverImageUrl;
    }

    public String getBio() {
        if (bio == null || bio.isEmpty()) {
            return "User has not provided a bio.";
        }
        return bio;
    }

    public static boolean isValidUsername(String username) {
        if(username == null || username.isEmpty()) {
            return false;
        }
        if(username.length() < 4)
        {
            return false;
        }
        Pattern pattern = Pattern.compile("^[a-zA-Z0-9_]+$");
        return pattern.matcher(username).matches();
    }
}