package com.app.fwitter.task;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Modern image uploader that uses Executor instead of deprecated AsyncTask
 */
public class ImageUploader {
    private static final String TAG = "ImageUploader";
    public static final String UPLOAD_URL = "https://lhojsvnzsgqzalyzmkne.supabase.co/functions/v1/storage-upload";
    public static final String STORAGE_URL = "https://lhojsvnzsgqzalyzmkne.supabase.co/storage/v1/object/public/faktw2/";
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface ImageUploadListener {
        void onUploadSuccess(String imageUrl);
        void onUploadFailure(String errorMessage);
    }

    /**
     * Upload an image file to Supabase storage
     *
     * @param imageFile File to upload
     * @param listener Callback for upload results
     */
    public void uploadImage(File imageFile, ImageUploadListener listener) {
        executor.execute(() -> {
            OkHttpClient client = new OkHttpClient();

            try {

                RequestBody requestBody = new MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("file",
                                imageFile.getName(),
                                RequestBody.create(MediaType.parse("image/*"), imageFile))
                        .build();

                Request request = new Request.Builder()
                        .url(UPLOAD_URL)
                        .post(requestBody)
                        .build();


                Response response = client.newCall(request).execute();

                final String result;
                if (response.isSuccessful() && response.body() != null) {
                    result = response.body().string();
                    mainHandler.post(() -> listener.onUploadSuccess(result));
                } else {
                    String errorMsg = "Server error: " + (response.body() != null ?
                            response.body().string() : response.code());
                    mainHandler.post(() -> listener.onUploadFailure(errorMsg));
                }
            } catch (IOException e) {
                Log.e(TAG, "Error uploading image", e);
                mainHandler.post(() -> listener.onUploadFailure("Network error: " + e.getMessage()));
            }
        });
    }
    /**
     * Upload an image directly from URI
     *
     * @param context Application context
     * @param imageUri URI of the image to upload
     * @param listener Callback for upload results
     */
    public void uploadImageFromUri(Context context, Uri imageUri, ImageUploadListener listener) {
        executor.execute(() -> {
            File tempFile = null;
            try {
                tempFile = createImageFile(context);
                copyImageToFile(context, imageUri, tempFile);

                OkHttpClient client = new OkHttpClient();


                RequestBody requestBody = new MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("file",
                                tempFile.getName(),
                                RequestBody.create(MediaType.parse("image/*"), tempFile))
                        .build();


                Request request = new Request.Builder()
                        .url(UPLOAD_URL)
                        .post(requestBody)
                        .build();

                Response response = client.newCall(request).execute();

                final File finalTempFile = tempFile;
                if (response.isSuccessful() && response.body() != null) {
                    final String result = response.body().string();
                    mainHandler.post(() -> {
                        if (finalTempFile != null && finalTempFile.exists()) {
                            finalTempFile.delete();
                        }
                        listener.onUploadSuccess(result);
                    });
                } else {
                    String errorMsg = "Server error: " + (response.body() != null ?
                            response.body().string() : response.code());
                    mainHandler.post(() -> {
                        if (finalTempFile != null && finalTempFile.exists()) {
                            finalTempFile.delete();
                        }
                        listener.onUploadFailure(errorMsg);
                    });
                }
            } catch (IOException e) {
                Log.e(TAG, "Error uploading image", e);
                if (tempFile != null && tempFile.exists()) {
                    tempFile.delete();
                }
                mainHandler.post(() -> listener.onUploadFailure("Network error: " + e.getMessage()));
            }
        });
    }

    private File createImageFile(Context context) throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        return File.createTempFile(imageFileName, ".jpg", storageDir);
    }

    private void copyImageToFile(Context context, Uri uri, File destination) throws IOException {
        try (InputStream inputStream = context.getContentResolver().openInputStream(uri)) {
            if (inputStream == null) {
                throw new IOException("Failed to open input stream");
            }


            long fileSize = getFileSize(context, uri);
            Log.d(TAG, "Original image size: " + fileSize + " bytes");


            if (fileSize > 524288) {
                compressAndSaveImage(context, uri, destination);
            } else {

                try (FileOutputStream outputStream = new FileOutputStream(destination)) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                    }
                    outputStream.flush();
                }
            }
        }
    }
    private long getFileSize(Context context, Uri uri) {
        try {
            return context.getContentResolver().openFileDescriptor(uri, "r").getStatSize();
        } catch (Exception e) {
            Log.e(TAG, "Error getting file size", e);
            return 0;
        }
    }
    private void compressAndSaveImage(Context context, Uri uri, File destination) throws IOException {
        try {

            android.graphics.Bitmap bitmap = android.provider.MediaStore.Images.Media.getBitmap(
                    context.getContentResolver(), uri);


            int quality = 90;
            boolean compressed = false;

            while (quality > 30 && !compressed) {
                try (FileOutputStream outputStream = new FileOutputStream(destination)) {

                    bitmap.compress(Bitmap.CompressFormat.WEBP, quality, outputStream);
                    outputStream.flush();


                    if (destination.length() <= 512000) {
                        compressed = true;
                        Log.d(TAG, "Compressed image size: " + destination.length() +
                                " bytes with quality: " + quality);
                    } else {

                        quality -= 10;
                        Log.d(TAG, "Reducing quality to " + quality);
                    }
                }
            }

            if (!compressed) {
                int width = bitmap.getWidth();
                int height = bitmap.getHeight();
                float scale = 0.8f;

                while (!compressed && scale > 0.3f) {
                    int newWidth = (int)(width * scale);
                    int newHeight = (int)(height * scale);

                    android.graphics.Bitmap resizedBitmap = android.graphics.Bitmap.createScaledBitmap(
                            bitmap, newWidth, newHeight, true);

                    try (FileOutputStream outputStream = new FileOutputStream(destination)) {
                        resizedBitmap.compress(Bitmap.CompressFormat.WEBP, 80, outputStream);
                        outputStream.flush();

                        if (destination.length() <= 524288) {
                            compressed = true;
                            Log.d(TAG, "Resized and compressed image: " +
                                    newWidth + "x" + newHeight + ", size: " + destination.length() + " bytes");
                        } else {
                            scale -= 0.1f;
                            Log.d(TAG, "Reducing scale to " + scale);
                            if (resizedBitmap != bitmap) {
                                resizedBitmap.recycle();
                            }
                        }
                    }
                }
            }

            bitmap.recycle();

        } catch (Exception e) {
            Log.e(TAG, "Error compressing image", e);
            throw new IOException("Failed to compress image: " + e.getMessage());
        }
    }

    /**
     * Shutdown the executor when no longer needed
     */
    public void cleanup() {
        executor.shutdown();
    }
}