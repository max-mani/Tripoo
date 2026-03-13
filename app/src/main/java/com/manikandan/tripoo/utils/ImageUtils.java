package com.manikandan.tripoo.utils;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.provider.MediaStore;
import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class ImageUtils {
    
    // Keep under Firestore 1MB doc limit: 256px at 70% gives ~20-40KB base64
    private static final int MAX_IMAGE_SIZE = 256;
    private static final int COMPRESSION_QUALITY = 70;
    
    /**
     * Crop image to 1:1 ratio and convert to base64 string
     * @param context Context for accessing content resolver
     * @param imageUri Uri of the image
     * @return Base64 encoded string of the cropped image
     */
    public static String cropAndConvertToBase64(Context context, Uri imageUri) throws IOException {
        Bitmap bitmap = loadBitmapFromUri(context, imageUri);
        Bitmap croppedBitmap = cropToSquare(bitmap);
        Bitmap resizedBitmap = resizeBitmap(croppedBitmap, MAX_IMAGE_SIZE);
        return bitmapToBase64(resizedBitmap);
    }
    
    /**
     * Load bitmap from URI
     */
    private static Bitmap loadBitmapFromUri(Context context, Uri uri) throws IOException {
        InputStream inputStream = context.getContentResolver().openInputStream(uri);
        Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
        if (inputStream != null) {
            inputStream.close();
        }
        
        // Handle image orientation
        bitmap = rotateImageIfRequired(context, bitmap, uri);
        return bitmap;
    }
    
    /**
     * Crop bitmap to square (1:1 ratio)
     */
    private static Bitmap cropToSquare(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int size = Math.min(width, height);
        
        int x = (width - size) / 2;
        int y = (height - size) / 2;
        
        return Bitmap.createBitmap(bitmap, x, y, size, size);
    }
    
    /**
     * Resize bitmap to max size while maintaining aspect ratio
     */
    private static Bitmap resizeBitmap(Bitmap bitmap, int maxSize) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        
        if (width <= maxSize && height <= maxSize) {
            return bitmap;
        }
        
        float scale = Math.min((float) maxSize / width, (float) maxSize / height);
        int newWidth = Math.round(width * scale);
        int newHeight = Math.round(height * scale);
        
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
    }
    
    /**
     * Convert bitmap to base64 string
     */
    private static String bitmapToBase64(Bitmap bitmap) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, COMPRESSION_QUALITY, baos);
        byte[] imageBytes = baos.toByteArray();
        return android.util.Base64.encodeToString(imageBytes, android.util.Base64.DEFAULT);
    }
    
    /**
     * Handle image rotation based on EXIF data
     */
    private static Bitmap rotateImageIfRequired(Context context, Bitmap bitmap, Uri uri) {
        try {
            InputStream inputStream = context.getContentResolver().openInputStream(uri);
            if (inputStream == null) {
                return bitmap;
            }
            
            ExifInterface exif = new ExifInterface(inputStream);
            inputStream.close();
            
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            
            if (orientation == ExifInterface.ORIENTATION_NORMAL) {
                return bitmap;
            }
            
            Matrix matrix = new Matrix();
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    matrix.postRotate(90);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    matrix.postRotate(180);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    matrix.postRotate(270);
                    break;
                default:
                    return bitmap;
            }
            
            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        } catch (Exception e) {
            // If EXIF reading fails, return original bitmap
            return bitmap;
        }
    }
    
    /**
     * Convert base64 string to bitmap.
     * Strips whitespace/newlines (e.g. from Firestore) before decoding.
     * Tries DEFAULT and NO_WRAP decoding for compatibility with different encodings.
     */
    public static Bitmap base64ToBitmap(String base64String) {
        if (base64String == null || base64String.isEmpty()) {
            return null;
        }
        
        try {
            // Remove data URI prefix if present
            if (base64String.startsWith("data:image")) {
                int comma = base64String.indexOf(',');
                base64String = comma >= 0 ? base64String.substring(comma + 1) : base64String;
            }
            // Strip whitespace and newlines (Firestore or Base64.encodeToString may add them)
            base64String = base64String.trim().replaceAll("\\s+", "");
            
            byte[] decodedBytes = android.util.Base64.decode(base64String, android.util.Base64.DEFAULT);
            Bitmap bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
            if (bitmap != null) {
                return bitmap;
            }
            // Fallback: try NO_WRAP (e.g. if string was encoded with different flags)
            decodedBytes = android.util.Base64.decode(base64String, android.util.Base64.NO_WRAP);
            return BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Check if string is base64 encoded image (not a URL).
     * Use this to decide whether to decode; never pass base64 string to Glide.load().
     */
    public static boolean isBase64Image(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        if (str.startsWith("http://") || str.startsWith("https://")) {
            return false;
        }
        if (str.startsWith("data:image")) {
            return true;
        }
        // JPEG base64 starts with /9j/, PNG with iVBOR
        if (str.trim().startsWith("/9j/") || str.trim().startsWith("iVBOR")) {
            return true;
        }
        // Long non-URL string is likely base64 (may contain newlines)
        return str.length() > 100;
    }
}
