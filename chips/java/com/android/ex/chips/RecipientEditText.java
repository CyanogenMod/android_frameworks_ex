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
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.ImageSpan;
import android.text.util.Rfc822Token;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.widget.MultiAutoCompleteTextView;
import android.widget.TextView;

public class RecipientEditText extends MultiAutoCompleteTextView {
    private static final String TAG = "RecipientEditText";

    public RecipientEditText(Context context) {
        super(context);
    }

    public RecipientEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public RecipientEditText(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        boolean superResult = super.onTouchEvent(event);
        final int action = event.getActionMasked();
        int offset = getOffset((int)event.getX(), (int)event.getY());
        int lineTop = getLayout().getLineTop(getLineAtCoordinate((int)event.getY()));
        int lineBottom = getLayout().getLineBottom(getLineAtCoordinate((int)event.getY()));

        CharSequence text = getText();
        if ((text instanceof Spannable)) {
            Spannable spannable = (Spannable) text;
            ChipSpan[] chips = spannable.getSpans(offset, offset, ChipSpan.class);
            int chipsCount = chips.length;
            if (chipsCount > 0) {
                if (chipsCount > 1) {
                    Log.d(TAG, "chips too many: " + chipsCount);
                }
                ChipSpan chip = chips[0];

                int spanStart = spannable.getSpanStart(chip);
                int spanEnd = spannable.getSpanEnd(chip);
                CharSequence chipText = chip.getText();
                spannable.removeSpan(chip);

                TextPaint paint = getPaint();
                int width = (int) Math.floor(paint.measureText(text, 0, text.length()));
                int height = lineBottom - lineTop;
                float ascent = getLayout().getLineAscent(getLineAtCoordinate((int)event.getY()));

                if (action == MotionEvent.ACTION_DOWN) {
                    spannable.setSpan(constructChipSpan(this, chipText, true),
                            spanStart, spanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                } else {
                    spannable.setSpan(constructChipSpan(this, chipText, false),
                            spanStart, spanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                setCursorVisible(false);
            } else {
                setCursorVisible(true);
            }
        }
        return superResult;
    }

    /* copied from TextView. TextView#getOffset() is hidden */

    public int getOffset(int x, int y) {
        if (getLayout() == null) return -1;
        final int line = getLineAtCoordinate(y);
        final int offset = getOffsetAtCoordinate(line, x);
        return offset;
    }

    private int convertToLocalHorizontalCoordinate(int x) {
        x -= getTotalPaddingLeft();
        // Clamp the position to inside of the view.
        x = Math.max(0, x);
        x = Math.min(getWidth() - getTotalPaddingRight() - 1, x);
        x += getScrollX();
        return x;
    }

    private int getLineAtCoordinate(int y) {
        y -= getTotalPaddingTop();
        // Clamp the position to inside of the view.
        y = Math.max(0, y);
        y = Math.min(getHeight() - getTotalPaddingBottom() - 1, y);
        y += getScrollY();
        return getLayout().getLineForVertical(y);
    }

    private int getOffsetAtCoordinate(int line, int x) {
        x = convertToLocalHorizontalCoordinate(x);
        return getLayout().getOffsetForHorizontal(line, x);
    }

    /* end of copied from TextView */

    @Override
    protected CharSequence convertSelectionToString(Object selectedItem) {
        final RecipientListEntry entry = (RecipientListEntry)selectedItem;
        final String displayName = entry.getDisplayName();
        final String email = entry.getDestination();
        if (TextUtils.isEmpty(displayName) && TextUtils.isEmpty(email)) {
            Log.w(TAG, "Both a display name and an email are null");
            return null;
        } else {
            final Rfc822Token token = new Rfc822Token(displayName, email, null);
            final CharSequence underlyingText = token.toString() + ", ";
            final CharSequence displayText =
                    !TextUtils.isEmpty(displayName) ? displayName : email;
            SpannableStringBuilder builder = new SpannableStringBuilder(underlyingText);
            builder.setSpan(constructChipSpan(this, displayText, false),
                    0, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            return builder;
        }
    }

    private static ChipSpan constructChipSpan(
            TextView view, CharSequence text, boolean pressed) {
        final Layout layout = view.getLayout();
        final Resources res = view.getContext().getResources();
        final TextPaint paint = view.getPaint();

        int line = layout.getLineForOffset(0);
        int lineTop = layout.getLineTop(line);
        int lineBottom = layout.getLineBottom(line);
        int lineBaseline = layout.getLineBaseline(line);
        int ascent = layout.getLineAscent(line);
        int descent = layout.getLineDescent(line);
        int width = (int) Math.floor(paint.measureText(text, 0, text.length()));
        int height = lineBottom - lineTop;

        Bitmap tmpBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(tmpBitmap);
        if (pressed) {
            canvas.drawColor(0xFFDDFFFF);
        } else {
            canvas.drawColor(0xFF00FFFF);
        }

        canvas.drawText(text, 0, text.length(), 0, Math.abs(ascent), paint);

        Drawable result = new BitmapDrawable(res, tmpBitmap);
        result.setBounds(0, 0, width, height);
        return new ChipSpan(result, text);
    }

    private static class ChipSpan extends ImageSpan {
        private final CharSequence mText;

        public ChipSpan(Drawable drawable, CharSequence text) {
            super(drawable);
            mText = text;
        }

        public CharSequence getText() {
            return mText;
        }
    }
}