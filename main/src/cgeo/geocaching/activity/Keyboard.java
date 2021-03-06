package cgeo.geocaching.activity;

import org.eclipse.jdt.annotation.NonNull;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

/**
 * Class for hiding/showing the soft keyboard on Android.
 * 
 */
public class Keyboard {
    private final Activity activity;

    public Keyboard(final @NonNull Activity activity) {
        this.activity = activity;
    }

    public void hide() {
        // Check if no view has focus:
        final View view = activity.getCurrentFocus();
        if (view != null) {
            final InputMethodManager inputManager = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            inputManager.hideSoftInputFromWindow(view.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
        }
    }

    public void show(final View view) {
        view.requestFocus();
        ((InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(view, 0);
    }

    public void showDelayed(final View view) {
        view.postDelayed(new Runnable() {

            @Override
            public void run() {
                final InputMethodManager keyboard = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
                keyboard.showSoftInput(view, 0);
            }
        }, 50);
    }
}
