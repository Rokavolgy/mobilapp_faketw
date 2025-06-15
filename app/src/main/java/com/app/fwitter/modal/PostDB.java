package com.app.fwitter.modal;

import com.google.firebase.firestore.FieldValue;

import java.util.Date;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class PostDB {
    private String id;
    private String userId;
    private String userName;
    private String content;
    private List<String> mediaUrls;
    private int likesCount;
    private int commentsCount;
    private FieldValue timestamp;

    public PostDB() {

    }


}
