package com.example.tripoo.viewmodel;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.example.tripoo.data.model.Expense;
import com.example.tripoo.data.repository.AuthRepository;
import com.example.tripoo.data.repository.ExpenseRepository;
import com.example.tripoo.utils.Resource;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.List;

public class ExpenseViewModel extends AndroidViewModel {
    private ExpenseRepository expenseRepository;
    private AuthRepository authRepository;
    private MutableLiveData<Resource<List<Expense>>> expensesLiveData = new MutableLiveData<>();
    private MutableLiveData<Resource<String>> addExpenseLiveData = new MutableLiveData<>();
    private MutableLiveData<Resource<String>> updateExpenseLiveData = new MutableLiveData<>();
    private MutableLiveData<Double> youOweLiveData = new MutableLiveData<>();
    private MutableLiveData<Double> youAreOwedLiveData = new MutableLiveData<>();
    private ListenerRegistration expensesListener;
    private String currentTripId;

    public ExpenseViewModel(@NonNull Application application) {
        super(application);
        expenseRepository = new ExpenseRepository();
        authRepository = new AuthRepository();
    }

    public void loadExpenses(String tripId) {
        currentTripId = tripId;
        expensesLiveData.setValue(Resource.loading());
        
        if (expensesListener != null) {
            expensesListener.remove();
        }
        
        expensesListener = expenseRepository.listenToExpenses(tripId, (snapshot, e) -> {
            if (e != null) {
                expensesLiveData.setValue(Resource.error(e.getMessage()));
                return;
            }
            
            if (snapshot != null) {
                List<Expense> expenses = new ArrayList<>();
                for (DocumentSnapshot doc : snapshot.getDocuments()) {
                    Expense expense = doc.toObject(Expense.class);
                    if (expense != null) {
                        expense.setExpenseId(doc.getId());
                        expenses.add(expense);
                    }
                }
                expensesLiveData.setValue(Resource.success(expenses));
                calculateOwedAmounts(expenses);
            }
        });
    }

    public void addExpense(String tripId, String title, double amount, String paidBy, List<String> splitWith) {
        addExpenseLiveData.setValue(Resource.loading());
        
        String currentUserId = authRepository.getCurrentUser() != null ? 
                authRepository.getCurrentUser().getUid() : null;
        
        Expense expense = new Expense(null, title, amount, paidBy, splitWith, currentUserId, Timestamp.now());
        expenseRepository.addExpense(tripId, expense)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        addExpenseLiveData.setValue(Resource.success("Expense added successfully"));
                    } else {
                        addExpenseLiveData.setValue(Resource.error(
                                task.getException() != null ? task.getException().getMessage() : "Failed to add expense"));
                    }
                });
    }

    public void updateExpense(String tripId, String expenseId, String title, double amount, String paidBy, List<String> splitWith) {
        updateExpenseLiveData.setValue(Resource.loading());
        
        String currentUserId = authRepository.getCurrentUser() != null ? 
                authRepository.getCurrentUser().getUid() : null;
        
        Expense expense = new Expense(expenseId, title, amount, paidBy, splitWith, currentUserId, Timestamp.now());
        expenseRepository.updateExpense(tripId, expenseId, expense)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        updateExpenseLiveData.setValue(Resource.success("Expense updated successfully"));
                    } else {
                        updateExpenseLiveData.setValue(Resource.error(
                                task.getException() != null ? task.getException().getMessage() : "Failed to update expense"));
                    }
                });
    }

    public void deleteExpense(String tripId, String expenseId) {
        expenseRepository.deleteExpense(tripId, expenseId)
                .addOnCompleteListener(task -> {
                    // Expense will be removed from list via listener
                });
    }

    private void calculateOwedAmounts(List<Expense> expenses) {
        String currentUserId = authRepository.getCurrentUser() != null ? 
                authRepository.getCurrentUser().getUid() : null;
        
        if (currentUserId == null) {
            youOweLiveData.setValue(0.0);
            youAreOwedLiveData.setValue(0.0);
            return;
        }
        
        double youOwe = 0.0;
        double youAreOwed = 0.0;
        
        for (Expense expense : expenses) {
            if (expense.getSplitWith() != null && expense.getSplitWith().contains(currentUserId)) {
                double splitAmount = expense.getAmount() / expense.getSplitWith().size();
                if (expense.getPaidBy().equals(currentUserId)) {
                    youAreOwed += expense.getAmount() - splitAmount;
                } else {
                    youOwe += splitAmount;
                }
            }
        }
        
        youOweLiveData.setValue(youOwe);
        youAreOwedLiveData.setValue(youAreOwed);
    }

    public LiveData<Resource<List<Expense>>> getExpensesLiveData() {
        return expensesLiveData;
    }

    public LiveData<Resource<String>> getAddExpenseLiveData() {
        return addExpenseLiveData;
    }

    public LiveData<Resource<String>> getUpdateExpenseLiveData() {
        return updateExpenseLiveData;
    }

    public LiveData<Double> getYouOweLiveData() {
        return youOweLiveData;
    }

    public LiveData<Double> getYouAreOwedLiveData() {
        return youAreOwedLiveData;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (expensesListener != null) {
            expensesListener.remove();
        }
    }
}
