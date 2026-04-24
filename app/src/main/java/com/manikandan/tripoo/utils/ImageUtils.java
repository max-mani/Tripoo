package com.manikandan.tripoo.utils;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class ImageUtils {

    /** Stored image is always this square edge (px); JPEG stays well under Firestore 1MB. */
    private static final int MAX_IMAGE_SIZE = 384;
    private static final int COMPRESSION_QUALITY = 82;
    /** Max dimension while decoding before crop (reduces OOM; keeps detail for 1:1 center crop). */
    private static final int MAX_DECODE_DIMENSION = 2048;

    /**
     * Center-crops to 1:1, scales to a square, encodes as JPEG/base64 for Firestore.
     */
    public static String cropAndConvertToBase64(Context context, Uri imageUri) throws IOException {
        Bitmap bitmap = loadBitmapFromUri(context, imageUri);
        Bitmap croppedBitmap = cropToCenterSquare(bitmap);
        if (croppedBitmap != bitmap) {
            bitmap.recycle();
        }
        Bitmap resizedBitmap = resizeBitmapToSquare(croppedBitmap, MAX_IMAGE_SIZE);
        if (resizedBitmap != croppedBitmap) {
            croppedBitmap.recycle();
        }
        Bitmap finalSquare = ensureSquare(resizedBitmap);
        if (finalSquare != resizedBitmap) {
            resizedBitmap.recycle();
        }
        String encoded = bitmapToBase64(finalSquare);
        finalSquare.recycle();
        return encoded;
    }

    /**
     * Center-crops to the largest inscribed square (1:1). If already square, returns the same instance.
     */
    public static Bitmap cropToCenterSquare(Bitmap bitmap) {
        if (bitmap == null) {
            return null;
        }
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        if (width <= 0 || height <= 0) {
            return bitmap;
        }
        if (width == height) {
            return bitmap;
        }
        int size = Math.min(width, height);
        int x = (width - size) / 2;
        int y = (height - size) / 2;
        return Bitmap.createBitmap(bitmap, x, y, size, size);
    }

    private static Bitmap loadBitmapFromUri(Context context, Uri uri) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        InputStream boundsStream = context.getContentResolver().openInputStream(uri);
        if (boundsStream == null) {
            throw new IOException("Could not open image");
        }
        BitmapFactory.decodeStream(boundsStream, null, bounds);
        boundsStream.close();

        int maxDim = Math.max(bounds.outWidth, bounds.outHeight);
        int inSampleSize = 1;
        while (maxDim / (inSampleSize * 2) > MAX_DECODE_DIMENSION) {
            inSampleSize *= 2;
        }

        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = inSampleSize;

        InputStream inputStream = context.getContentResolver().openInputStream(uri);
        if (inputStream == null) {
            throw new IOException("Could not open image");
        }
        Bitmap bitmap = BitmapFactory.decodeStream(inputStream, null, opts);
        inputStream.close();
        if (bitmap == null) {
            throw new IOException("Could not decode image");
        }

        return rotateImageIfRequired(context, bitmap, uri);
    }

    private static Bitmap resizeBitmapToSquare(Bitmap bitmap, int maxEdgePx) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        if (width <= maxEdgePx && height <= maxEdgePx) {
            return bitmap;
        }
        return Bitmap.createScaledBitmap(bitmap, maxEdgePx, maxEdgePx, true);
    }

    private static Bitmap ensureSquare(Bitmap bitmap) {
        if (bitmap == null) {
            return null;
        }
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        if (w == h) {
            return bitmap;
        }
        return cropToCenterSquare(bitmap);
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

            Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            bitmap.recycle();
            return rotated;
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
