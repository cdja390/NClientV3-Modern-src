package com.maxwai.nclientv3.utility;
// Modified/Added for NClientV3 Modern under Apache-2.0 (a derivative of NClientV3 by maxwai and contributors); see docs/PATCHED_FILES.md
import android.app.Activity;
import android.content.Context;
import android.graphics.Rect;
import android.os.Build;
import android.view.ActionMode;
import android.view.KeyEvent;
import android.view.KeyboardShortcutGroup;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SearchEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.WindowInsets;
import android.view.accessibility.AccessibilityEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;
import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.widget.SearchView;
import com.maxwai.nclientv3.R;
import com.maxwai.nclientv3.async.database.Queries;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Makes the in-page SearchView usable: hide IME on Enter, and let Back
 * dismiss the keyboard first instead of collapsing the search view.
 */
public class SearchImeHelper {
    private static String historyQuery = "";

    public static void onSubmit(View view) {
        if (view == null) return;
        View focused = view.findFocus();
        view.clearFocus();
        hideKeyboard(focused != null ? focused : view);
    }

    /** Safe entry point for anonymous listeners that cannot read private fields. */
    public static void onSubmitActivity(Activity activity) {
        if (activity == null) return;
        View focused = activity.getCurrentFocus();
        View decor = activity.getWindow() != null ? activity.getWindow().getDecorView() : null;
        if (focused != null) {
            focused.clearFocus();
            hideKeyboard(focused);
        }
        if (decor != null) hideKeyboard(decor);
    }

    public static void hideKeyboard(View view) {
        if (view == null) return;
        Context ctx = view.getContext();
        if (ctx == null) return;
        InputMethodManager imm = (InputMethodManager) ctx.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    public static boolean isImeVisible(Activity activity) {
        if (activity == null || activity.getWindow() == null) return false;
        View decor = activity.getWindow().getDecorView();
        if (decor == null) return false;
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                WindowInsets insets = decor.getRootWindowInsets();
                if (insets != null) return insets.isVisible(WindowInsets.Type.ime());
            } catch (Throwable ignored) {
            }
        }
        Rect rect = new Rect();
        decor.getWindowVisibleDisplayFrame(rect);
        int height = decor.getHeight();
        if (height <= 0) return false;
        return (height - rect.bottom) > (height * 15 / 100);
    }

    private static boolean searchHasInput(Activity activity, MenuItem searchItem) {
        View actionView = searchItem.getActionView();
        if (actionView != null && actionView.hasFocus()) return true;
        return isImeVisible(activity);
    }

    public static boolean handleBack(Activity activity, MenuItem searchItem) {
        return handleBack(activity, searchItem, null);
    }

    public static boolean handleBack(Activity activity, MenuItem searchItem, boolean[] pendingHide) {
        if (searchItem == null || !searchItem.isActionViewExpanded()) return false;
        boolean pending = pendingHide != null && pendingHide[0];
        if (searchHasInput(activity, searchItem) || pending) {
            onSubmit(searchItem.getActionView());
            if (pendingHide != null) pendingHide[0] = false;
            return true;
        }
        return false;
    }

    /** API 33+: predictive back bypasses key events; take Back above AppCompat's collapse. */
    static final class Api33Back {
        static Object register(final Activity activity, final MenuItem searchItem, final boolean[] pendingHide) {
            OnBackInvokedCallback cb = new OnBackInvokedCallback() {
                @Override
                public void onBackInvoked() {
                    if (searchItem.isActionViewExpanded()
                            && (searchHasInput(activity, searchItem) || (pendingHide != null && pendingHide[0]))) {
                        onSubmit(searchItem.getActionView());
                        if (pendingHide != null) pendingHide[0] = false;
                    } else {
                        searchItem.collapseActionView();
                    }
                }
            };
            OnBackInvokedDispatcher d = activity.getOnBackInvokedDispatcher();
            try {
                d.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_OVERLAY + 1, cb);
            } catch (Throwable t) {
                d.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_OVERLAY, cb);
            }
            return cb;
        }

        static void unregister(Activity activity, Object cb) {
            if (cb == null) return;
            try {
                activity.getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback((OnBackInvokedCallback) cb);
            } catch (Throwable ignored) {
            }
        }
    }

    /** Consumes KEYCODE_BACK before AppCompatDelegateImpl can collapse the SearchView. */
    static final class KeyInterceptCallback implements Window.Callback {
        final Window.Callback inner;
        final Activity activity;
        final MenuItem searchItem;
        final boolean[] pendingHide;

        KeyInterceptCallback(Window.Callback inner, Activity activity, MenuItem searchItem, boolean[] pendingHide) {
            this.inner = inner;
            this.activity = activity;
            this.searchItem = searchItem;
            this.pendingHide = pendingHide;
        }

        private boolean consumeBack(KeyEvent event) {
            if (event == null || event.getKeyCode() != KeyEvent.KEYCODE_BACK) return false;
            return handleBack(activity, searchItem, pendingHide);
        }

        @Override
        public boolean dispatchKeyEvent(KeyEvent event) {
            if (consumeBack(event)) return true;
            return inner != null && inner.dispatchKeyEvent(event);
        }

        @Override
        public boolean dispatchKeyShortcutEvent(KeyEvent event) {
            return inner != null && inner.dispatchKeyShortcutEvent(event);
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent event) {
            return inner != null && inner.dispatchTouchEvent(event);
        }

        @Override
        public boolean dispatchTrackballEvent(MotionEvent event) {
            return inner != null && inner.dispatchTrackballEvent(event);
        }

        @Override
        public boolean dispatchGenericMotionEvent(MotionEvent event) {
            return inner != null && inner.dispatchGenericMotionEvent(event);
        }

        @Override
        public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
            return inner != null && inner.dispatchPopulateAccessibilityEvent(event);
        }

        @Override
        public View onCreatePanelView(int featureId) {
            return inner != null ? inner.onCreatePanelView(featureId) : null;
        }

        @Override
        public boolean onCreatePanelMenu(int featureId, Menu menu) {
            return inner != null && inner.onCreatePanelMenu(featureId, menu);
        }

        @Override
        public boolean onPreparePanel(int featureId, View view, Menu menu) {
            return inner != null && inner.onPreparePanel(featureId, view, menu);
        }

        @Override
        public boolean onMenuOpened(int featureId, Menu menu) {
            return inner != null && inner.onMenuOpened(featureId, menu);
        }

        @Override
        public boolean onMenuItemSelected(int featureId, MenuItem item) {
            return inner != null && inner.onMenuItemSelected(featureId, item);
        }

        @Override
        public void onWindowAttributesChanged(WindowManager.LayoutParams attrs) {
            if (inner != null) inner.onWindowAttributesChanged(attrs);
        }

        @Override
        public void onContentChanged() {
            if (inner != null) inner.onContentChanged();
        }

        @Override
        public void onWindowFocusChanged(boolean hasFocus) {
            if (inner != null) inner.onWindowFocusChanged(hasFocus);
        }

        @Override
        public void onAttachedToWindow() {
            if (inner != null) inner.onAttachedToWindow();
        }

        @Override
        public void onDetachedFromWindow() {
            if (inner != null) inner.onDetachedFromWindow();
        }

        @Override
        public void onPanelClosed(int featureId, Menu menu) {
            if (inner != null) inner.onPanelClosed(featureId, menu);
        }

        @Override
        public boolean onSearchRequested() {
            return inner != null && inner.onSearchRequested();
        }

        @Override
        public boolean onSearchRequested(SearchEvent searchEvent) {
            return inner != null && inner.onSearchRequested(searchEvent);
        }

        @Override
        public ActionMode onWindowStartingActionMode(ActionMode.Callback callback) {
            return inner != null ? inner.onWindowStartingActionMode(callback) : null;
        }

        @Override
        public ActionMode onWindowStartingActionMode(ActionMode.Callback callback, int type) {
            return inner != null ? inner.onWindowStartingActionMode(callback, type) : null;
        }

        @Override
        public void onActionModeStarted(ActionMode mode) {
            if (inner != null) inner.onActionModeStarted(mode);
        }

        @Override
        public void onActionModeFinished(ActionMode mode) {
            if (inner != null) inner.onActionModeFinished(mode);
        }

        @Override
        public void onProvideKeyboardShortcuts(List<KeyboardShortcutGroup> data, Menu menu, int deviceId) {
            if (inner != null) inner.onProvideKeyboardShortcuts(data, menu, deviceId);
        }

        @Override
        public void onPointerCaptureChanged(boolean hasCapture) {
            if (inner != null) inner.onPointerCaptureChanged(hasCapture);
        }
    }

    private static void installKeyIntercept(Activity activity, MenuItem searchItem, boolean[] pendingHide) {
        if (activity == null || activity.getWindow() == null) return;
        Window window = activity.getWindow();
        Window.Callback existing = window.getCallback();
        Window.Callback inner = existing;
        if (existing instanceof KeyInterceptCallback) {
            inner = ((KeyInterceptCallback) existing).inner;
        }
        window.setCallback(new KeyInterceptCallback(inner, activity, searchItem, pendingHide));
    }

    public static void install(final Activity activity, final MenuItem searchItem) {
        if (activity == null || searchItem == null) return;
        searchItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS | MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW);
        View actionView = searchItem.getActionView();
        if (actionView instanceof SearchView) {
            ((SearchView) actionView).setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        }
        final boolean[] pendingHide = new boolean[]{false};
        installKeyIntercept(activity, searchItem, pendingHide);
        final boolean api33 = Build.VERSION.SDK_INT >= 33;
        final ComponentActivity component = activity instanceof ComponentActivity ? (ComponentActivity) activity : null;
        final OnBackPressedCallback callback = component == null ? null : new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (handleBack(activity, searchItem, pendingHide)) return;
                setEnabled(false);
                component.getOnBackPressedDispatcher().onBackPressed();
                setEnabled(true);
            }
        };
        if (!api33 && component != null) {
            component.getOnBackPressedDispatcher().addCallback(component, callback);
        }
        final Object[] api33Cb = new Object[1];
        searchItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem item) {
                pendingHide[0] = true;
                View decor = activity.getWindow() != null ? activity.getWindow().getDecorView() : null;
                if (decor != null) {
                    decor.post(new Runnable() {
                        @Override
                        public void run() {
                            if (api33) {
                                Api33Back.unregister(activity, api33Cb[0]);
                                api33Cb[0] = null;
                                if (searchItem.isActionViewExpanded()) {
                                    try {
                                        api33Cb[0] = Api33Back.register(activity, searchItem, pendingHide);
                                    } catch (Throwable ignored) {
                                    }
                                }
                            } else if (component != null) {
                                try {
                                    callback.remove();
                                } catch (Throwable ignored) {
                                }
                                component.getOnBackPressedDispatcher().addCallback(component, callback);
                            }
                        }
                    });
                }
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem item) {
                pendingHide[0] = false;
                if (api33) {
                    Api33Back.unregister(activity, api33Cb[0]);
                    api33Cb[0] = null;
                }
                View view = item.getActionView();
                if (view != null) hideKeyboard(view);
                return true;
            }
        });
    }

    public static void installHistory(final Activity activity, MenuItem searchItem) {
        if (activity == null || searchItem == null) return;
        install(activity, searchItem);
        View actionView = searchItem.getActionView();
        if (!(actionView instanceof SearchView)) return;
        final SearchView searchView = (SearchView) actionView;
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                onSubmit(searchView);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                historyQuery = newText == null ? "" : newText;
                applyHistoryFilter(activity, historyQuery);
                return true;
            }
        });
    }

    public static void refreshHistory(Activity activity) {
        if (activity == null) return;
        if (historyQuery == null || historyQuery.trim().length() == 0) return;
        applyHistoryFilter(activity, historyQuery);
    }

    private static Object findField(Object target, String name) {
        Class<?> c = target.getClass();
        while (c != null) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(target);
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            } catch (Throwable t) {
                return null;
            }
        }
        return null;
    }

    private static void applyHistoryFilter(Activity activity, String query) {
        List<?> all;
        try {
            all = Queries.HistoryTable.getHistory();
        } catch (Throwable t) {
            return;
        }
        if (all == null) all = Collections.emptyList();
        String needle = query == null ? "" : query.trim().toLowerCase();
        List<Object> filtered = new ArrayList<Object>();
        for (Object item : all) {
            if (needle.length() == 0 || titleOf(item).toLowerCase().contains(needle)) {
                filtered.add(item);
            }
        }
        try {
            Object adapter = findField(activity, "adapter");
            if (adapter != null) {
                adapter.getClass().getMethod("restartDataset", List.class).invoke(adapter, filtered);
            }
        } catch (Throwable ignored) {
        }
        View empty = activity.findViewById(R.id.history_empty);
        if (empty != null) {
            empty.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    private static String titleOf(Object item) {
        if (item == null) return "";
        for (String m : new String[]{"getTitle", "getTitleString"}) {
            try {
                Object title = item.getClass().getMethod(m).invoke(item);
                if (title != null) return String.valueOf(title);
            } catch (Throwable ignored) {
            }
        }
        return "";
    }
}
