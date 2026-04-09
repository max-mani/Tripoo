package com.manikandan.tripoo.viewmodel;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.manikandan.tripoo.data.model.Expense;
import com.manikandan.tripoo.data.repository.AuthRepository;
import com.manikandan.tripoo.data.repository.ExpenseRepository;
import com.manikandan.tripoo.utils.Resource;
import com.google.firebase.firestore.ListenerRegistration;
import kotlin.Unit;

import java.util.List;

public class ExpenseViewModel extends AndroidViewModel {
    private final ExpenseRepository expenseRepository;
    private final AuthRepository authRepository;
    private final MutableLiveData<Resource<List<Expense>>> expensesLiveData = new MutableLiveData<>();
    private final MutableLiveData<Resource<String>> addExpenseLiveData = new MutableLiveData<>();
    private final MutableLiveData<Resource<String>> updateExpenseLiveData = new MutableLiveData<>();
    private final MutableLiveData<Double> youOweLiveData = new MutableLiveData<>();
    private final MutableLiveData<Double> youAreOwedLiveData = new MutableLiveData<>();
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

        expensesListener = expenseRepository.listenToExpenses(tripId, (expenses, e) -> {
            if (e != null) {
                expensesLiveData.setValue(Resource.error(e.getMessage()));
                return Unit.INSTANCE;
            }
            if (expenses != null) {
                expensesLiveData.setValue(Resource.success(expenses));
                calculateOwedAmounts(expenses);
            }
            return Unit.INSTANCE;
        });
    }

    public void addExpense(String tripId, String title, double amount, String category, String paidBy, List<String> splitWith) {
        addExpenseLiveData.setValue(Resource.loading());
        if (category == null) category = "other";
        Expense expense = new Expense("", title, amount, category, paidBy, splitWith != null ? splitWith : java.util.Collections.emptyList(), System.currentTimeMillis(), false);
        expenseRepository.addExpense(tripId, expense, err -> {
            if (err == null) {
                addExpenseLiveData.setValue(Resource.success("Expense added successfully"));
            } else {
                addExpenseLiveData.setValue(Resource.error(err.getMessage() != null ? err.getMessage() : "Failed to add expense"));
            }
            return Unit.INSTANCE;
        });
    }

    public void updateExpense(String tripId, String expenseId, String title, double amount, String paidBy, List<String> splitWith) {
        updateExpenseLiveData.setValue(Resource.loading());
        Expense expense = new Expense(expenseId, title, amount, "other", paidBy, splitWith != null ? splitWith : java.util.Collections.emptyList(), System.currentTimeMillis(), false);
        expenseRepository.updateExpense(tripId, expenseId, expense, err -> {
            if (err == null) {
                updateExpenseLiveData.setValue(Resource.success("Expense updated successfully"));
            } else {
                updateExpenseLiveData.setValue(Resource.error(err.getMessage() != null ? err.getMessage() : "Failed to update expense"));
            }
            return Unit.INSTANCE;
        });
    }

    public void deleteExpense(String tripId, String expenseId) {
        expenseRepository.deleteExpense(tripId, expenseId, err -> Unit.INSTANCE);
    }

    private void calculateOwedAmounts(List<Expense> expenses) {
        String currentUserId = authRepository.getCurrentUser() != null ? authRepository.getCurrentUser().getUid() : null;
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
