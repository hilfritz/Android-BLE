package com.hilfritz.blescanner.manager;


import android.os.Handler;
import android.os.Looper;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import java.util.ArrayList;
import java.util.List;

public class SafeDelay implements DefaultLifecycleObserver {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Runnable> pending = new ArrayList<>();

    public SafeDelay(@NonNull LifecycleOwner owner) {
        // auto-clean when Activity/Fragment is destroyed
        owner.getLifecycle().addObserver(this);
    }

    /**
     * Run the given action after delayMs on the main thread.
     */
    @MainThread
    public void post(long delayMs, @NonNull Runnable action) {
        // Wrap the action so we can track/remove it easily
        Runnable wrapper = new Runnable() {
            @Override
            public void run() {
                pending.remove(this);
                action.run();
            }
        };
        pending.add(wrapper);
        handler.postDelayed(wrapper, delayMs);
    }

    /**
     * Cancel all pending delayed actions.
     */
    @MainThread
    public void cancelAll() {
        for (Runnable r : new ArrayList<>(pending)) {
            handler.removeCallbacks(r);
        }
        pending.clear();
    }

    @Override
    public void onDestroy(@NonNull LifecycleOwner owner) {
        // Called automatically when the Activity/Fragment dies
        cancelAll();
    }
}