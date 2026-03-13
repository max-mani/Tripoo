package com.manikandan.tripoo.view;

import android.content.Context;
import android.os.CountDownTimer;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.manikandan.tripoo.data.model.Trip;
import com.manikandan.tripoo.databinding.CountdownCardBinding;
import com.manikandan.tripoo.databinding.ItemCountdownBoxBinding;
import com.manikandan.tripoo.utils.CountdownTimeFormatter;

/**
 * Reusable countdown view showing Days | Hours | Minutes | Seconds until a trip start time.
 * Lifecycle-safe: stops the timer when detached. Use setTripStartTime + start() or bindTrip().
 */
public class TripCountdownView extends FrameLayout {

    private CountdownCardBinding binding;

    private long tripStartTimeMillis = 0L;
    @Nullable
    private CountDownTimer countDownTimer;
    private boolean running;

    public TripCountdownView(@NonNull Context context) {
        super(context);
        init(context);
    }

    public TripCountdownView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public TripCountdownView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(@NonNull Context context) {
        binding = CountdownCardBinding.inflate(LayoutInflater.from(context), this, true);
        setLabels();
        showZeros();
    }

    private void setLabels() {
        setLabel(binding.boxDays, "DAYS");
        setLabel(binding.boxHours, "HOURS");
        setLabel(binding.boxMinutes, "MINUTES");
        setLabel(binding.boxSeconds, "SECONDS");
    }

    private static void setLabel(@Nullable ItemCountdownBoxBinding box, String label) {
        if (box != null && box.tvCountdownLabel != null) box.tvCountdownLabel.setText(label);
    }

    /**
     * Sets the trip start time (UTC epoch millis). Call start() to begin countdown.
     */
    public void setTripStartTime(long millis) {
        tripStartTimeMillis = millis;
        stop();
        updateUiFromRemaining(millis - System.currentTimeMillis());
    }

    /**
     * Binds a Firestore Trip and starts the countdown.
     * Uses Trip's startDate (Timestamp → millis). Call from HomeFragment when trip is loaded.
     */
    public void bindTrip(@Nullable Trip trip) {
        if (trip == null) {
            showZeros();
            stop();
            return;
        }
        long millis = trip.getStartDate();
        setTripStartTime(millis);
        start();
    }

    /**
     * Starts the countdown. No-op if already running or start time is in the past.
     */
    public void start() {
        if (running) return;
        long remaining = tripStartTimeMillis - System.currentTimeMillis();
        if (remaining <= 0) {
            showZeros();
            return;
        }
        running = true;
        countDownTimer = new CountDownTimer(remaining, 1000L) {
            @Override
            public void onTick(long millisUntilFinished) {
                if (!running) return;
                updateUiFromRemaining(millisUntilFinished);
            }

            @Override
            public void onFinish() {
                running = false;
                countDownTimer = null;
                showZeros();
            }
        };
        countDownTimer.start();
    }

    /**
     * Stops the countdown and clears the timer. Safe to call multiple times.
     */
    public void stop() {
        running = false;
        if (countDownTimer != null) {
            countDownTimer.cancel();
            countDownTimer = null;
        }
    }

    private void updateUiFromRemaining(long remainingMillis) {
        if (remainingMillis <= 0) {
            showZeros();
            return;
        }
        long totalSeconds = remainingMillis / 1000L;
        long seconds = totalSeconds % 60;
        long minutes = (totalSeconds / 60) % 60;
        long hours = (totalSeconds / (60 * 60)) % 24;
        long days = totalSeconds / (60 * 60 * 24);

        setValue(binding.boxDays, CountdownTimeFormatter.formatDays(days));
        setValue(binding.boxHours, CountdownTimeFormatter.formatTwoDigits(hours));
        setValue(binding.boxMinutes, CountdownTimeFormatter.formatTwoDigits(minutes));
        setValue(binding.boxSeconds, CountdownTimeFormatter.formatTwoDigits(seconds));
    }

    private static void setValue(@Nullable ItemCountdownBoxBinding box, String value) {
        if (box != null && box.tvCountdownValue != null) box.tvCountdownValue.setText(value);
    }

    private void showZeros() {
        setValue(binding.boxDays, "0");
        setValue(binding.boxHours, "00");
        setValue(binding.boxMinutes, "00");
        setValue(binding.boxSeconds, "00");
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stop();
    }
}
