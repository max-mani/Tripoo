package com.example.tripoo.utils;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import java.util.Random;

public class TripCodeGenerator {
    private static final String PREFIX = "TRP-";
    private static final int CODE_LENGTH = 3;
    private static final Random random = new Random();

    public interface CodeGenerationCallback {
        void onCodeGenerated(String tripCode);
        void onError(String error);
    }

    public static void generateUniqueTripCode(FirebaseFirestore firestore, CodeGenerationCallback callback) {
        generateCode(firestore, callback, 0);
    }

    private static void generateCode(FirebaseFirestore firestore, CodeGenerationCallback callback, int attempt) {
        if (attempt > 10) {
            callback.onError("Failed to generate unique trip code after multiple attempts");
            return;
        }

        String code = generateRandomCode();
        
        // Check if code exists in Firestore
        firestore.collection("trips")
                .whereEqualTo("tripCode", code)
                .whereEqualTo("isActive", true)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        QuerySnapshot snapshot = task.getResult();
                        if (snapshot != null && snapshot.isEmpty()) {
                            // Code is unique
                            callback.onCodeGenerated(code);
                        } else {
                            // Code exists, try again
                            generateCode(firestore, callback, attempt + 1);
                        }
                    } else {
                        callback.onError("Error checking trip code: " + 
                                (task.getException() != null ? task.getException().getMessage() : "Unknown error"));
                    }
                });
    }

    private static String generateRandomCode() {
        int number = random.nextInt(900) + 100; // Generate 3-digit number (100-999)
        return PREFIX + number;
    }
}
