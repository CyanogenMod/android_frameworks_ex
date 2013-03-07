/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.ex.chips;

import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.text.style.DynamicDrawableSpan;
import android.text.style.ImageSpan;

/**
 * VisibleRecipientChip defines an ImageSpan that contains information relevant to a
 * particular recipient and renders a background asset to go with it.
 */
/* package */class VisibleRecipientChip extends ImageSpan implements RecipientChip {
    private final CharSequence mDisplay;

    private final CharSequence mValue;

    private final long mContactId;

    private final long mDataId;

    private RecipientEntry mEntry;

    private boolean mSelected = false;

    private CharSequence mOriginalText;

    public VisibleRecipientChip(Drawable drawable, RecipientEntry entry) {
        super(drawable, DynamicDrawableSpan.ALIGN_BOTTOM);
        mDisplay = entry.getDisplayName();
        mValue = entry.getDestination().trim();
        mContactId = entry.getContactId();
        mDataId = entry.getDataId();
        mEntry = entry;
    }

    /**
     * Set the selected state of the chip.
     * @param selected
     */
    @Override
    public void setSelected(boolean selected) {
        mSelected = selected;
    }

    /**
     * Return true if the chip is selected.
     */
    @Override
    public boolean isSelected() {
        return mSelected;
    }

    /**
     * Get the text displayed in the chip.
     */
    @Override
    public CharSequence getDisplay() {
        return mDisplay;
    }

    /**
     * Get the text value this chip represents.
     */
    @Override
    public CharSequence getValue() {
        return mValue;
    }

    /**
     * Get the id of the contact associated with this chip.
     */
    @Override
    public long getContactId() {
        return mContactId;
    }

    /**
     * Get the id of the data associated with this chip.
     */
    @Override
    public long getDataId() {
        return mDataId;
    }

    /**
     * Get associated RecipientEntry.
     */
    @Override
    public RecipientEntry getEntry() {
        return mEntry;
    }

    @Override
    public void setOriginalText(String text) {
        if (TextUtils.isEmpty(text)) {
            mOriginalText = text;
        } else {
            mOriginalText = text.trim();
        }
    }

    @Override
    public CharSequence getOriginalText() {
        return !TextUtils.isEmpty(mOriginalText) ? mOriginalText : mEntry.getDestination();
    }

    @Override
    public Rect getBounds() {
        return getDrawable().getBounds();
    }

    @Override
    public void draw(Canvas canvas) {
        getDrawable().draw(canvas);
    }

    @Override
    public String toString() {
        return mDisplay + " <" + mValue + ">";
    }
}
