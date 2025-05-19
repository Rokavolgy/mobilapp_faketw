package com.app.fwitter;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.app.fwitter.modal.Post;
import com.app.fwitter.task.ImageUploader;
import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class PostAdapter extends RecyclerView.Adapter<PostAdapter.PostViewHolder> {
    private List<Post> posts;
    private PostInteractionListener listener;

    public interface PostInteractionListener {
        void onLikeClicked(Post post, int position);
        void onProfileClicked(Post post);
        void onDeleteClicked(Post post);

    }

    public PostAdapter(List<Post> posts) {
        this.posts = posts;
    }

    public void setPostInteractionListener(PostInteractionListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public PostViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.post_card, parent, false);
        return new PostViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PostViewHolder holder, int position) {
        Post post = posts.get(position);
        holder.bind(post);
    }

    @Override
    public int getItemCount() {
        return posts.size();
    }

    class PostViewHolder extends RecyclerView.ViewHolder {
        TextView usernameTextView;
        TextView postContentTextView;
        ImageView profileImageView;
        TextView likeCountTextView;
        TextView timestampTextView;
        ImageButton likeButton;
        ImageButton deleteButton;

        public PostViewHolder(@NonNull View itemView) {
            super(itemView);

            usernameTextView = itemView.findViewById(R.id.usernameTextView);
            postContentTextView = itemView.findViewById(R.id.postContentTextView);
            profileImageView = itemView.findViewById(R.id.profileView);
            likeCountTextView = itemView.findViewById(R.id.likeCountTextView);
            timestampTextView = itemView.findViewById(R.id.timestampTextView);
            likeButton = itemView.findViewById(R.id.likeButton);
            deleteButton = itemView.findViewById(R.id.deleteButton);
        }

        void bind(final Post post) {
            usernameTextView.setText(post.getUserName());
            postContentTextView.setText(post.getContent());
            likeCountTextView.setText(String.valueOf(post.getLikesCount()));

            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault());
            timestampTextView.setText(sdf.format(post.getTimestamp()));

            if (post.getUserProfilePicUrl() != null && !post.getUserProfilePicUrl().isEmpty()) {
                Glide.with(itemView.getContext())
                        .load(post.getUserProfilePicUrl())
                        .placeholder(R.mipmap.default_profile_image)
                        .into(profileImageView);
            } else {
                profileImageView.setImageResource(R.mipmap.default_profile_image);
            }

            if (post.getMediaUrls() != null && !post.getMediaUrls().isEmpty()) {
                String fullImageUrl = ImageUploader.STORAGE_URL + post.getMediaUrls().get(0);
                Glide.with(itemView.getContext())
                        .load(fullImageUrl)
                        .into((ImageView) itemView.findViewById(R.id.postImageView));
                (itemView.findViewById(R.id.postImageView)).setVisibility(View.VISIBLE);
            } else {

                (itemView.findViewById(R.id.postImageView)).setVisibility(View.GONE);
            }

            updateLikeButtonState(post);

            setupClickListeners(post, getAdapterPosition());

            if(post.getUserId() == null || !post.getUserId().equals(FirebaseAuth.getInstance().getCurrentUser().getUid())) {
                deleteButton.setVisibility(View.GONE);
            } else {
                deleteButton.setVisibility(View.VISIBLE);
            }
        }

        private void updateLikeButtonState(Post post) {
            if (post.isLikedByCurrentUser()) {
                likeButton.setImageResource(R.drawable.heart_filled);
            } else {
                likeButton.setImageResource(R.drawable.heart);
            }
        }

        private void setupClickListeners(final Post post, final int position) {
            likeButton.setOnClickListener(v -> {
                if (listener != null) {
                    var heartAnimation = AnimationUtils.loadAnimation(this.deleteButton.getContext(),R.anim.heart_animation);
                    likeButton.startAnimation(heartAnimation);
                    listener.onLikeClicked(post, position);
                }
            });

            profileImageView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onProfileClicked(post);
                }
            });



            deleteButton.setOnClickListener(v -> {
                if (listener != null) {
                    new androidx.appcompat.app.AlertDialog.Builder(itemView.getContext())
                            .setTitle("Delete Post")
                            .setMessage("Are you sure you want to delete this post?")
                            .setPositiveButton("Delete", (dialog, which) -> {
                                listener.onDeleteClicked(post);
                            })
                            .setNegativeButton("Cancel", (dialog, which) -> {
                                dialog.dismiss();
                            })
                            .show();
                }
            });
        }
    }
}