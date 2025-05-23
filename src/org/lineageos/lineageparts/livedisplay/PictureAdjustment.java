/*
 * SPDX-FileCopyrightText: 2016 The CyanogenMod Project
 * SPDX-FileCopyrightText: 2021-2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.lineageparts.livedisplay;

import android.content.Context;
import android.content.DialogInterface;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.Range;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import lineageos.hardware.HSIC;
import lineageos.hardware.LiveDisplayManager;

import org.lineageos.lineageparts.R;
import org.lineageos.lineageparts.widget.CustomDialogPreference;
import org.lineageos.lineageparts.widget.IntervalSeekBar;

import java.util.List;

/**
 * Special preference type that allows configuration of Color settings
 */
public class PictureAdjustment extends CustomDialogPreference<AlertDialog> {
    private static final String TAG = "PictureAdjustment";

    private final LiveDisplayManager mLiveDisplay;
    private final List<Range<Float>> mRanges;

    // These arrays must all match in length and order
    private static final int[] SEEKBAR_ID = new int[] {
        R.id.adj_hue_seekbar,
        R.id.adj_saturation_seekbar,
        R.id.adj_intensity_seekbar,
        R.id.adj_contrast_seekbar
    };

    private static final int[] SEEKBAR_VALUE_ID = new int[] {
        R.id.adj_hue_value,
        R.id.adj_saturation_value,
        R.id.adj_intensity_value,
        R.id.adj_contrast_value
    };

    private final ColorSeekBar[] mSeekBars = new ColorSeekBar[SEEKBAR_ID.length];

    private final float[] mCurrentAdj = new float[5];
    private final float[] mOriginalAdj = new float[5];

    public PictureAdjustment(Context context, AttributeSet attrs) {
        super(context, attrs);

        mLiveDisplay = LiveDisplayManager.getInstance(context);
        mRanges = mLiveDisplay.getConfig().getPictureAdjustmentRanges();

        setDialogLayoutResource(R.layout.display_picture_adjustment);
    }

    @Override
    protected void onPrepareDialogBuilder(AlertDialog.Builder builder,
            DialogInterface.OnClickListener listener) {
        super.onPrepareDialogBuilder(builder, listener);

        builder.setNeutralButton(R.string.reset, null);
        builder.setNegativeButton(R.string.cancel, null);
        builder.setPositiveButton(R.string.dlg_ok, null);
    }

    private void updateBars() {
        for (int i = 0; i < SEEKBAR_ID.length; i++) {
            mSeekBars[i].setValue(mCurrentAdj[i]);
        }
    }

    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);

        System.arraycopy(mLiveDisplay.getPictureAdjustment().toFloatArray(), 0, mOriginalAdj, 0, 5);
        System.arraycopy(mOriginalAdj, 0, mCurrentAdj, 0, 5);

        for (int i = 0; i < SEEKBAR_ID.length; i++) {
            IntervalSeekBar seekBar = view.findViewById(SEEKBAR_ID[i]);
            TextView value = view.findViewById(SEEKBAR_VALUE_ID[i]);
            final Range<Float> range = mRanges.get(i);
            mSeekBars[i] = new ColorSeekBar(seekBar, range, value, i);
        }
        updateBars();
    }

    @Override
    protected boolean onDismissDialog(AlertDialog dialog, int which) {
        // Can't use onPrepareDialogBuilder for this as we want the dialog
        // to be kept open on click
        if (which == DialogInterface.BUTTON_NEUTRAL) {
            System.arraycopy(mLiveDisplay.getDefaultPictureAdjustment().toFloatArray(),
                    0, mCurrentAdj, 0, 5);
            updateBars();
            updateAdjustment(mCurrentAdj);
            return false;
        }
        return true;
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);
        updateAdjustment(positiveResult ? mCurrentAdj : mOriginalAdj);
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        final Parcelable superState = super.onSaveInstanceState();
        if (getDialog() == null || !getDialog().isShowing()) {
            return superState;
        }

        // Save the dialog state
        final SavedState myState = new SavedState(superState);
        myState.currentAdj = mCurrentAdj;
        myState.originalAdj = mOriginalAdj;

        // Restore the old state when the activity or dialog is being paused
        updateAdjustment(mOriginalAdj);

        return myState;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if (state == null || !state.getClass().equals(SavedState.class)) {
            // Didn't save state for us in onSaveInstanceState
            super.onRestoreInstanceState(state);
            return;
        }

        SavedState myState = (SavedState) state;
        super.onRestoreInstanceState(myState.getSuperState());

        System.arraycopy(myState.originalAdj, 0, mOriginalAdj, 0, 5);
        System.arraycopy(myState.currentAdj, 0, mCurrentAdj, 0, 5);

        updateBars();
        updateAdjustment(mCurrentAdj);
    }

    private static class SavedState extends BaseSavedState {
        float[] originalAdj;
        float[] currentAdj;

        public SavedState(Parcelable superState) {
            super(superState);
        }

        public SavedState(Parcel source) {
            super(source);
            originalAdj = source.createFloatArray();
            currentAdj = source.createFloatArray();
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeFloatArray(originalAdj);
            dest.writeFloatArray(currentAdj);
        }

        public static final Creator<SavedState> CREATOR = new Creator<>() {
            public PictureAdjustment.SavedState createFromParcel(Parcel in) {
                return new PictureAdjustment.SavedState(in);
            }

            public PictureAdjustment.SavedState[] newArray(int size) {
                return new PictureAdjustment.SavedState[size];
            }
        };
    }

    private void updateAdjustment(final float[] adjustment) {
        mLiveDisplay.setPictureAdjustment(HSIC.fromFloatArray(adjustment));
    }

    private class ColorSeekBar implements SeekBar.OnSeekBarChangeListener {
        private final int mIndex;
        private final IntervalSeekBar mSeekBar;
        private final TextView mValue;
        private final Range<Float> mRange;

        public ColorSeekBar(IntervalSeekBar seekBar, Range<Float> range, TextView value,
                            int index) {
            mSeekBar = seekBar;
            mValue = value;
            mIndex = index;
            mRange = range;
            mSeekBar.setMinimum(range.getLower());
            mSeekBar.setMaximum(range.getUpper());

            mSeekBar.setOnSeekBarChangeListener(this);
        }

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            IntervalSeekBar isb = (IntervalSeekBar)seekBar;
            float fp = isb.getProgressFloat();
            if (fromUser) {
                mCurrentAdj[mIndex] = mRanges.get(mIndex).clamp(fp);
                updateAdjustment(mCurrentAdj);
            }
            mValue.setText(getLabel(mCurrentAdj[mIndex]));
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            // Do nothing here
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            // Do nothing here
        }

        private String getLabel(float value) {
            if (mRange.getUpper() == 1.0f) {
                return String.format("%d%%", Math.round(100F * value));
            }
            return String.format("%d", Math.round(value));
        }

        public void setValue(float value) {
            mSeekBar.setProgressFloat(value);
            mValue.setText(getLabel(value));
        }
    }
}
