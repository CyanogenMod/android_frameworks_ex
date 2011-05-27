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
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.text.Editable;
import android.text.Layout;
import android.text.Selection;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.method.QwertyKeyListener;
import android.text.style.ImageSpan;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.PopupWindow.OnDismissListener;
import android.widget.ListPopupWindow;
import android.widget.ListView;
import android.widget.MultiAutoCompleteTextView;

/**
 * RecipientEditTextView is an auto complete text view for use with applications
 * that use the new Chips UI for addressing a message to recipients.
 */
public class RecipientEditTextView extends MultiAutoCompleteTextView
    implements OnItemClickListener {

    private static final int DEFAULT_CHIP_BACKGROUND = 0x77CCCCCC;

    private static final int CHIP_PADDING = 10;

    public static String CHIP_BACKGROUND = "chipBackground";

    // TODO: eliminate this and take the pressed state from the provided
    // drawable.
    public static String CHIP_BACKGROUND_PRESSED = "chipBackgroundPressed";

    private Drawable mChipBackground = null;

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

    public RecipientChip constructChipSpan(CharSequence text, int offset, boolean pressed) {
        Layout layout = getLayout();
        int line = layout.getLineForOffset(offset);
        int lineTop = layout.getLineTop(line);
        int lineBottom = layout.getLineBottom(line);

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
                + (CHIP_PADDING * 2);

        // Create the background of the chip.
        Bitmap tmpBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(tmpBitmap);
        if (mChipBackground != null) {
            mChipBackground.setBounds(0, 0, width, height);
            mChipBackground.draw(canvas);
        } else {
            ColorDrawable color = new ColorDrawable(DEFAULT_CHIP_BACKGROUND);
            color.setBounds(0, 0, width, height);
            color.draw(canvas);
        }

        // Align the display text with where the user enters text.
        canvas.drawText(ellipsizedText, 0, ellipsizedText.length(), CHIP_PADDING, height
                - layout.getLineDescent(line), paint);

        // Get the location of the widget so we can properly offset
        // the anchor for each chip.
        int[] xy = new int[2];
        getLocationOnScreen(xy);
        // Pass the full text, un-ellipsized, to the chip.
        Drawable result = new BitmapDrawable(getResources(), tmpBitmap);
        result.setBounds(0, 0, width, height);
        Rect bounds = new Rect(xy[0] + offset, xy[1] + lineTop, xy[0] + width, xy[1] + lineBottom);
        RecipientChip recipientChip = new RecipientChip(result, text, text, -1, offset, bounds);

        // Return text to the original size.
        paint.setTextSize(defaultSize);

        return recipientChip;
    }

    // Get the max amount of space a chip can take up. The formula takes into
    // account the width of the EditTextView, any view padding, and padding
    // that will be added to the chip.
    private float calculateAvailableWidth() {
        return getWidth() - getPaddingLeft() - getPaddingRight() - (CHIP_PADDING * 2);
    }

    public void setChipBackgroundDrawable(Drawable d) {
        mChipBackground = d;
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

    // TODO: this should be handled by the framework directly; working with
    // @debunne to figure out why it isn't being handled properly.
    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_TAB:
                if (event.hasNoModifiers()) {
                    if (getListSelection() != ListView.INVALID_POSITION) {
                        performCompletion();
                        return true;
                    } else {
                        int end = getSelectionEnd();
                        int start = mTokenizer.findTokenStart(getText(), end);
                        String text = getText().toString().substring(start, end);
                        clearComposingText();

                        Editable editable = getText();

                        QwertyKeyListener.markAsReplaced(editable, start, end, "");
                        editable.replace(start, end, createChip(text));
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

    public Spannable getSpannable() {
        return (Spannable) getText();
    }

    /**
     * RecipientChip defines an ImageSpan that contains information relevant to
     * a particular recipient.
     */
    public class RecipientChip extends ImageSpan implements OnItemClickListener, OnDismissListener {
        private final CharSequence mDisplay;

        private final CharSequence mValue;

        private final int mOffset;

        private ListPopupWindow mPopup;

        private View mAnchorView;

        private int mLeft;

        private int mId = -1;

        public RecipientChip(Drawable drawable, CharSequence text, CharSequence value, int id,
                int offset, Rect bounds) {
            super(drawable);
            mDisplay = text;
            mValue = value;
            mOffset = offset;
            mAnchorView = new View(getContext());
            mAnchorView.setLeft(bounds.left);
            mAnchorView.setRight(bounds.left);
            mAnchorView.setTop(bounds.right + CHIP_PADDING);
            mAnchorView.setBottom(bounds.right + CHIP_PADDING);
            mAnchorView.setVisibility(View.GONE);

            mId = id;
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
            spannable.setSpan(null, spanStart, spanEnd, 0);
            onChipChanged();
        }

        public int getChipStart() {
            return getSpannable().getSpanStart(this);
        }

        public int getChipEnd() {
            return getSpannable().getSpanEnd(this);
        }

        public void replaceChip(String text) {
            clearComposingText();

            RecipientChip newChipSpan = constructChipSpan(text, mOffset, false);
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
            mPopup = new ListPopupWindow(widget.getContext());

            if (!mPopup.isShowing()) {
                mAnchorView.setLeft(mLeft);
                mAnchorView.setRight(mLeft);
                mPopup.setAnchorView(mAnchorView);
                mPopup.setAdapter(getAdapter());
                // TODO: get width from dimen.xml.
                mPopup.setWidth(200);
                mPopup.setOnItemClickListener(this);
                mPopup.setOnDismissListener(this);
                mPopup.show();
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
            RecipientListEntry entry = (RecipientListEntry) adapterView.getItemAtPosition(position);
            replaceChip(entry.getDisplayName());
        }

        // When the popup dialog is dismissed, return the cursor to the end.
        @Override
        public void onDismiss() {
            mHandler.post(mDelayedSelectionMode);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getAction();
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
                    Selection.setSelection(getSpannable(), currentChip.getChipStart(), currentChip
                            .getChipEnd());
                }
                return true;
            }
        }

        return super.onTouchEvent(event);
    }

    private CharSequence createChip(String text) {
        // We want to override the tokenizer behavior with our own ending
        // token, space.
        SpannableString chipText = new SpannableString(mTokenizer.terminateToken(text));
        int end = getSelectionEnd();
        int start = mTokenizer.findTokenStart(getText(), end);
        chipText.setSpan(constructChipSpan(text, start, false), 0, text.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return chipText;
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        // Figure out what got clicked!
        RecipientListEntry entry = (RecipientListEntry) parent.getItemAtPosition(position);
        clearComposingText();

        int end = getSelectionEnd();
        int start = mTokenizer.findTokenStart(getText(), end);

        Editable editable = getText();
        editable.replace(start, end, createChip(entry.getDisplayName()));
        QwertyKeyListener.markAsReplaced(editable, start, end, "");
    }
}
