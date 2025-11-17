package com.hilfritz.blescanner.ui.dialog;


import android.app.Activity;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.hilfritz.blescanner.R;

import java.lang.ref.WeakReference;

public class DialogManager implements DefaultLifecycleObserver {

    private final WeakReference<Activity> activityRef;
    private androidx.appcompat.app.AlertDialog currentDialog;

    public DialogManager(@NonNull AppCompatActivity activity) {
        this.activityRef = new WeakReference<>(activity);
        // Auto-cleanup when Activity is destroyed
        activity.getLifecycle().addObserver(this);
    }

    /**
     * Show a simple info dialog with OK button.
     */
    @MainThread
    public void showInfoDialog(@NonNull String title,
                               @NonNull String message) {
       this.showInfoDialogXml(title, message);
    }

    public void showInfoDialogXml(@NonNull String title, @NonNull String message) {
        Activity activity = activityRef.get();
        if (activity == null || activity.isFinishing()) return;

        dismissCurrent();

        // Inflate custom layout
        LayoutInflater inflater = LayoutInflater.from(activity);
        View view = inflater.inflate(R.layout.dialog_info, null, false);

        TextView titleView = view.findViewById(R.id.text_title);
        TextView messageView = view.findViewById(R.id.text_message);
        Button okButton = view.findViewById(R.id.button_ok);

        titleView.setText(title);
        messageView.setText(message);

        MaterialAlertDialogBuilder builder =
                new MaterialAlertDialogBuilder(activity, R.style.MyRoundedAlertDialogTheme)
                        .setView(view)          // use our custom layout
                        .setCancelable(true);   // back button / outside tap closes

        currentDialog = builder.create();

        okButton.setOnClickListener(v -> {
            // just close, do nothing else
            dismissCurrent();
        });

        currentDialog.setOnDismissListener(dialog -> currentDialog = null);

        currentDialog.show();
    }


    /**
     * Show a confirm dialog with OK / Cancel callbacks.
     */
    @MainThread
    public void showConfirmDialog(@NonNull String title,
                                  @NonNull String message,
                                  @NonNull DialogInterface.OnClickListener onOk) {
        Activity activity = activityRef.get();
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }

        dismissCurrent();

        currentDialog = new MaterialAlertDialogBuilder(activity,
                R.style.MyRoundedAlertDialogTheme)
                .setTitle(title)
                .setMessage(message)
                .setCancelable(true)
                .setPositiveButton("OK", onOk)
                .setNegativeButton("Cancel", null)
                .setOnDismissListener(dialog -> currentDialog = null)
                .create();

        currentDialog.show();
    }

    /**
     * Safely dismiss the current dialog, if any.
     */
    @MainThread
    public void dismissCurrent() {
        if (currentDialog != null && currentDialog.isShowing()) {
            currentDialog.dismiss();
        }
        currentDialog = null;
    }

    // ---------- Lifecycle cleanup ----------

    @Override
    public void onPause(@NonNull LifecycleOwner owner) {
        // Optional: hide dialog when activity goes to background
        dismissCurrent();
    }

    @Override
    public void onDestroy(@NonNull LifecycleOwner owner) {
        // Make absolutely sure nothing is leaking
        dismissCurrent();
    }
}