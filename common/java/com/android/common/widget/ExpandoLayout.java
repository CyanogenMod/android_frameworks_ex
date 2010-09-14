/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.common.widget;

import android.os.Bundle;
import android.os.Debug;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.Parcelable.Creator;
import android.util.AttributeSet;
import android.util.Log;
import android.util.SparseArray;
import android.view.AbsSavedState;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.BaseSavedState;
import android.widget.LinearLayout;
import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.content.Context;

/***
 * ExpandoLayout is a ViewGroup which holds two views.  The "left view" is the view which
 * expands/contracts as needed.  The "right view" takes the remainder of the space (width)
 * of the ExpandoLayout and shifts right when the left view is expanded (this shift is
 * special because the right view retains it's width and doesn't squish down to fit
 * the newly reduced amount of space).
 *
 * The "left view" is the first child and the "right view" is the second child.  You must
 * have exactly two children.
 *
 * The left view's width is specified in dips.  This width is the collapsed width.
 * The ExpandoView object should contain an attribute called "expanded_layout_percentage" which
 * is a floating point number between 0 and 1.  When expanded, the left view expands to a width
 * which is this fraction of the ExpandLayout's width.  This can be controlled at runtime using
 * setParentPercentage().
 *
 * ExpandLayout's "start_expanded" (boolean) controls whether the initial state is expanded.
 * This defaults to false (contracted).
 *
 * The animation duration from collapsed to expanded and vice versa are controlled with these
 * attributes:
 *
 *    animation_expand_duration="250"
 *    animation_collapse_duration="250"
 *
 * In some cases you may want to override the left view in the collapsed state (i.e. to show
 * something other than a sliver of the normal left view).  To accomplish this make the left
 * view into a composite view (e.g. FrameLayout) containing two views.  The special view which
 * is to be shown when collapsed is referenced in the ExpandoLayout params as:
 *
 * collapsed_view="@+id/collapsed".
 *
 * ExpandoLayout will make that view visible/gone as needed.
 */
public class ExpandoLayout extends LinearLayout implements Animator.AnimatorListener {
    private boolean mExpandedState;
    private int mCollapsedWidth;
    private long mCollapseDuration;
    private long mExpandDuration;
    private float mExpandedPercentage;
    private View mLeftView;
    private View mRightView;

    // special views to be made visible/invisible depending on whether we're
    // collapsed/expanded.
    private View mExpandedView;
    private View mCollapsedView;
    private int mExpandedViewId = -1;
    private int mCollapsedViewId = -1;

    private LinearLayout.LayoutParams mLeftParams;
    private LinearLayout.LayoutParams mRightParams;
    private ObjectAnimator<ExpandoLayout> mAnimator;
    private PropertyValuesHolder<Integer> mAnimationValues;

    // for perf counting (fps)
    private int mAnimationCount;


    public ExpandoLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(attrs);
    }

    public ExpandoLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }

    public ExpandoLayout(Context context) {
        super(context);
        init(null);
    }

    private void init(AttributeSet attrs) {
        mExpandedPercentage = attrs.getAttributeFloatValue(null, "expanded_layout_percentage", .2F);
        mExpandDuration = attrs.getAttributeIntValue(null, "animation_expand_duration", 250);
        mCollapseDuration = attrs.getAttributeIntValue(null, "animation_collapse_duration", 250);
        mExpandedViewId = attrs.getAttributeResourceValue(null, "expanded_view", -1);
        mCollapsedViewId = attrs.getAttributeResourceValue(null, "collapsed_view", -1);
        setOrientation(LinearLayout.HORIZONTAL);
        mExpandedState = attrs.getAttributeBooleanValue(null, "start_expanded", false);

//      setSaveEnabled(true);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        if (getChildCount() != 2) {
            throw new IllegalArgumentException("ExpandoLayout must have exactly two children");
        }

        mLeftView = getChildAt(0);
        mRightView = getChildAt(1);

        mExpandedView = findViewById(mExpandedViewId);
        mCollapsedView = findViewById(mCollapsedViewId);
        if (mCollapsedView != null) {
            mCollapsedView.setOnClickListener(new OnClickListener() {
                public void onClick(View v) {
                    setExpanded(true);
                }
            });
        }

        onExpandStateChanged();

        // capture the left view's initial width as the collapsed width.  if we expand
        // and then collapse again we will restore to this value
        if (mCollapsedWidth == ViewGroup.LayoutParams.MATCH_PARENT ||
            mCollapsedWidth == ViewGroup.LayoutParams.WRAP_CONTENT) {
            throw new IllegalArgumentException("the first view of an ExpandoLayout must use a dip width (not WRAP_CONTENT or MATCH_PARENT)");
        }
        mCollapsedWidth = mLeftView.getLayoutParams().width;
        mLeftParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0F);
        mRightParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0F);
        setLayoutWidth(mCollapsedWidth);
    }

    private void setLayoutWidth(int width) {
        mLeftParams.width = width;
        mLeftView.setLayoutParams(mLeftParams);

        mRightParams.width = getMeasuredWidth() - mCollapsedWidth;
        mRightView.setLayoutParams(mRightParams);
    }

    private int computeWidthTarget(int myWidth) {
        return mExpandedState
            ? (int)(myWidth * mExpandedPercentage)
            : mCollapsedWidth;
    }

    private int computeWidthTarget() {
        return computeWidthTarget(getMeasuredWidth());
    }

    public void setWidthTarget(int newWidth) {
        setLayoutWidth(newWidth);
        mAnimationCount++;
        requestLayout();
    }

    /*
     * Override the expanded_layout_percentage attribute.  When "expanded", the "left view"
     * will get a width of the ExpandoLayout's width multiplied times the percentage parameter
     * pass here.
     */
    public void setExpandedWithPercentage(float percentage, long animationDuration) {
        if (percentage < 0F || percentage > 1F) {
            throw new IllegalArgumentException("percentage must be between 0 and 1");
        }
        mExpandedPercentage = percentage;
        if (mExpandedState) {
            animate(animationDuration, mLeftView.getMeasuredWidth(), computeWidthTarget());
        }
        requestLayout();
    }

    public float getExpandedWidthPercentage() {
        return mExpandedPercentage;
    }

    private void animate(long duration, int current, int target) {
        if (mAnimator == null) {
            mAnimationValues = new PropertyValuesHolder<Integer>("widthTarget", current, target);
            mAnimator = new ObjectAnimator<ExpandoLayout>(0, this, mAnimationValues);
            mAnimator.addListener(this);
        }
        mAnimator.setDuration(duration);
        mAnimationValues.setValues(current, target);

        mAnimationCount = 0;
        mAnimator.start();
    }

    public void setExpanded(boolean expanded) {
        setExpanded(expanded, true);
    }

    public void setExpanded(boolean expanded, boolean animate) {
        if (mExpandedState == expanded) {
            return;
        }
        mExpandedState = expanded;

        if (animate) {
            long animationDuration = expanded ? mExpandDuration : mCollapseDuration;
            animate(animationDuration, mLeftView.getMeasuredWidth(), computeWidthTarget());
        } else {
            setWidthTarget(computeWidthTarget());
        }
    }

    public boolean isExpanded() {
        return mExpandedState;
    }

    @Override
    public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int myWidth = View.MeasureSpec.getSize(widthMeasureSpec);
        setLayoutWidth(computeWidthTarget(myWidth));
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    public void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
    }

    private void onExpandStateChanged() {
        if (mExpandedView != null) {
            mExpandedView.setVisibility(mExpandedState ? View.VISIBLE : View.GONE);
        }
        if (mCollapsedView != null) {
            mCollapsedView.setVisibility(mExpandedState ? View.GONE : View.VISIBLE);
        }
    }

    public void onAnimationCancel(Animator animation) {
        // TODO something?
    }

    public void onAnimationEnd(Animator animation) {
//      logV("animation frames count: " + mAnimationCount + " in " + mAnimator.getDuration());
        onExpandStateChanged();
    }

    public void onAnimationRepeat(Animator animation) {
    }

    public void onAnimationStart(Animator animation) {
        mAnimationCount = 0;
    }

    private static class SavedState extends BaseSavedState {
        private final boolean mExpanded;
        private final float mPercentage;

        private SavedState(Parcelable superState, boolean expanded, float percentage) {
            super(superState);
            mExpanded = expanded;
            mPercentage = percentage;
        }

        private SavedState(Parcel in) {
            super(in);
            mExpanded = in.readInt() == 0;
            mPercentage = in.readFloat();
        }

        public boolean getExpanded() {
            return mExpanded;
        }

        public float getPercentage() {
            return mPercentage;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeInt(mExpanded ? 1 : 0);
            dest.writeFloat(mPercentage);
        }

        public static final Parcelable.Creator<SavedState> CREATOR
                = new Creator<SavedState>() {
            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in);
            }

            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
    }

    /*
    @Override
    public Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();
        SavedState savedState = new SavedState(superState, mExpandedState, mExpandedPercentage);
        return savedState;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        SavedState savedState = (SavedState) state;
        super.onRestoreInstanceState(savedState.getSuperState());
        logV("onRestoreInstanceState: " + savedState.getExpanded() + " " + savedState.getPercentage());
    }
    */

    private void logV(String msg) {
        Log.v("expando", msg);
    }
}
