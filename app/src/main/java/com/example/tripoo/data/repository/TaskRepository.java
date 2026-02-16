package com.example.tripoo.data.repository;

import com.example.tripoo.data.model.Task;
import com.example.tripoo.utils.FirebaseHelper;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

public class TaskRepository {
    private FirebaseFirestore firestore;
    private static final String COLLECTION_TRIPS = "trips";
    private static final String COLLECTION_TASKS = "tasks";

    public TaskRepository() {
        firestore = FirebaseHelper.getInstance().getFirestore();
    }

    public com.google.android.gms.tasks.Task<DocumentReference> addTask(String tripId, Task task) {
        return firestore.collection(COLLECTION_TRIPS)
                .document(tripId)
                .collection(COLLECTION_TASKS)
                .add(task.toMap());
    }

    public com.google.android.gms.tasks.Task<Void> updateTask(String tripId, String taskId, Task task) {
        return firestore.collection(COLLECTION_TRIPS)
                .document(tripId)
                .collection(COLLECTION_TASKS)
                .document(taskId)
                .update(task.toMap());
    }

    public com.google.android.gms.tasks.Task<Void> deleteTask(String tripId, String taskId) {
        return firestore.collection(COLLECTION_TRIPS)
                .document(tripId)
                .collection(COLLECTION_TASKS)
                .document(taskId)
                .delete();
    }

    public com.google.android.gms.tasks.Task<QuerySnapshot> getTasks(String tripId) {
        return firestore.collection(COLLECTION_TRIPS)
                .document(tripId)
                .collection(COLLECTION_TASKS)
                .orderBy("category")
                .orderBy("dueDate")
                .get();
    }

    public ListenerRegistration listenToTasks(String tripId, com.google.firebase.firestore.EventListener<QuerySnapshot> listener) {
        return firestore.collection(COLLECTION_TRIPS)
                .document(tripId)
                .collection(COLLECTION_TASKS)
                .orderBy("category")
                .orderBy("dueDate")
                .addSnapshotListener(listener);
    }
}
