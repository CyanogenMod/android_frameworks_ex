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

/**
 * RecipientChip defines a drawable object that contains information relevant to a
 * particular recipient.
 */
/* package */interface RecipientChip {

    /**
     * Set the selected state of the chip.
     * @param selected
     */
    public void setSelected(boolean selected);
    /**
     * Return true if the chip is selected.
     */
    public boolean isSelected();

    /**
     * Get the text displayed in the chip.
     */
    public CharSequence getDisplay();

    /**
     * Get the text value this chip represents.
     */
    public CharSequence getValue();

    /**
     * Get the id of the contact associated with this chip.
     */
    public long getContactId();

    /**
     * Get the id of the data associated with this chip.
     */
    public long getDataId();

    /**
     * Get associated RecipientEntry.
     */
    public RecipientEntry getEntry();

    /**
     * Set the text in the edittextview originally associated with this chip
     * before any reverse lookups.
     */
    public void setOriginalText(String text);

    /**
     * Set the text in the edittextview originally associated with this chip
     * before any reverse lookups.
     */
    public CharSequence getOriginalText();

    /**
     * Get the bounds of the chip; may be 0,0 if it is not visibly rendered.
     */
    public Rect getBounds();

    /**
     * Draw the chip.
     */
    public void draw(Canvas canvas);
}
