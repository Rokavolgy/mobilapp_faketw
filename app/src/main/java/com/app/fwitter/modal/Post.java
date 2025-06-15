package com.app.fwitter.modal;

import android.util.Log;

import com.app.fwitter.task.ImageUploader;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FieldValue;

import java.util.Date;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class Post {
    private String id;
    private String userId;
    private String userName;
    private String userProfilePicUrl;
    private String content;
    private List<String> mediaUrls;
    private int likesCount;
    private int commentsCount;
    private Date timestamp;
    private boolean isLikedByCurrentUser;

    public Post() {

    }

    public Date getTimestamp() {
        if(timestamp == null) {
            // new posts may not have a timestamp set yet
            Log.e("Post", "Warning: Timestamp is null, returning current time");
            return new Date();
        }
        return timestamp;
    }
}