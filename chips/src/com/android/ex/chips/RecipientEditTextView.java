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
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.text.Editable;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.method.QwertyKeyListener;
import android.text.style.ImageSpan;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Filter.FilterListener;
import android.widget.PopupWindow.OnDismissListener;
import android.widget.ListPopupWindow;
import android.widget.MultiAutoCompleteTextView;

/**
 * RecipientEditTextView is an auto complete text view for use with applications
 * that use the new Chips UI for addressing a message to recipients.
 */
public class RecipientEditTextView extends MultiAutoCompleteTextView
    implements OnItemClickListener {

    private static final String TAG = "RecipientEditTextView";

    private Drawable mChipBackground = null;

    private int mChipPadding;

    private Tokenizer mTokenizer;

    private final Handler mHandler;

    private Runnable mDelayedSelectionMode = new Runnable() {
        @Override
        public void run() {
            setSelection(getText().length());
        }
    };

    public RecipientEditTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mHandler = new Handler();
        setOnItemClickListener(this);
    }

    public RecipientChip constructChipSpan(RecipientEntry contact, int offset, boolean pressed)
        throws NullPointerException {
        if (mChipBackground == null) {
            throw new NullPointerException
                ("Unable to render any chips as setChipDimensions was not called.");
        }
        String text = contact.getDisplayName();
        Layout layout = getLayout();
        int line = layout.getLineForOffset(offset);
        int lineTop = layout.getLineTop(line);

        TextPaint paint = getPaint();
        float defaultSize = paint.getTextSize();

        // Reduce the size of the text slightly so that we can get the "look" of
        // padding.
        paint.setTextSize((float) (paint.getTextSize() * .9));

        // Ellipsize the text so that it takes AT MOST the entire width of the
        // autocomplete text entry area. Make sure to leave space for padding
        // on the sides.
        CharSequence ellipsizedText = TextUtils.ellipsize(text, paint, calculateAvailableWidth(),
                TextUtils.TruncateAt.END);

        int height = getLineHeight();
        int width = (int) Math.floor(paint.measureText(ellipsizedText, 0, ellipsizedText.length()))
                + (mChipPadding * 2);

        // Create the background of the chip.
        Bitmap tmpBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(tmpBitmap);
        if (mChipBackground != null) {
            mChipBackground.setBounds(0, 0, width, height);
            mChipBackground.draw(canvas);
        } else {
            Log.w(TAG,
                    "Unable to draw a background for the chips as it was never set");
        }

        // Align the display text with where the user enters text.
        canvas.drawText(ellipsizedText, 0, ellipsizedText.length(), mChipPadding, height
                - layout.getLineDescent(line), paint);

        // Get the location of the widget so we can properly offset
        // the anchor for each chip.
        int[] xy = new int[2];
        getLocationOnScreen(xy);
        // Pass the full text, un-ellipsized, to the chip.
        Drawable result = new BitmapDrawable(getResources(), tmpBitmap);
        result.setBounds(0, 0, width, height);
        Rect bounds = new Rect(xy[0] + offset, xy[1] + lineTop, xy[0] + width,
                calculateLineBottom(xy[1], line));
        RecipientChip recipientChip = new RecipientChip(result, contact, offset, bounds);

        // Return text to the original size.
        paint.setTextSize(defaultSize);

        return recipientChip;
    }

    // The bottom of the line the chip will be located on is calculated by 4 factors:
    // 1) which line the chip appears on
    // 2) the height of a line in the autocomplete view
    // 3) padding built into the edit text view will move the bottom position
    // 4) the position of the autocomplete view on the screen, taking into account
    // that any top padding will move this down visually
    private int calculateLineBottom(int yOffset, int line) {
        int bottomPadding = 0;
        if (line == getLineCount() - 1) {
            bottomPadding += getPaddingBottom();
        }
        return ((line + 1) * getLineHeight()) + (yOffset + getPaddingTop()) + bottomPadding;
    }

    // Get the max amount of space a chip can take up. The formula takes into
    // account the width of the EditTextView, any view padding, and padding
    // that will be added to the chip.
    private float calculateAvailableWidth() {
        return getWidth() - getPaddingLeft() - getPaddingRight() - (mChipPadding * 2);
    }

    /**
     * Set all chip dimensions and resources. This has to be done from the application
     * as this is a static library.
     * @param chipBackground drawable
     * @param padding Padding around the text in a chip
     * @param offset Offset between the chip and the dropdown of alternates
     */
    public void setChipDimensions(Drawable chipBackground, float padding) {
        mChipBackground = chipBackground;
        mChipPadding = (int) padding;
    }

    @Override
    public void setTokenizer(Tokenizer tokenizer) {
        mTokenizer = tokenizer;
        super.setTokenizer(mTokenizer);
    }

    // We want to handle replacing text in the onItemClickListener
    // so we can get all the associated contact information including
    // display text, address, and id.
    @Override
    protected void replaceText(CharSequence text) {
        return;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_TAB:
                if (event.hasNoModifiers()) {
                    if (isPopupShowing()) {
                        // choose the first entry.
                        submitItemAtPosition(0);
                        dismissDropDown();
                        return true;
                    } else {
                        int end = getSelectionEnd();
                        int start = mTokenizer.findTokenStart(getText(), end);
                        String text = getText().toString().substring(start, end);
                        clearComposingText();

                        Editable editable = getText();
                        RecipientEntry entry = RecipientEntry.constructFakeEntry(text);
                        QwertyKeyListener.markAsReplaced(editable, start, end, "");
                        editable.replace(start, end, createChip(entry));
                        dismissDropDown();
                    }
                }
        }
        return super.onKeyUp(keyCode, event);
    }

    public void onChipChanged() {
        // Must be posted so that the previous span
        // is correctly replaced with the previous selection points.
        mHandler.post(mDelayedSelectionMode);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        int start = getSelectionStart();
        int end = getSelectionEnd();
        Spannable span = getSpannable();

        RecipientChip[] chips = span.getSpans(start, end, RecipientChip.class);
        if (chips != null) {
            for (RecipientChip chip : chips) {
                chip.onKeyDown(keyCode, event);
            }
        }

        if (keyCode == KeyEvent.KEYCODE_ENTER && event.hasNoModifiers()) {
            return true;
        }

        return super.onKeyDown(keyCode, event);
    }

    private Spannable getSpannable() {
        return (Spannable) getText();
    }


    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getAction();
        boolean handled = super.onTouchEvent(event);
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_DOWN) {
            Spannable span = getSpannable();
            int offset = getOffsetForPosition(event.getX(), event.getY());
            int start = offset;
            int end = span.length();
            RecipientChip[] chips = span.getSpans(start, end, RecipientChip.class);
            if (chips != null && chips.length > 0) {
                // Get the first chip that matched.
                final RecipientChip currentChip = chips[0];

                if (action == MotionEvent.ACTION_UP) {
                    currentChip.onClick(this);
                } else if (action == MotionEvent.ACTION_DOWN) {

                }
            }
        }
        return handled;
    }

    private CharSequence createChip(RecipientEntry entry) {
        // We want to override the tokenizer behavior with our own ending
        // token, space.
        SpannableString chipText = new SpannableString(mTokenizer.terminateToken(entry
                .getDisplayName()));
        int end = getSelectionEnd();
        int start = mTokenizer.findTokenStart(getText(), end);
        try {
            chipText.setSpan(constructChipSpan(entry, start, false), 0, entry.getDisplayName()
                    .length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        } catch (NullPointerException e) {
            Log.e(TAG, e.getMessage());
            return null;
        }

        return chipText;
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        submitItemAtPosition(position);
    }

    private void submitItemAtPosition(int position) {
        RecipientEntry entry = (RecipientEntry) getAdapter().getItem(position);
        clearComposingText();

        int end = getSelectionEnd();
        int start = mTokenizer.findTokenStart(getText(), end);

        Editable editable = getText();
        editable.replace(start, end, createChip(entry));
        QwertyKeyListener.markAsReplaced(editable, start, end, "");
    }

    /**
     * RecipientChip defines an ImageSpan that contains information relevant to
     * a particular recipient.
     */
    public class RecipientChip extends ImageSpan implements OnItemClickListener, OnDismissListener,
            FilterListener {
        private final CharSequence mDisplay;

        private final CharSequence mValue;

        private final int mOffset;

        private ListPopupWindow mPopup;

        private View mAnchorView;

        private int mLeft;

        private int mId = -1;

        public RecipientChip(Drawable drawable, RecipientEntry entry, int offset, Rect bounds) {
            super(drawable);
            mDisplay = entry.getDisplayName();
            mValue = entry.getDestination();
            mId = entry.getContactId();
            mOffset = offset;

            mAnchorView = new View(getContext());
            mAnchorView.setLeft(bounds.left);
            mAnchorView.setRight(bounds.left);
            mAnchorView.setTop(bounds.bottom);
            mAnchorView.setBottom(bounds.bottom);
            mAnchorView.setVisibility(View.GONE);
        }

        public void onKeyDown(int keyCode, KeyEvent event) {
            if (keyCode == KeyEvent.KEYCODE_DEL) {
                if (mPopup != null && mPopup.isShowing()) {
                    mPopup.dismiss();
                }
                removeChip();
            }
        }

        public boolean isCompletedContact() {
            return mId != -1;
        }

        private void replace(RecipientChip newChip) {
            Spannable spannable = getSpannable();
            int spanStart = getChipStart();
            int spanEnd = getChipEnd();
            QwertyKeyListener.markAsReplaced(getText(), spanStart, spanEnd, "");
            spannable.removeSpan(this);
            spannable.setSpan(newChip, spanStart, spanEnd, 0);
        }

        public void removeChip() {
            Spannable spannable = getSpannable();
            int spanStart = getChipStart();
            int spanEnd = getChipEnd();
            QwertyKeyListener.markAsReplaced(getText(), spanStart, spanEnd, "");
            spannable.removeSpan(this);
            getText().delete(spanStart, spanEnd);
        }

        public int getChipStart() {
            return getSpannable().getSpanStart(this);
        }

        public int getChipEnd() {
            return getSpannable().getSpanEnd(this);
        }

        public void replaceChip(RecipientEntry entry) {
            clearComposingText();

            RecipientChip newChipSpan = null;
            try {
                newChipSpan = constructChipSpan(entry, mOffset, false);
            } catch (NullPointerException e) {
                Log.e(TAG, e.getMessage());
                return;
            }
            replace(newChipSpan);
            if (mPopup != null && mPopup.isShowing()) {
                mPopup.dismiss();
            }
            onChipChanged();
        }

        public CharSequence getDisplay() {
            return mDisplay;
        }

        public CharSequence getValue() {
            return mValue;
        }

        public void onClick(View widget) {
            if (isCompletedContact()) {
                mPopup = new ListPopupWindow(widget.getContext());

                if (!mPopup.isShowing()) {
                    mAnchorView.setLeft(mLeft);
                    mAnchorView.setRight(mLeft);
                    mPopup.setAnchorView(mAnchorView);
                    BaseRecipientAdapter adapter = (BaseRecipientAdapter)getAdapter();
                    adapter.getFilter().filter(getValue(), this);
                    mPopup.setAdapter(adapter);
                    // TODO: get width from dimen.xml.
                    mPopup.setWidth(getWidth());
                    mPopup.setOnItemClickListener(this);
                    mPopup.setOnDismissListener(this);
                }
            } else {
                CharSequence text = getValue();
                removeChip();
                Editable editable = getText();
                setSelection(editable.length());
                editable.append(text);
            }
        }

        @Override
        public void draw(Canvas canvas, CharSequence text, int start, int end, float x, int top,
                int y, int bottom, Paint paint) {
            mLeft = (int) x;
            super.draw(canvas, text, start, end, x, top, y, bottom, paint);
        }

        @Override
        public void onItemClick(AdapterView<?> adapterView, View view, int position, long rowId) {
            mPopup.dismiss();
            clearComposingText();
            replaceChip((RecipientEntry) adapterView.getItemAtPosition(position));
        }

        // When the popup dialog is dismissed, return the cursor to the end.
        @Override
        public void onDismiss() {
            mHandler.post(mDelayedSelectionMode);
        }

        @Override
        public void onFilterComplete(int count) {
            if (count > 0 && mPopup != null) {
                mPopup.show();
            }
        }
    }
}
