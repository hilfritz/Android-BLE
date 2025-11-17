package com.hilfritz.blescanner.ui.animate;

import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

public class TypeWriterStatus implements DefaultLifecycleObserver {

    public interface Listener {
        /** Called when the current text has finished typing (before any clear / sequence). */
        void onTypingFinished();

        /** Called after the text is cleared (single-message mode). */
        void onCleared();

        /** Called when a sequence of messages finishes (no more items, non-looping). */
        void onSequenceFinished();
    }

    private final WeakReference<TextView> textViewRef;
    private final Handler handler = new Handler(Looper.getMainLooper());

    // Core typing
    private CharSequence fullText = "";
    private int index = 0;
    private long charDelayMs = 50;

    // Auto clear
    private boolean autoClearEnabled = true;
    private long clearDelayMs = 3000;
    private long fadeDurationMs = 300;

    // Listener (weak)
    private WeakReference<Listener> listenerRef;

    // Blinking cursor
    private boolean cursorEnabled = false;
    private char cursorChar = '_';
    private long cursorBlinkMs = 500;
    private boolean cursorVisible = false;
    private String baseTextForCursor = null;

    // Sequence / queue
    private boolean inSequenceMode = false;
    private boolean sequenceLoopEnabled = false;
    private long betweenMessagesDelayMs = 500;
    private List<CharSequence> sequence = new ArrayList<>();
    private int sequenceIndex = 0;

    // ----- Runnables -----

    private final Runnable typingRunnable = new Runnable() {
        @Override
        public void run() {
            TextView tv = textViewRef.get();
            if (tv == null) {
                cancelInternal(false);
                return;
            }

            if (index <= fullText.length()) {
                tv.setText(fullText.subSequence(0, index));
                index++;

                if (index <= fullText.length()) {
                    // continue typing
                    handler.postDelayed(this, charDelayMs);
                } else {
                    // finished typing one message
                    Listener listener = getListener();
                    if (listener != null) listener.onTypingFinished();

                    if (inSequenceMode) {
                        // sequence mode: no clear, no cursor; go to next item
                        handler.postDelayed(sequenceAdvanceRunnable, betweenMessagesDelayMs);
                    } else {
                        // single-message mode
                        baseTextForCursor = fullText.toString();
                        if (cursorEnabled) {
                            startCursorBlink();
                        }
                        if (autoClearEnabled) {
                            handler.postDelayed(clearRunnable, clearDelayMs);
                        }
                    }
                }
            }
        }
    };

    private final Runnable clearRunnable = new Runnable() {
        @Override
        public void run() {
            stopCursorBlink();
            TextView tv = textViewRef.get();
            if (tv != null) {
                // fade out then clear
                tv.animate()
                        .alpha(0f)
                        .setDuration(fadeDurationMs)
                        .withEndAction(() -> {
                            TextView tv2 = textViewRef.get();
                            if (tv2 != null) {
                                tv2.setText("");
                                tv2.setAlpha(1f);
                            }
                            Listener listener = getListener();
                            if (listener != null) listener.onCleared();
                            cancelInternal(true);
                        })
                        .start();
            } else {
                Listener listener = getListener();
                if (listener != null) listener.onCleared();
                cancelInternal(true);
            }
        }
    };

    private final Runnable cursorBlinkRunnable = new Runnable() {
        @Override
        public void run() {
            TextView tv = textViewRef.get();
            if (tv == null || baseTextForCursor == null) {
                stopCursorBlink();
                return;
            }

            cursorVisible = !cursorVisible;
            String textToShow = cursorVisible
                    ? baseTextForCursor + " " + cursorChar
                    : baseTextForCursor;
            tv.setText(textToShow);

            handler.postDelayed(this, cursorBlinkMs);
        }
    };

    private final Runnable sequenceAdvanceRunnable = new Runnable() {
        @Override
        public void run() {
            if (!inSequenceMode || sequence == null || sequence.isEmpty()) {
                inSequenceMode = false;
                Listener listener = getListener();
                if (listener != null) listener.onSequenceFinished();
                return;
            }

            sequenceIndex++;
            if (sequenceIndex >= sequence.size()) {
                if (sequenceLoopEnabled) {
                    sequenceIndex = 0;
                } else {
                    inSequenceMode = false;
                    Listener listener = getListener();
                    if (listener != null) listener.onSequenceFinished();
                    return;
                }
            }

            startSequenceItem();
        }
    };

    // ----- ctor & lifecycle -----

    public TypeWriterStatus(@NonNull LifecycleOwner owner,
                            @NonNull TextView textView) {
        this.textViewRef = new WeakReference<>(textView);
        owner.getLifecycle().addObserver(this);
    }

    @Override
    public void onDestroy(@NonNull LifecycleOwner owner) {
        cancelInternal(true);
    }

    // ----- Public API (single message) -----

    /**
     * Start typing a single message with current settings.
     * Resets any active sequence.
     */
    @MainThread
    public void start(@NonNull CharSequence text) {
        inSequenceMode = false;
        cancelInternal(false);

        this.fullText = text;
        this.index = 0;

        TextView tv = textViewRef.get();
        if (tv != null) {
            tv.setAlpha(1f);
            tv.setText("");
            tv.setVisibility(View.VISIBLE);
        }

        handler.post(typingRunnable);
    }

    /**
     * Stop everything (typing, cursor, sequence, clearing).
     */
    @MainThread
    public void cancel() {
        cancelInternal(true);
    }

    // ----- Public API (sequence / queue) -----

    /**
     * Play a sequence of messages one after another (no auto-clear between them).
     * When done:
     *  - if loop = true, starts again from the first.
     *  - if loop = false, stops and onSequenceFinished() is called.
     */
    @MainThread
    public void playSequence(@NonNull List<CharSequence> messages, boolean loop) {
        cancelInternal(false);
        this.sequence.clear();
        this.sequence.addAll(messages);
        this.sequenceLoopEnabled = loop;
        this.sequenceIndex = 0;
        this.inSequenceMode = true;

        if (this.sequence.isEmpty()) {
            inSequenceMode = false;
            return;
        }

        startSequenceItem();
    }

    private void startSequenceItem() {
        TextView tv = textViewRef.get();
        if (tv == null) {
            inSequenceMode = false;
            return;
        }
        stopCursorBlink();
        tv.setAlpha(1f);
        tv.setText("");

        fullText = sequence.get(sequenceIndex);
        index = 0;
        handler.post(typingRunnable);
    }

    // ----- Internal helpers -----

    private void cancelInternal(boolean clearListener) {
        handler.removeCallbacks(typingRunnable);
        handler.removeCallbacks(clearRunnable);
        handler.removeCallbacks(cursorBlinkRunnable);
        handler.removeCallbacks(sequenceAdvanceRunnable);
        stopCursorBlink();
        inSequenceMode = false;

        if (clearListener) {
            listenerRef = null;
        }
    }

    private void startCursorBlink() {
        stopCursorBlink();
        cursorVisible = false;
        handler.post(cursorBlinkRunnable);
    }

    private void stopCursorBlink() {
        handler.removeCallbacks(cursorBlinkRunnable);
        baseTextForCursor = null;
        cursorVisible = false;
    }

    private Listener getListener() {
        return listenerRef != null ? listenerRef.get() : null;
    }

    // ----- Setters / config -----

    public void setListener(Listener listener) {
        this.listenerRef = (listener == null) ? null : new WeakReference<>(listener);
    }

    /** Delay between characters (lower = faster typing) */
    public void setCharDelayMs(long ms) {
        this.charDelayMs = ms;
    }

    /** Delay before auto-clear after typing (single message mode) */
    public void setClearDelayMs(long ms) {
        this.clearDelayMs = ms;
    }

    /** Enable / disable auto-clear after typing (single message mode only) */
    public void setAutoClear(boolean enabled) {
        this.autoClearEnabled = enabled;
    }

    /** Duration of fade-out animation when clearing (single message mode) */
    public void setFadeDurationMs(long ms) {
        this.fadeDurationMs = ms;
    }

    /** Enable or disable blinking cursor after typing (single message mode) */
    public void setCursorEnabled(boolean enabled) {
        this.cursorEnabled = enabled;
    }

    /** Change cursor character (default '_') */
    public void setCursorChar(char c) {
        this.cursorChar = c;
    }

    /** Blink interval for cursor (ms) */
    public void setCursorBlinkMs(long ms) {
        this.cursorBlinkMs = ms;
    }

    /** Delay between sequence messages (ms) */
    public void setBetweenMessagesDelayMs(long ms) {
        this.betweenMessagesDelayMs = ms;
    }
}