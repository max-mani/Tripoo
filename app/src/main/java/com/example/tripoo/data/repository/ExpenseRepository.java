package com.example.tripoo.data.repository;

import com.example.tripoo.data.model.Expense;
import com.example.tripoo.utils.FirebaseHelper;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

public class ExpenseRepository {
    private FirebaseFirestore firestore;
    private static final String COLLECTION_TRIPS = "trips";
    private static final String COLLECTION_EXPENSES = "expenses";

    public ExpenseRepository() {
        firestore = FirebaseHelper.getInstance().getFirestore();
    }

    public Task<DocumentReference> addExpense(String tripId, Expense expense) {
        return firestore.collection(COLLECTION_TRIPS)
                .document(tripId)
                .collection(COLLECTION_EXPENSES)
                .add(expense.toMap());
    }

    public Task<Void> updateExpense(String tripId, String expenseId, Expense expense) {
        return firestore.collection(COLLECTION_TRIPS)
                .document(tripId)
                .collection(COLLECTION_EXPENSES)
                .document(expenseId)
                .update(expense.toMap());
    }

    public Task<Void> deleteExpense(String tripId, String expenseId) {
        return firestore.collection(COLLECTION_TRIPS)
                .document(tripId)
                .collection(COLLECTION_EXPENSES)
                .document(expenseId)
                .delete();
    }

    public Task<QuerySnapshot> getExpenses(String tripId) {
        return firestore.collection(COLLECTION_TRIPS)
                .document(tripId)
                .collection(COLLECTION_EXPENSES)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get();
    }

    public ListenerRegistration listenToExpenses(String tripId, com.google.firebase.firestore.EventListener<QuerySnapshot> listener) {
        return firestore.collection(COLLECTION_TRIPS)
                .document(tripId)
                .collection(COLLECTION_EXPENSES)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener(listener);
    }
}
