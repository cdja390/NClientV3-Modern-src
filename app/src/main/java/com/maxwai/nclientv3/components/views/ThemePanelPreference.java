package com.maxwai.nclientv3.components.views;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.maxwai.nclientv3.R;
import com.maxwai.nclientv3.utility.ThemeCardHelper;

public class ThemePanelPreference extends Preference {
    boolean binding;

    public ThemePanelPreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.pref_theme_panel);
    }

    private SharedPreferences prefs() {
        return getContext().getSharedPreferences("Settings", 0);
    }

    private void recreate() {
        Context context = getContext();
        while (!(context instanceof Activity) && context instanceof ContextWrapper) {
            context = ((ContextWrapper) context).getBaseContext();
        }
        if (context instanceof Activity) {
            ((Activity) context).recreate();
        }
    }

    private void selectColor(ViewGroup group, String selected) {
        int count = group.getChildCount();
        for (int i = 0; i < count; i++) {
            View child = group.getChildAt(i);
            if (child instanceof MaterialButton) {
                Object tag = child.getTag();
                ((MaterialButton) child).setChecked(tag != null && selected.equals(tag));
            }
        }
    }

    private void wireDots(ViewGroup group) {
        int count = group.getChildCount();
        for (int i = 0; i < count; i++) {
            View child = group.getChildAt(i);
            if (child instanceof MaterialButton) {
                child.setOnClickListener(new Dot(this));
            }
        }
    }

    public void applyChoice(int kind, int checkedId) {
        if (binding || kind != 0) {
            return;
        }
        final String modeKey;
        final int nightMode;
        final boolean blackTheme;
        if (checkedId == R.id.mode_system) {
            modeKey = "system";
            nightMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
            blackTheme = false;
        } else if (checkedId == R.id.mode_dark) {
            modeKey = "dark";
            nightMode = AppCompatDelegate.MODE_NIGHT_YES;
            blackTheme = false;
        } else if (checkedId == R.id.mode_black) {
            modeKey = "black";
            nightMode = AppCompatDelegate.MODE_NIGHT_YES;
            blackTheme = true;
        } else {
            modeKey = "light";
            nightMode = AppCompatDelegate.MODE_NIGHT_NO;
            blackTheme = false;
        }
        prefs().edit()
            .putString("theme_select", modeKey)
            .putBoolean("black_theme", blackTheme)
            .apply();
        AppCompatDelegate.setDefaultNightMode(nightMode);
        recreate();
    }

    public void applyColor(String color) {
        if (binding) {
            return;
        }
        prefs().edit().putString("theme_color", color).apply();
        recreate();
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        View itemView = holder.itemView;
        MaterialButtonToggleGroup modeGroup = itemView.findViewById(R.id.mode_group);
        ViewGroup colorGroup = itemView.findViewById(R.id.color_group);
        binding = true;
        SharedPreferences sp = prefs();
        final int checkedId;
        if (sp.getBoolean("black_theme", false)) {
            checkedId = R.id.mode_black;
        } else {
            String mode = sp.getString("theme_select", "light");
            if ("system".equals(mode)) {
                checkedId = R.id.mode_system;
            } else if ("dark".equals(mode)) {
                checkedId = R.id.mode_dark;
            } else {
                checkedId = R.id.mode_light;
            }
        }
        modeGroup.check(checkedId);
        String color = sp.getString("theme_color", "purple");
        binding = false;
        modeGroup.clearOnButtonCheckedListeners();
        modeGroup.addOnButtonCheckedListener(new Pick(this, 0));
        ThemeCardHelper.bindSukiSuCards(colorGroup, color, new Dot(this));

        View header = itemView.findViewById(R.id.header_container);
        View modeContainer = itemView.findViewById(R.id.mode_container);
        View colorContainer = itemView.findViewById(R.id.color_container);
        ImageView btnCollapse = itemView.findViewById(R.id.btn_collapse);
        ThemeCardHelper.setupSectionCollapse(header, modeContainer, colorContainer, btnCollapse);
    }

    static class Pick implements MaterialButtonToggleGroup.OnButtonCheckedListener {
        final ThemePanelPreference host;
        final int kind;

        Pick(ThemePanelPreference host, int kind) {
            this.host = host;
            this.kind = kind;
        }

        @Override
        public void onButtonChecked(MaterialButtonToggleGroup group, int checkedId, boolean isChecked) {
            if (!isChecked) {
                return;
            }
            host.applyChoice(kind, checkedId);
        }
    }

    static class Dot implements View.OnClickListener {
        final ThemePanelPreference host;

        Dot(ThemePanelPreference host) {
            this.host = host;
        }

        @Override
        public void onClick(View v) {
            Object tag = v.getTag();
            if (tag instanceof String) {
                host.applyColor((String) tag);
            }
        }
    }
}
