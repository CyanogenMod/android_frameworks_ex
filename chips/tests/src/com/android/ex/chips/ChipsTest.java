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
import android.test.AndroidTestCase;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.util.Rfc822Tokenizer;

import com.android.ex.chips.RecipientEditTextView;
import com.android.ex.chips.RecipientEntry;


public class ChipsTest extends AndroidTestCase {
    private RecipientChip[] mMockRecips = new RecipientChip[5];

    private RecipientEntry[] mMockEntries = new RecipientEntry[5];

    private Rfc822Tokenizer mTokenizer;

    private Editable mEditable;

    class MockRecipientEditTextView extends RecipientEditTextView {

        public MockRecipientEditTextView(Context context) {
            super(context, null);
            mTokenizer = new Rfc822Tokenizer();
            setTokenizer(mTokenizer);
            for (int i = 0; i < mMockRecips.length; i++) {
                mMockEntries[i] = RecipientEntry.constructGeneratedEntry("user",
                        "user@username.com");
            }
            for (int i = 0; i < mMockRecips.length; i++) {
                mMockRecips[i] = new RecipientChip(null, mMockEntries[i], i);
            }
        }

        @Override
        public RecipientChip[] getSortedRecipients() {
            return mMockRecips;
        }

        @Override
        public Editable getText() {
            return mEditable;
        }

        @Override
        public Editable getSpannable() {
            return mEditable;
        }
    }

    private MockRecipientEditTextView createViewForTesting() {
        mEditable = new SpannableStringBuilder();
        MockRecipientEditTextView view = new MockRecipientEditTextView(getContext());
        return view;
    }

    public void testCreateDisplayText() {
        RecipientEditTextView view = createViewForTesting();
        RecipientEntry entry = RecipientEntry.constructGeneratedEntry("User Name, Jr",
                "user@username.com");
        String test = view.createDisplayText(entry);
        assertEquals("Expected a properly formatted RFC email address",
                "\"User Name, Jr\" <user@username.com>, ", test);

        RecipientEntry alreadyFormatted = RecipientEntry.constructFakeEntry("user@username.com, ");
        test = view.createDisplayText(alreadyFormatted);
        assertEquals("Expected a properly formatted RFC email address", "<user@username.com>, ",
                test);

        RecipientEntry alreadyFormattedNoSpace = RecipientEntry
                .constructFakeEntry("user@username.com,");
        test = view.createDisplayText(alreadyFormattedNoSpace);
        assertEquals("Expected a properly formatted RFC email address", "<user@username.com>, ",
                test);

        RecipientEntry alreadyNamed = RecipientEntry.constructGeneratedEntry("User Name",
                "\"User Name, Jr\" <user@username.com>");
        test = view.createDisplayText(alreadyNamed);
        assertEquals(
                "Expected address that used the name not the excess address name",
                "User Name <user@username.com>, ", test);
    }

    public void testSanitizeBetween() {
        MockRecipientEditTextView view = createViewForTesting();
        String first = (String) mTokenizer.terminateToken("FIRST");
        String second = (String) mTokenizer.terminateToken("SECOND");
        String extra = "EXTRA";
        mEditable.append(first + extra + second);
        int firstStart = mEditable.toString().indexOf(first);
        int firstEnd = firstStart + first.length();
        int secondStart = mEditable.toString().indexOf(second);
        int secondEnd = secondStart + second.length();
        mEditable.setSpan(mMockRecips[mMockRecips.length - 2], firstStart, firstEnd, 0);
        mEditable.setSpan(mMockRecips[mMockRecips.length - 1], secondStart, secondEnd, 0);
        view.sanitizeBetween();
        String editableString = mEditable.toString();
        assertEquals(editableString.indexOf(extra), -1);
        assertEquals(editableString.indexOf(first), firstStart);
        assertEquals(editableString.indexOf(second), secondStart - extra.length());
        assertEquals(editableString, (first + second));

        mEditable = new SpannableStringBuilder();
        mEditable.append(first);
        firstStart = mEditable.toString().indexOf(first);
        firstEnd = firstStart + first.length();
        mEditable.setSpan(mMockRecips[mMockRecips.length - 1], firstStart, firstEnd, 0);
        view.sanitizeBetween();
        assertEquals(mEditable.toString(), first);
    }
}
