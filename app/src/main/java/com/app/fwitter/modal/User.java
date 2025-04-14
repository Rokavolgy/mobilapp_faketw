package com.app.fwitter.modal;

import lombok.Getter;
import lombok.Setter;
import com.google.firebase.Timestamp;


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

}