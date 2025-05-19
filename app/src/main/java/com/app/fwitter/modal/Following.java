package com.app.fwitter.modal;

public class Following {

    private String userId;
    private String followingUserId;

    public Following() {
    }

    public Following(String userId, String followingUserId) {
        this.userId = userId;
        this.followingUserId = followingUserId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getFollowingUserId() {
        return followingUserId;
    }

    public void setFollowingUserId(String followingUserId) {
        this.followingUserId = followingUserId;
    }
}
