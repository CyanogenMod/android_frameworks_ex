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
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;
import android.text.Editable;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.QwertyKeyListener;
import android.text.style.ImageSpan;
import android.text.util.Rfc822Token;
import android.text.util.Rfc822Tokenizer;
import android.util.AttributeSet;
import android.util.Log;
import android.view.ActionMode;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;
import android.view.ActionMode.Callback;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ListAdapter;
import android.widget.ListPopupWindow;
import android.widget.ListView;
import android.widget.MultiAutoCompleteTextView;
import android.widget.ScrollView;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import java.util.ArrayList;

/**
 * RecipientEditTextView is an auto complete text view for use with applications
 * that use the new Chips UI for addressing a message to recipients.
 */
public class RecipientEditTextView extends MultiAutoCompleteTextView implements
        OnItemClickListener, Callback, RecipientAlternatesAdapter.OnCheckedItemChangedListener {

    private static final String TAG = "RecipientEditTextView";

    // TODO: get correct number/ algorithm from with UX.
    private static final int CHIP_LIMIT = 2;

    private static final int INVALID_CONTACT = -1;

    // TODO: get correct size from UX.
    private static final float MORE_WIDTH_FACTOR = 0.25f;

    private Drawable mChipBackground = null;

    private Drawable mChipDelete = null;

    private int mChipPadding;

    private Tokenizer mTokenizer;

    private Drawable mChipBackgroundPressed;

    private RecipientChip mSelectedChip;

    private int mAlternatesLayout;

    private Bitmap mDefaultContactPhoto;

    private ImageSpan mMoreChip;

    private int mMoreString;


    private final ArrayList<String> mPendingChips = new ArrayList<String>();

    private float mChipHeight;

    private float mChipFontSize;

    private Validator mValidator;

    private Drawable mInvalidChipBackground;

    private Handler mHandler;

    private static int DISMISS = "dismiss".hashCode();

    private static final long DISMISS_DELAY = 300;

    private int mPendingChipsCount = 0;

    private static int sSelectedTextColor = -1;

    private static final char COMMIT_CHAR_COMMA = ',';

    private static final char COMMIT_CHAR_SEMICOLON = ';';

    private static final char COMMIT_CHAR_SPACE = ' ';

    private ListPopupWindow mAlternatesPopup;

    private ArrayList<RecipientChip> mTemporaryRecipients;

    private ArrayList<RecipientChip> mRemovedSpans;

    /**
     * Used with {@link mAlternatesPopup}. Handles clicks to alternate addresses for a selected chip.
     */
    private OnItemClickListener mAlternatesListener;

    private int mCheckedItem;
    private TextWatcher mTextWatcher;

    private ScrollView mScrollView;

    private boolean mTried;

    private final Runnable mAddTextWatcher = new Runnable() {
        @Override
        public void run() {
            if (mTextWatcher == null) {
                mTextWatcher = new RecipientTextWatcher();
                addTextChangedListener(mTextWatcher);
            }
        }
    };

    private IndividualReplacementTask mIndividualReplacements;

    public RecipientEditTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        if (sSelectedTextColor == -1) {
            sSelectedTextColor = context.getResources().getColor(android.R.color.white);
        }
        mAlternatesPopup = new ListPopupWindow(context);
        mAlternatesListener = new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView,View view, int position,
                    long rowId) {
                mAlternatesPopup.setOnItemClickListener(null);
                replaceChip(mSelectedChip, ((RecipientAlternatesAdapter) adapterView.getAdapter())
                        .getRecipientEntry(position));
                Message delayed = Message.obtain(mHandler, DISMISS);
                delayed.obj = mAlternatesPopup;
                mHandler.sendMessageDelayed(delayed, DISMISS_DELAY);
                clearComposingText();
            }
        };
        setSuggestionsEnabled(false);
        setOnItemClickListener(this);
        setCustomSelectionActionModeCallback(this);
        mHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                if (msg.what == DISMISS) {
                    ((ListPopupWindow) msg.obj).dismiss();
                    return;
                }
                super.handleMessage(msg);
            }
        };
    }

    @Override
    public <T extends ListAdapter & Filterable> void setAdapter(T adapter) {
        super.setAdapter(adapter);
        if (adapter == null) {
            return;
        }
        // Start the filtering process as soon as possible. This will
        // cause any needed services to be started and make the first filter
        // query come back more quickly.
        Filter f = ((Filterable) adapter).getFilter();
        f.filter("sara");
    }

    @Override
    public void onSelectionChanged(int start, int end) {
        // When selection changes, see if it is inside the chips area.
        // If so, move the cursor back after the chips again.
        Spannable span = getSpannable();
        int textLength = getText().length();
        RecipientChip[] chips = span.getSpans(start, textLength, RecipientChip.class);
        if (chips != null && chips.length > 0) {
            if (chips != null && chips.length > 0) {
                // Grab the last chip and set the cursor to after it.
                setSelection(Math.min(span.getSpanEnd(chips[chips.length - 1]) + 1, textLength));
            }
        }
        super.onSelectionChanged(start, end);
    }

    /**
     * Convenience method: Append the specified text slice to the TextView's
     * display buffer, upgrading it to BufferType.EDITABLE if it was
     * not already editable. Commas are excluded as they are added automatically
     * by the view.
     */
    @Override
    public void append(CharSequence text, int start, int end) {
        super.append(text, start, end);
        if (!TextUtils.isEmpty(text) && TextUtils.getTrimmedLength(text) > 0) {
            final String displayString = (String) text;
            int seperatorPos = displayString.indexOf(COMMIT_CHAR_COMMA);
            if (seperatorPos != 0 && !TextUtils.isEmpty(displayString)
                    && TextUtils.getTrimmedLength(displayString) > 0) {
                mPendingChipsCount++;
                mPendingChips.add((String)text);
            }
        }
    }

    @Override
    public void onFocusChanged(boolean hasFocus, int direction, Rect previous) {
        if (!hasFocus) {
            shrink();
            // Reset any pending chips as they would have been handled
            // when the field lost focus.
            mPendingChipsCount = 0;
            mPendingChips.clear();
            mHandler.post(mAddTextWatcher);
        } else {
            expand();
            scrollLineIntoView(getLineCount());
        }
        super.onFocusChanged(hasFocus, direction, previous);
    }

    private void shrink() {
        if (mSelectedChip != null) {
            clearSelectedChip();
        } else {
            commitDefault();
        }
        createMoreChip();
    }

    private void expand() {
        removeMoreChip();
        setCursorVisible(true);
        Editable text = getText();
        setSelection(text != null && text.length() > 0 ? text.length() : 0);
        // If there are any temporary chips, try replacing them now that the user
        // has expanded the field.
        if (mTemporaryRecipients != null && mTemporaryRecipients.size() > 0) {
            new RecipientReplacementTask().execute();
            mTemporaryRecipients = null;
        }
    }

    private CharSequence ellipsizeText(CharSequence text, TextPaint paint, float maxWidth) {
        paint.setTextSize(mChipFontSize);
        if (maxWidth <= 0 && Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Max width is negative: " + maxWidth);
        }
        return TextUtils.ellipsize(text, paint, maxWidth,
                TextUtils.TruncateAt.END);
    }

    private Bitmap createSelectedChip(RecipientEntry contact, TextPaint paint, Layout layout) {
        // Ellipsize the text so that it takes AT MOST the entire width of the
        // autocomplete text entry area. Make sure to leave space for padding
        // on the sides.
        int height = (int) mChipHeight;
        int deleteWidth = height;
        CharSequence ellipsizedText = ellipsizeText(contact.getDisplayName(), paint,
                calculateAvailableWidth(true) - deleteWidth);

        // Make sure there is a minimum chip width so the user can ALWAYS
        // tap a chip without difficulty.
        int width = Math.max(deleteWidth * 2, (int) Math.floor(paint.measureText(ellipsizedText, 0,
                ellipsizedText.length()))
                + (mChipPadding * 2) + deleteWidth);

        // Create the background of the chip.
        Bitmap tmpBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(tmpBitmap);
        if (mChipBackgroundPressed != null) {
            mChipBackgroundPressed.setBounds(0, 0, width, height);
            mChipBackgroundPressed.draw(canvas);
            paint.setColor(sSelectedTextColor);
            // Align the display text with where the user enters text.
            canvas.drawText(ellipsizedText, 0, ellipsizedText.length(), mChipPadding, height
                    - Math.abs(height - mChipFontSize)/2, paint);
            // Make the delete a square.
            mChipDelete.setBounds(width - deleteWidth, 0, width, height);
            mChipDelete.draw(canvas);
        } else {
            Log.w(TAG, "Unable to draw a background for the chips as it was never set");
        }
        return tmpBitmap;
    }


    /**
     * Get the background drawable for a RecipientChip.
     */
    public Drawable getChipBackground(RecipientEntry contact) {
        return (mValidator != null && mValidator.isValid(contact.getDestination())) ?
                mChipBackground : mInvalidChipBackground;
    }

    private Bitmap createUnselectedChip(RecipientEntry contact, TextPaint paint, Layout layout) {
        // Ellipsize the text so that it takes AT MOST the entire width of the
        // autocomplete text entry area. Make sure to leave space for padding
        // on the sides.
        int height = (int) mChipHeight;
        int iconWidth = height;
        CharSequence ellipsizedText = ellipsizeText(contact.getDisplayName(), paint,
                calculateAvailableWidth(false) - iconWidth);
        // Make sure there is a minimum chip width so the user can ALWAYS
        // tap a chip without difficulty.
        int width = Math.max(iconWidth * 2, (int) Math.floor(paint.measureText(ellipsizedText, 0,
                ellipsizedText.length()))
                + (mChipPadding * 2) + iconWidth);

        // Create the background of the chip.
        Bitmap tmpBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(tmpBitmap);
        Drawable background = getChipBackground(contact);
        if (background != null) {
            background.setBounds(0, 0, width, height);
            background.draw(canvas);

            // Don't draw photos for recipients that have been typed in.
            if (contact.getContactId() != INVALID_CONTACT) {
                byte[] photoBytes = contact.getPhotoBytes();
                // There may not be a photo yet if anything but the first contact address
                // was selected.
                if (photoBytes == null && contact.getPhotoThumbnailUri() != null) {
                    // TODO: cache this in the recipient entry?
                    ((BaseRecipientAdapter) getAdapter()).fetchPhoto(contact, contact
                            .getPhotoThumbnailUri());
                    photoBytes = contact.getPhotoBytes();
                }

                Bitmap photo;
                if (photoBytes != null) {
                    photo = BitmapFactory.decodeByteArray(photoBytes, 0, photoBytes.length);
                } else {
                    // TODO: can the scaled down default photo be cached?
                    photo = mDefaultContactPhoto;
                }
                // Draw the photo on the left side.
                Matrix matrix = new Matrix();
                RectF src = new RectF(0, 0, photo.getWidth(), photo.getHeight());
                RectF dst = new RectF(width - iconWidth, 0, width, height);
                matrix.setRectToRect(src, dst, Matrix.ScaleToFit.CENTER);
                canvas.drawBitmap(photo, matrix, paint);
            } else {
                // Don't leave any space for the icon. It isn't being drawn.
                iconWidth = 0;
            }

            // Align the display text with where the user enters text.
            canvas.drawText(ellipsizedText, 0, ellipsizedText.length(), mChipPadding,
                    height - Math.abs(height - mChipFontSize) / 2, paint);
        } else {
            Log.w(TAG, "Unable to draw a background for the chips as it was never set");
        }
        return tmpBitmap;
    }

    public RecipientChip constructChipSpan(RecipientEntry contact, int offset, boolean pressed)
            throws NullPointerException {
        if (mChipBackground == null) {
            throw new NullPointerException(
                    "Unable to render any chips as setChipDimensions was not called.");
        }
        Layout layout = getLayout();

        TextPaint paint = getPaint();
        float defaultSize = paint.getTextSize();
        int defaultColor = paint.getColor();

        Bitmap tmpBitmap;
        if (pressed) {
            tmpBitmap = createSelectedChip(contact, paint, layout);

        } else {
            tmpBitmap = createUnselectedChip(contact, paint, layout);
        }

        // Pass the full text, un-ellipsized, to the chip.
        Drawable result = new BitmapDrawable(getResources(), tmpBitmap);
        result.setBounds(0, 0, tmpBitmap.getWidth(), tmpBitmap.getHeight());
        RecipientChip recipientChip = new RecipientChip(result, contact, offset);
        // Return text to the original size.
        paint.setTextSize(defaultSize);
        paint.setColor(defaultColor);
        return recipientChip;
    }

    /**
     * Calculate the bottom of the line the chip will be located on using:
     * 1) which line the chip appears on
     * 2) the height of a chip
     * 3) padding built into the edit text view
     */
    private int calculateOffsetFromBottom(int line) {
        // Line offsets start at zero.
        int actualLine = getLineCount() - (line + 1);
        return -((actualLine * ((int)mChipHeight) + getPaddingBottom()) + getPaddingTop());
    }

    /**
     * Get the max amount of space a chip can take up. The formula takes into
     * account the width of the EditTextView, any view padding, and padding
     * that will be added to the chip.
     */
    private float calculateAvailableWidth(boolean pressed) {
        return getWidth() - getPaddingLeft() - getPaddingRight() - (mChipPadding * 2);
    }

    /**
     * Set all chip dimensions and resources. This has to be done from the
     * application as this is a static library.
     * @param chipBackground
     * @param chipBackgroundPressed
     * @param invalidChip
     * @param chipDelete
     * @param defaultContact
     * @param moreResource
     * @param alternatesLayout
     * @param chipHeight
     * @param padding Padding around the text in a chip
     */
    public void setChipDimensions(Drawable chipBackground, Drawable chipBackgroundPressed,
            Drawable invalidChip, Drawable chipDelete, Bitmap defaultContact, int moreResource,
            int alternatesLayout, float chipHeight, float padding,
            float chipFontSize) {
        mChipBackground = chipBackground;
        mChipBackgroundPressed = chipBackgroundPressed;
        mChipDelete = chipDelete;
        mChipPadding = (int) padding;
        mAlternatesLayout = alternatesLayout;
        mDefaultContactPhoto = defaultContact;
        mMoreString = moreResource;
        mChipHeight = chipHeight;
        mChipFontSize = chipFontSize;
        mInvalidChipBackground = invalidChip;
    }

    @Override
    public void onSizeChanged(int width, int height, int oldw, int oldh) {
        super.onSizeChanged(width, height, oldw, oldh);
        // Check for any pending tokens created before layout had been completed
        // on the view.
        if (width != 0 && height != 0) {
            if (mPendingChipsCount > 0) {
                handlePendingChips();
            }
            mPendingChipsCount = 0;
            mPendingChips.clear();
            mHandler.post(mAddTextWatcher);
        }
        // Try to find the scroll view parent, if it exists.
        if (mScrollView == null && !mTried) {
            ViewParent parent = getParent();
            while (parent != null && !(parent instanceof ScrollView)) {
                parent = parent.getParent();
            }
            if (parent != null) {
                mScrollView = (ScrollView) parent;
            }
            mTried = true;
        }
    }

    private void handlePendingChips() {
        mTemporaryRecipients = new ArrayList<RecipientChip>(mPendingChipsCount);
        Editable editable = getText();
        // Tokenize!
        for (int i = 0; i < mPendingChips.size(); i++) {
            String current = mPendingChips.get(i);
            int tokenStart = editable.toString().indexOf(current);
            int tokenEnd = tokenStart + current.length();
            if (tokenStart >= 0) {
                // When we have a valid token, include it with the token
                // to the left.
                if (tokenEnd < editable.length() - 2
                        && editable.charAt(tokenEnd) == COMMIT_CHAR_COMMA) {
                    tokenEnd++;
                }
                createReplacementChip(tokenStart, tokenEnd, editable);
            }
            mPendingChipsCount--;
        }
        sanitizeSpannable();
        if (mTemporaryRecipients != null
                && mTemporaryRecipients.size() <= RecipientAlternatesAdapter.MAX_LOOKUPS) {
            if (hasFocus() || mTemporaryRecipients.size() < CHIP_LIMIT) {
                new RecipientReplacementTask().execute();
                mTemporaryRecipients = null;
            } else {
                // Create the "more" chip
                mIndividualReplacements = new IndividualReplacementTask();
                mIndividualReplacements.execute(new ArrayList<RecipientChip>(mTemporaryRecipients
                        .subList(0, CHIP_LIMIT)));

                createMoreChip();
            }
        } else {
            // There are too many recipients to look up, so just fall back to
            // showing addresses for all of them.
            mTemporaryRecipients = null;
            createMoreChip();
        }
    }

    /**
     * Remove any characters after the last valid chip.
     */
    private void sanitizeSpannable() {
        // Find the last chip; eliminate any commit characters after it.
        RecipientChip[] chips = getRecipients();
        if (chips != null && chips.length > 0) {
            int end;
            ImageSpan lastSpan;
            if (mMoreChip != null) {
                lastSpan = mMoreChip;
            } else {
                lastSpan = chips[chips.length - 1];
            }
            end = getSpannable().getSpanEnd(lastSpan);
            Editable editable = getText();
            int length = editable.length();
            if (length > end) {
                // See what characters occur after that and eliminate them.
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "There were extra characters after the last tokenizable entry."
                            + editable);
                }
                editable.delete(end + 1, length);
            }
        }
    }

    /**
     * Create a chip that represents just the email address of a recipient. At some later
     * point, this chip will be attached to a real contact entry, if one exists.
     */
    private void createReplacementChip(int tokenStart, int tokenEnd, Editable editable) {
        String token = editable.toString().substring(tokenStart, tokenEnd);
        int commitCharIndex = token.indexOf(COMMIT_CHAR_COMMA);
        if (commitCharIndex == token.length() - 1) {
            token = token.substring(0, token.length() - 1);
        }
        RecipientEntry entry = createTokenizedEntry(token);
        String displayText = entry.getDestination();
        displayText = (String) mTokenizer.terminateToken(displayText);
        // Always leave a blank space at the end of a chip.
        int textLength = displayText.length() - 1;
        SpannableString chipText = new SpannableString(displayText);
        int end = getSelectionEnd();
        int start = mTokenizer.findTokenStart(getText(), end);
        RecipientChip chip = null;
        try {
            chip = constructChipSpan(entry, start, false);
            chipText.setSpan(chip, 0, textLength, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        } catch (NullPointerException e) {
            Log.e(TAG, e.getMessage(), e);
        }

        editable.replace(tokenStart, tokenEnd, chipText);
        // Add this chip to the list of entries "to replace"
        if (chip != null) {
            mTemporaryRecipients.add(chip);
        }
    }

    private RecipientEntry createTokenizedEntry(String token) {
        Rfc822Token[] tokens = Rfc822Tokenizer.tokenize(token);
        String address;
        if (tokens != null && tokens.length > 0) {
            address = tokens[0].getAddress();
        } else {
            address = token;
        }
        return RecipientEntry.constructFakeEntry(address);
    }

    @Override
    public void setTokenizer(Tokenizer tokenizer) {
        mTokenizer = tokenizer;
        super.setTokenizer(mTokenizer);
    }

    @Override
    public void setValidator(Validator validator) {
        mValidator = validator;
        super.setValidator(validator);
    }

    /**
     * We cannot use the default mechanism for replaceText. Instead,
     * we override onItemClickListener so we can get all the associated
     * contact information including display text, address, and id.
     */
    @Override
    protected void replaceText(CharSequence text) {
        return;
    }

    /**
     * Dismiss any selected chips when the back key is pressed.
     */
    @Override
    public boolean onKeyPreIme(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            clearSelectedChip();
        }
        return super.onKeyPreIme(keyCode, event);
    }

    /**
     * Monitor key presses in this view to see if the user types
     * any commit keys, which consist of ENTER, TAB, or DPAD_CENTER.
     * If the user has entered text that has contact matches and types
     * a commit key, create a chip from the topmost matching contact.
     * If the user has entered text that has no contact matches and types
     * a commit key, then create a chip from the text they have entered.
     */
    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_DPAD_CENTER:
                if (event.hasNoModifiers()) {
                    if (commitDefault()) {
                        return true;
                    }
                    if (mSelectedChip != null) {
                        clearSelectedChip();
                        return true;
                    } else if (focusNext()) {
                        return true;
                    }
                }
                break;
            case KeyEvent.KEYCODE_TAB:
                if (event.hasNoModifiers()) {
                    if (mSelectedChip != null) {
                        clearSelectedChip();
                    } else {
                        commitDefault();
                    }
                    if (focusNext()) {
                        return true;
                    }
                }
        }
        return super.onKeyUp(keyCode, event);
    }

    private boolean focusNext() {
        View next = focusSearch(View.FOCUS_DOWN);
        if (next != null) {
            next.requestFocus();
            return true;
        }
        return false;
    }

    /**
     * Create a chip from the default selection. If the popup is showing, the
     * default is the first item in the popup suggestions list. Otherwise, it is
     * whatever the user had typed in. End represents where the the tokenizer
     * should search for a token to turn into a chip.
     * @return If a chip was created from a real contact.
     */
    private boolean commitDefault() {
        Editable editable = getText();
        int end = getSelectionEnd();
        int start = mTokenizer.findTokenStart(editable, end);

        if (shouldCreateChip(start, end)) {
            int whatEnd = mTokenizer.findTokenEnd(getText(), start);
            // In the middle of chip; treat this as an edit
            // and commit the whole token.
            if (whatEnd != getSelectionEnd()) {
                handleEdit(start, whatEnd);
                return true;
            }
            return commitChip(start, end , editable);
        }
        return false;
    }

    private void commitByCharacter() {
        Editable editable = getText();
        int end = getSelectionEnd();
        int start = mTokenizer.findTokenStart(editable, end);
        if (shouldCreateChip(start, end)) {
            commitChip(start, end, editable);
        }
        setSelection(getText().length());
    }

    private boolean commitChip(int start, int end, Editable editable) {
        if (getAdapter().getCount() > 0) {
            // choose the first entry.
            submitItemAtPosition(0);
            dismissDropDown();
            return true;
        } else {
            int tokenEnd = mTokenizer.findTokenEnd(editable, start);
            String text = editable.toString().substring(start, tokenEnd).trim();
            clearComposingText();
            if (text != null && text.length() > 0 && !text.equals(" ")) {
                RecipientEntry entry = RecipientEntry.constructFakeEntry(text);
                QwertyKeyListener.markAsReplaced(editable, start, end, "");
                CharSequence chipText = createChip(entry, false);
                editable.replace(start, end, chipText);
                dismissDropDown();
                return true;
            }
        }
        return false;
    }

    private boolean shouldCreateChip(int start, int end) {
        if (hasFocus() && enoughToFilter()) {
            RecipientChip[] chips = getSpannable().getSpans(start, end, RecipientChip.class);
            if ((chips == null || chips.length == 0)) {
                return true;
            }
        }
        return false;
    }

    private void handleEdit(int start, int end) {
        // This is in the middle of a chip, so select out the whole chip
        // and commit it.
        Editable editable = getText();
        setSelection(end);
        String text = getText().toString().substring(start, end);
        RecipientEntry entry = RecipientEntry.constructFakeEntry(text);
        QwertyKeyListener.markAsReplaced(editable, start, end, "");
        CharSequence chipText = createChip(entry, false);
        editable.replace(start, getSelectionEnd(), chipText);
        dismissDropDown();
    }

    /**
     * If there is a selected chip, delegate the key events
     * to the selected chip.
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (mSelectedChip != null && keyCode == KeyEvent.KEYCODE_DEL) {
            if (mAlternatesPopup != null && mAlternatesPopup.isShowing()) {
                mAlternatesPopup.dismiss();
            }
            removeChip(mSelectedChip);
        }

        if (keyCode == KeyEvent.KEYCODE_ENTER && event.hasNoModifiers()) {
            return true;
        }

        return super.onKeyDown(keyCode, event);
    }

    private Spannable getSpannable() {
        return getText();
    }

    private int getChipStart(RecipientChip chip) {
        return getSpannable().getSpanStart(chip);
    }

    private int getChipEnd(RecipientChip chip) {
        return getSpannable().getSpanEnd(chip);
    }

    /**
     * Instead of filtering on the entire contents of the edit box,
     * this subclass method filters on the range from
     * {@link Tokenizer#findTokenStart} to {@link #getSelectionEnd}
     * if the length of that range meets or exceeds {@link #getThreshold}
     * and makes sure that the range is not already a Chip.
     */
    @Override
    protected void performFiltering(CharSequence text, int keyCode) {
        if (enoughToFilter()) {
            int end = getSelectionEnd();
            int start = mTokenizer.findTokenStart(text, end);
            // If this is a RecipientChip, don't filter
            // on its contents.
            Spannable span = getSpannable();
            RecipientChip[] chips = span.getSpans(start, end, RecipientChip.class);
            if (chips != null && chips.length > 0) {
                return;
            }
        }
        super.performFiltering(text, keyCode);
    }

    private void clearSelectedChip() {
        if (mSelectedChip != null) {
            unselectChip(mSelectedChip);
            mSelectedChip = null;
        }
        setCursorVisible(true);
    }

    /**
     * Monitor touch events in the RecipientEditTextView.
     * If the view does not have focus, any tap on the view
     * will just focus the view. If the view has focus, determine
     * if the touch target is a recipient chip. If it is and the chip
     * is not selected, select it and clear any other selected chips.
     * If it isn't, then select that chip.
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isFocused()) {
            // Ignore any chip taps until this view is focused.
            return super.onTouchEvent(event);
        }

        boolean handled = super.onTouchEvent(event);
        int action = event.getAction();
        boolean chipWasSelected = false;

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_DOWN) {
            float x = event.getX();
            float y = event.getY();
            setCursorVisible(false);
            int offset = putOffsetInRange(getOffsetForPosition(x, y));
            RecipientChip currentChip = findChip(offset);
            if (currentChip != null) {
                if (action == MotionEvent.ACTION_UP) {
                    if (mSelectedChip != null && mSelectedChip != currentChip) {
                        clearSelectedChip();
                        mSelectedChip = selectChip(currentChip);
                    } else if (mSelectedChip == null) {
                        // Selection may have moved due to the tap event,
                        // but make sure we correctly reset selection to the
                        // end so that any unfinished chips are committed.
                        setSelection(getText().length());
                        commitDefault();
                        mSelectedChip = selectChip(currentChip);
                    } else {
                        onClick(mSelectedChip, offset, x, y);
                    }
                }
                chipWasSelected = true;
                handled = true;
            }
        }
        if (action == MotionEvent.ACTION_UP && !chipWasSelected) {
            clearSelectedChip();
        }
        return handled;
    }

    private void scrollLineIntoView(int line) {
        if (mScrollView != null) {
            mScrollView.scrollBy(0, calculateOffsetFromBottom(line));
        }
    }

    private void showAlternates(RecipientChip currentChip, ListPopupWindow alternatesPopup,
            int width, Context context) {
        int line = getLayout().getLineForOffset(getChipStart(currentChip));
        int bottom = calculateOffsetFromBottom(line);
        // Align the alternates popup with the left side of the View,
        // regardless of the position of the chip tapped.
        alternatesPopup.setWidth(width);
        alternatesPopup.setAnchorView(this);
        alternatesPopup.setVerticalOffset(bottom);
        alternatesPopup.setAdapter(createAlternatesAdapter(currentChip));
        alternatesPopup.setOnItemClickListener(mAlternatesListener);
        alternatesPopup.show();
        ListView listView = alternatesPopup.getListView();
        listView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        // Checked item would be -1 if the adapter has not
        // loaded the view that should be checked yet. The
        // variable will be set correctly when onCheckedItemChanged
        // is called in a separate thread.
        if (mCheckedItem != -1) {
            listView.setItemChecked(mCheckedItem, true);
            mCheckedItem = -1;
        }
    }

    private ListAdapter createAlternatesAdapter(RecipientChip chip) {
        return new RecipientAlternatesAdapter(getContext(), chip.getContactId(), chip.getDataId(),
                mAlternatesLayout, this);
    }

    public void onCheckedItemChanged(int position) {
        ListView listView = mAlternatesPopup.getListView();
        if (listView != null && listView.getCheckedItemCount() == 0) {
            listView.setItemChecked(position, true);
        } else {
            mCheckedItem = position;
        }
    }

    // TODO: This algorithm will need a lot of tweaking after more people have used
    // the chips ui. This attempts to be "forgiving" to fat finger touches by favoring
    // what comes before the finger.
    private int putOffsetInRange(int o) {
        int offset = o;
        Editable text = getText();
        int length = text.length();
        // Remove whitespace from end to find "real end"
        int realLength = length;
        for (int i = length - 1; i >= 0; i--) {
            if (text.charAt(i) == ' ') {
                realLength--;
            } else {
                break;
            }
        }

        // If the offset is beyond or at the end of the text,
        // leave it alone.
        if (offset >= realLength) {
            return offset;
        }
        Editable editable = getText();
        while (offset >= 0 && findText(editable, offset) == -1 && findChip(offset) == null) {
            // Keep walking backward!
            offset--;
        }
        return offset;
    }

    private int findText(Editable text, int offset) {
        if (text.charAt(offset) != ' ') {
            return offset;
        }
        return -1;
    }

    private RecipientChip findChip(int offset) {
        RecipientChip[] chips = getSpannable().getSpans(0, getText().length(), RecipientChip.class);
        // Find the chip that contains this offset.
        for (int i = 0; i < chips.length; i++) {
            RecipientChip chip = chips[i];
            int start = getChipStart(chip);
            int end = getChipEnd(chip);
            if (offset >= start && offset <= end) {
                return chip;
            }
        }
        return null;
    }

    private CharSequence createChip(RecipientEntry entry, boolean pressed) {
        String displayText = entry.getDestination();
        displayText = (String) mTokenizer.terminateToken(displayText);
        // Always leave a blank space at the end of a chip.
        int textLength = displayText.length()-1;
        SpannableString chipText = new SpannableString(displayText);
        int end = getSelectionEnd();
        int start = mTokenizer.findTokenStart(getText(), end);
        try {
            chipText.setSpan(constructChipSpan(entry, start, pressed), 0, textLength,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        } catch (NullPointerException e) {
            Log.e(TAG, e.getMessage(), e);
            return null;
        }

        return chipText;
    }

    /**
     * When an item in the suggestions list has been clicked, create a chip from the
     * contact information of the selected item.
     */
    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        submitItemAtPosition(position);
    }

    private void submitItemAtPosition(int position) {
        RecipientEntry entry = createValidatedEntry(
                (RecipientEntry)getAdapter().getItem(position));
        clearComposingText();

        int end = getSelectionEnd();
        int start = mTokenizer.findTokenStart(getText(), end);

        Editable editable = getText();
        QwertyKeyListener.markAsReplaced(editable, start, end, "");
        editable.replace(start, end, createChip(entry, false));
    }

    private RecipientEntry createValidatedEntry(RecipientEntry item) {
        if (item == null) {
            return null;
        }
        final RecipientEntry entry;
        // If the display name and the address are the same, or if this is a
        // valid contact, but the destination is invalid, then make this a fake
        // recipient that is editable.
        String destination = item.getDestination();
        if (TextUtils.equals(item.getDisplayName(), destination)
                || (mValidator != null && !mValidator.isValid(destination))) {
            entry = RecipientEntry.constructFakeEntry(destination);
        } else {
            entry = item;
        }
        return entry;
    }

    /** Returns a collection of contact Id for each chip inside this View. */
    /* package */ Collection<Long> getContactIds() {
        final Set<Long> result = new HashSet<Long>();
        RecipientChip[] chips = getRecipients();
        if (chips != null) {
            for (RecipientChip chip : chips) {
                result.add(chip.getContactId());
            }
        }
        return result;
    }

    private RecipientChip[] getRecipients() {
        return getSpannable().getSpans(0, getText().length(), RecipientChip.class);
    }

    /** Returns a collection of data Id for each chip inside this View. May be null. */
    /* package */ Collection<Long> getDataIds() {
        final Set<Long> result = new HashSet<Long>();
        RecipientChip [] chips = getRecipients();
        if (chips != null) {
            for (RecipientChip chip : chips) {
                result.add(chip.getDataId());
            }
        }
        return result;
    }


    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
        return false;
    }

    @Override
    public void onDestroyActionMode(ActionMode mode) {
    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        return false;
    }

    /**
     * No chips are selectable.
     */
    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
        return false;
    }

    /**
     * Create the more chip. The more chip is text that replaces any chips that
     * do not fit in the pre-defined available space when the
     * RecipientEditTextView loses focus.
     */
    private void createMoreChip() {
        RecipientChip[] recipients = getRecipients();
        if (recipients == null || recipients.length <= CHIP_LIMIT) {
            mMoreChip = null;
            return;
        }
        int numRecipients = recipients.length;
        int overage = numRecipients - CHIP_LIMIT;
        Editable text = getText();
        // TODO: get the correct size from visual design.
        int width = (int) Math.floor(getWidth() * MORE_WIDTH_FACTOR);
        int height = getLineHeight();
        Bitmap drawable = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(drawable);
        String moreText = getResources().getString(mMoreString, overage);
        canvas.drawText(moreText, 0, moreText.length(), 0, height - getLayout().getLineDescent(0),
                getPaint());

        Drawable result = new BitmapDrawable(getResources(), drawable);
        result.setBounds(0, 0, width, height);
        ImageSpan moreSpan = new ImageSpan(result);
        Spannable spannable = getSpannable();
        // Remove the overage chips.
        if (recipients == null || recipients.length == 0) {
            Log.w(TAG,
                    "We have recipients. Tt should not be possible to have zero RecipientChips.");
            mMoreChip = null;
            return;
        }
        mRemovedSpans = new ArrayList<RecipientChip>();
        int totalReplaceStart = 0;
        int totalReplaceEnd = 0;
        for (int i = numRecipients - overage; i < recipients.length; i++) {
            mRemovedSpans.add(recipients[i]);
            if (i == numRecipients - overage) {
                totalReplaceStart = spannable.getSpanStart(recipients[i]);
            }
            if (i == recipients.length - 1) {
                totalReplaceEnd = spannable.getSpanEnd(recipients[i]);
            }
            if (mTemporaryRecipients != null && !mTemporaryRecipients.contains(recipients[i])) {
                recipients[i].storeChipStart(spannable.getSpanStart(recipients[i]));
                recipients[i].storeChipEnd(spannable.getSpanEnd(recipients[i]));
            }
            spannable.removeSpan(recipients[i]);
        }
        // TODO: why would these ever be backwards?
        int end = Math.max(totalReplaceStart, totalReplaceEnd);
        int start = Math.min(totalReplaceStart, totalReplaceEnd);
        SpannableString chipText = new SpannableString(text.subSequence(start, end));
        chipText.setSpan(moreSpan, 0, chipText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.replace(start, end, chipText);
        mMoreChip = moreSpan;
    }

    /**
     * Replace the more chip, if it exists, with all of the recipient chips it had
     * replaced when the RecipientEditTextView gains focus.
     */
    private void removeMoreChip() {
        if (mMoreChip != null) {
            Spannable span = getSpannable();
            span.removeSpan(mMoreChip);
            mMoreChip = null;
            // Re-add the spans that were removed.
            if (mRemovedSpans != null && mRemovedSpans.size() > 0) {
                // Recreate each removed span.
                Editable editable = getText();
                for (RecipientChip chip : mRemovedSpans) {
                    int chipStart = chip.getStoredChipStart();
                    int chipEnd;
                    String token;
                    if (chipStart == -1) {
                        // Need to find the location of the chip, again.
                        token = (String)mTokenizer.terminateToken(chip.getEntry().getDestination());
                        chipStart = editable.toString().indexOf(token);
                        // -1 for the space!
                        chipEnd = chipStart + token.length() - 1;
                    } else {
                        chipEnd = Math.min(editable.length(), chip.getStoredChipEnd());
                    }
                    if (Log.isLoggable(TAG, Log.DEBUG) && chipEnd != chip.getStoredChipEnd()) {
                        Log.d(TAG,
                                "Unexpectedly, the chip ended after the end of the editable text. "
                                        + "Chip End " + chip.getStoredChipEnd()
                                        + "Editable length " + editable.length());
                    }
                    // Only set the span if we found a matching token.
                    if (chipStart != -1) {
                        editable.setSpan(chip, chipStart, chipEnd,
                                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                }
                mRemovedSpans.clear();
            }
        }
    }

    /**
     * Show specified chip as selected. If the RecipientChip is just an email address,
     * selecting the chip will take the contents of the chip and place it at
     * the end of the RecipientEditTextView for inline editing. If the
     * RecipientChip is a complete contact, then selecting the chip
     * will change the background color of the chip, show the delete icon,
     * and a popup window with the address in use highlighted and any other
     * alternate addresses for the contact.
     * @param currentChip Chip to select.
     * @return A RecipientChip in the selected state or null if the chip
     * just contained an email address.
     */
    public RecipientChip selectChip(RecipientChip currentChip) {
        if (currentChip.getContactId() != INVALID_CONTACT) {
            int start = getChipStart(currentChip);
            int end = getChipEnd(currentChip);
            getSpannable().removeSpan(currentChip);
            RecipientChip newChip;
            try {
                newChip = constructChipSpan(currentChip.getEntry(), start, true);
            } catch (NullPointerException e) {
                Log.e(TAG, e.getMessage(), e);
                return null;
            }
            Editable editable = getText();
            QwertyKeyListener.markAsReplaced(editable, start, end, "");
            if (start == -1 || end == -1) {
                Log.d(TAG, "The chip being selected no longer exists but should.");
            } else {
                editable.setSpan(newChip, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            newChip.setSelected(true);
            if (newChip.getEntry().getContactId() == INVALID_CONTACT) {
                scrollLineIntoView(getLayout().getLineForOffset(
                        getChipStart(newChip)));
            }
            showAlternates(newChip, mAlternatesPopup, getWidth(), getContext());
            return newChip;
        } else {
            CharSequence text = currentChip.getValue();
            Editable editable = getText();
            removeChip(currentChip);
            editable.append(text);
            setCursorVisible(true);
            setSelection(editable.length());
            return null;
        }
    }


    /**
     * Remove selection from this chip. Unselecting a RecipientChip will render
     * the chip without a delete icon and with an unfocused background. This
     * is called when the RecipientChip no longer has focus.
     */
    public void unselectChip(RecipientChip chip) {
        int start = getChipStart(chip);
        int end = getChipEnd(chip);
        Editable editable = getText();
        mSelectedChip = null;
        if (start == -1 || end == -1) {
            Log.e(TAG, "The chip being unselected no longer exists but should.");
        } else {
            getSpannable().removeSpan(chip);
            QwertyKeyListener.markAsReplaced(editable, start, end, "");
            editable.removeSpan(chip);
            try {
                editable.setSpan(constructChipSpan(chip.getEntry(), start, false), start, end,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } catch (NullPointerException e) {
                Log.e(TAG, e.getMessage(), e);
            }
        }
        setCursorVisible(true);
        setSelection(editable.length());
        if (mAlternatesPopup != null && mAlternatesPopup.isShowing()) {
            mAlternatesPopup.dismiss();
        }
    }


    /**
     * Return whether this chip contains the position passed in.
     */
    public boolean matchesChip(RecipientChip chip, int offset) {
        int start = getChipStart(chip);
        int end = getChipEnd(chip);
        if (start == -1 || end == -1) {
            return false;
        }
        return (offset >= start && offset <= end);
    }


    /**
     * Return whether a touch event was inside the delete target of
     * a selected chip. It is in the delete target if:
     * 1) the x and y points of the event are within the
     * delete assset.
     * 2) the point tapped would have caused a cursor to appear
     * right after the selected chip.
     * @return boolean
     */
    private boolean isInDelete(RecipientChip chip, int offset, float x, float y) {
        // Figure out the bounds of this chip and whether or not
        // the user clicked in the X portion.
        return chip.isSelected() && offset == getChipEnd(chip);
    }

    /**
     * Remove the chip and any text associated with it from the RecipientEditTextView.
     */
    private void removeChip(RecipientChip chip) {
        Spannable spannable = getSpannable();
        int spanStart = spannable.getSpanStart(chip);
        int spanEnd = spannable.getSpanEnd(chip);
        Editable text = getText();
        int toDelete = spanEnd;
        boolean wasSelected = chip == mSelectedChip;
        // Clear that there is a selected chip before updating any text.
        if (wasSelected) {
            mSelectedChip = null;
        }
        // Always remove trailing spaces when removing a chip.
        while (toDelete >= 0 && toDelete < text.length() && text.charAt(toDelete) == ' ') {
            toDelete++;
        }
        spannable.removeSpan(chip);
        text.delete(spanStart, toDelete);
        if (wasSelected) {
            clearSelectedChip();
        }
    }

    /**
     * Replace this currently selected chip with a new chip
     * that uses the contact data provided.
     */
    public void replaceChip(RecipientChip chip, RecipientEntry entry) {
        boolean wasSelected = chip == mSelectedChip;
        if (wasSelected) {
            mSelectedChip = null;
        }
        int start = getChipStart(chip);
        int end = getChipEnd(chip);
        getSpannable().removeSpan(chip);
        Editable editable = getText();
        CharSequence chipText = createChip(entry, false);
        if (start == -1 || end == -1) {
            Log.e(TAG, "The chip to replace does not exist but should.");
            editable.insert(0, chipText);
        } else {
            // There may be a space to replace with this chip's new associated
            // space. Check for it.
            int toReplace = end;
            while (toReplace >= 0 && toReplace < editable.length()
                    && editable.charAt(toReplace) == ' ') {
                toReplace++;
            }
            editable.replace(start, toReplace, chipText);
        }
        setCursorVisible(true);
        if (wasSelected) {
            clearSelectedChip();
        }
    }

    /**
     * Handle click events for a chip. When a selected chip receives a click
     * event, see if that event was in the delete icon. If so, delete it.
     * Otherwise, unselect the chip.
     */
    public void onClick(RecipientChip chip, int offset, float x, float y) {
        if (chip.isSelected()) {
            if (isInDelete(chip, offset, x, y)) {
                removeChip(chip);
            } else {
                clearSelectedChip();
            }
        }
    }

    private boolean chipsPending() {
        return mPendingChipsCount > 0 || (mRemovedSpans != null && mRemovedSpans.size() > 0);
    }

    private class RecipientTextWatcher implements TextWatcher {
        @Override
        public void afterTextChanged(Editable s) {
            // Get whether there are any recipients pending addition to the view.
            // If there are, don't do anything in the text watcher.
            if (chipsPending()) {
                return;
            }
            if (mSelectedChip != null) {
                setCursorVisible(true);
                setSelection(getText().length());
                clearSelectedChip();
            }
            int length = s.length();
            // Make sure there is content there to parse and that it is
            // not just the commit character.
            if (length > 1) {
                char last;
                int end = getSelectionEnd() == 0 ? 0 : getSelectionEnd() - 1;
                int len = length() - 1;
                if (end != len) {
                    last = s.charAt(end);
                } else {
                    last = s.charAt(len);
                }
                if (last == COMMIT_CHAR_SEMICOLON || last == COMMIT_CHAR_COMMA) {
                    commitByCharacter();
                } else if (last == COMMIT_CHAR_SPACE) {
                    // Check if this is a valid email address. If it is,
                    // commit it.
                    String text = getText().toString();
                    int tokenStart = mTokenizer.findTokenStart(text, getSelectionEnd());
                    String sub = text.substring(tokenStart, mTokenizer.findTokenEnd(text,
                            tokenStart));
                    if (mValidator != null && mValidator.isValid(sub)) {
                        commitByCharacter();
                    }
                }
            }
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }
    }

    private class RecipientReplacementTask extends AsyncTask<Void, Void, Void> {
        private RecipientChip createFreeChip(RecipientEntry entry) {
            String displayText = entry.getDestination();
            displayText = (String) mTokenizer.terminateToken(displayText);
            try {
                return constructChipSpan(entry, -1, false);
            } catch (NullPointerException e) {
                Log.e(TAG, e.getMessage(), e);
                return null;
            }
        }

        @Override
        protected Void doInBackground(Void... params) {
            if (mIndividualReplacements != null) {
                mIndividualReplacements.cancel(true);
            }
            // For each chip in the list, look up the matching contact.
            // If there is a match, replace that chip with the matching
            // chip.
            final ArrayList<RecipientChip> originalRecipients = new ArrayList<RecipientChip>();
            RecipientChip[] existingChips = getSpannable().getSpans(0, getText().length(),
                    RecipientChip.class);
            for (int i = 0; i < existingChips.length; i++) {
                originalRecipients.add(existingChips[i]);
            }
            if (mRemovedSpans != null) {
                originalRecipients.addAll(mRemovedSpans);
            }
            String[] addresses = new String[originalRecipients.size()];
            for (int i = 0; i < originalRecipients.size(); i++) {
                addresses[i] = originalRecipients.get(i).getEntry().getDestination();
            }
            HashMap<String, RecipientEntry> entries = RecipientAlternatesAdapter
                    .getMatchingRecipients(getContext(), addresses);
            final ArrayList<RecipientChip> replacements = new ArrayList<RecipientChip>();
            for (final RecipientChip temp : originalRecipients) {
                RecipientEntry entry = null;
                if (temp.getEntry().getContactId() == INVALID_CONTACT
                        && getSpannable().getSpanStart(temp) != -1) {
                    // Replace this.
                    entry = createValidatedEntry(entries.get(temp.getEntry().getDestination()));
                }
                if (entry != null) {
                    replacements.add(createFreeChip(entry));
                } else {
                    replacements.add(temp);
                }
            }
            if (replacements != null && replacements.size() > 0) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        SpannableStringBuilder text = new SpannableStringBuilder(getText()
                                .toString());
                        Editable oldText = getText();
                        int start, end;
                        int i = 0;
                        for (RecipientChip chip : originalRecipients) {
                            start = oldText.getSpanStart(chip);
                            if (start != -1) {
                                end = oldText.getSpanEnd(chip);
                                text.removeSpan(chip);
                                // Leave a spot for the space!
                                text.setSpan(replacements.get(i), start, end,
                                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                            }
                            i++;
                        }
                        Editable editable = getText();
                        editable.clear();
                        editable.insert(0, text);
                        originalRecipients.clear();
                    }
                });
            }
            return null;
        }
    }

    private class IndividualReplacementTask extends AsyncTask<Object, Void, Void> {
        @SuppressWarnings("unchecked")
        @Override
        protected Void doInBackground(Object... params) {
            // For each chip in the list, look up the matching contact.
            // If there is a match, replace that chip with the matching
            // chip.
            final ArrayList<RecipientChip> originalRecipients =
                (ArrayList<RecipientChip>) params[0];
            String[] addresses = new String[originalRecipients.size()];
            for (int i = 0; i < originalRecipients.size(); i++) {
                addresses[i] = originalRecipients.get(i).getEntry().getDestination();
            }
            HashMap<String, RecipientEntry> entries = RecipientAlternatesAdapter
                    .getMatchingRecipients(getContext(), addresses);
            for (final RecipientChip temp : originalRecipients) {
                if (temp.getEntry().getContactId() == INVALID_CONTACT
                        && getSpannable().getSpanStart(temp) != -1) {
                    // Replace this.
                    final RecipientEntry entry = createValidatedEntry(entries.get(temp.getEntry()
                            .getDestination()));
                    if (entry != null) {
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                replaceChip(temp, entry);
                            }
                        });
                    }
                }
            }
            return null;
        }
    }
}
