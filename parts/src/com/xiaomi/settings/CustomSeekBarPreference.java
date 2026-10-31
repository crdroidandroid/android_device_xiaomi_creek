/*
 * Copyright (C) 2025 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 *
 * CSAK AKKOR add a buildhez, ha a klee fában NINCS már
 * com.xiaomi.settings.CustomSeekBarPreference! Ha van, ezt a fájlt
 * és az optional/attrs_custom_seekbar.xml-t hagyd ki.
 *
 * Önálló, kompakt implementáció az auto_hbm.xml által használt
 * szerződéssel: android:min / android:max / android:defaultValue /
 * settings:units; int-ként perzisztál; OnPreferenceChangeListener-nek
 * Integer értéket ad át.
 */

package com.xiaomi.settings;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

public class CustomSeekBarPreference extends Preference
        implements SeekBar.OnSeekBarChangeListener {

    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
    private static final String SETTINGS_NS = "http://schemas.android.com/apk/res-auto";

    private int mMin;
    private int mMax;
    private int mDefault;
    private int mValue;
    private String mUnits = "";

    private SeekBar mSeekBar;
    private TextView mValueText;

    public CustomSeekBarPreference(Context context, AttributeSet attrs) {
        super(context, attrs);

        mMin = attrs.getAttributeIntValue(ANDROID_NS, "min", 0);
        mMax = attrs.getAttributeIntValue(ANDROID_NS, "max", 100);
        mDefault = attrs.getAttributeIntValue(ANDROID_NS, "defaultValue", mMin);

        TypedArray a = context.obtainStyledAttributes(attrs,
                R.styleable.CustomSeekBarPreference);
        String units = a.getString(R.styleable.CustomSeekBarPreference_units);
        a.recycle();
        if (units != null && !units.isEmpty()) mUnits = " " + units;

        setLayoutResource(R.layout.preference_custom_seekbar);
    }

    @Override
    protected void onSetInitialValue(Object defaultValue) {
        mValue = getPersistedInt(mDefault);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        mSeekBar = (SeekBar) holder.findViewById(R.id.custom_seekbar);
        mValueText = (TextView) holder.findViewById(R.id.custom_seekbar_value);

        mSeekBar.setMax(mMax - mMin);
        mSeekBar.setProgress(mValue - mMin);
        mSeekBar.setOnSeekBarChangeListener(this);
        updateValueText(mValue);
    }

    private void updateValueText(int value) {
        if (mValueText != null) {
            mValueText.setText(value + mUnits);
        }
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if (fromUser) {
            updateValueText(progress + mMin);
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {}

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        int newValue = seekBar.getProgress() + mMin;
        if (newValue != mValue && callChangeListener(Integer.valueOf(newValue))) {
            mValue = newValue;
            persistInt(newValue);
        } else {
            // visszaállítás, ha a listener elutasította
            seekBar.setProgress(mValue - mMin);
        }
        updateValueText(mValue);
    }

    public void setValue(int value) {
        mValue = Math.max(mMin, Math.min(mMax, value));
        persistInt(mValue);
        if (mSeekBar != null) mSeekBar.setProgress(mValue - mMin);
        updateValueText(mValue);
    }

    public int getValue() {
        return mValue;
    }
}
