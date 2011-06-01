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

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.util.AttributeSet;
import android.widget.Filterable;
import android.widget.FrameLayout;
import android.widget.ListAdapter;
import android.widget.MultiAutoCompleteTextView;

import java.util.Collection;

/**
 * RecipientEditTextView is an auto complete text view for use with applications
 * that use the new Chips UI for addressing a message to recipients.
 */
public class RecipientEditTextView extends FrameLayout {
    private RecipientEditTextViewInner mEditTextViewInner;

    public RecipientEditTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mEditTextViewInner = new RecipientEditTextViewInner(context, attrs);
        addView(mEditTextViewInner);
    }

    Collection<Long> getContactIds() {
        return mEditTextViewInner.getContactIds();
    }

    Collection<Long> getDataIds() {
        return mEditTextViewInner.getDataIds();
    }

    public Editable getText() {
        return mEditTextViewInner.getRecipients();
    }

    public <T extends ListAdapter & Filterable> void setAdapter(T adapter) {
        mEditTextViewInner.setAdapter(adapter);
    }

    public void setChipDimensions(Drawable chipBackground, Drawable chipBackgroundPressed,
            Drawable chipDelete, float padding) {
        mEditTextViewInner.setChipDimensions(chipBackground, chipBackgroundPressed, chipDelete,
                padding);
    }

    public void setTokenizer(MultiAutoCompleteTextView.Tokenizer tokenizer) {
        mEditTextViewInner.setTokenizer(tokenizer);
    }
}
