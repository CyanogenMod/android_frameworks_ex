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

import android.text.util.Rfc822Tokenizer;

/**
 * Extension of the Rfc822Tokenizer for use with the RecipientEditTextView that
 * will tokenize properly to create recipient chips.
 */
public class Rfc822ChipTokenizer extends Rfc822Tokenizer {

    /**
     * {@inheritDoc}
     */
    public int findTokenEnd(CharSequence text, int cursor) {
        int len = text.length();
        int i = cursor;

        while (i < len) {
            char c = text.charAt(i);

            if (c == ',' || c == ';' || c == ' ') {
                return i;
            } else if (c == '"') {
                i++;

                while (i < len) {
                    c = text.charAt(i);

                    if (c == '"') {
                        i++;
                        break;
                    } else if (c == '\\' && i + 1 < len) {
                        i += 2;
                    } else {
                        i++;
                    }
                }
            } else if (c == '(') {
                int level = 1;
                i++;

                while (i < len && level > 0) {
                    c = text.charAt(i);

                    if (c == ')') {
                        level--;
                        i++;
                    } else if (c == '(') {
                        level++;
                        i++;
                    } else if (c == '\\' && i + 1 < len) {
                        i += 2;
                    } else {
                        i++;
                    }
                }
            } else if (c == '<') {
                i++;

                while (i < len) {
                    c = text.charAt(i);

                    if (c == '>') {
                        i++;
                        break;
                    } else {
                        i++;
                    }
                }
            } else {
                i++;
            }
        }

        return i;
    }

    /**
     * Terminates the specified address with a space. This assumes that the
     * specified text already has valid syntax. The Adapter subclass's
     * convertToString() method must make that guarantee.
     */
    public CharSequence terminateToken(CharSequence text) {
        // We want to override the tokenizer behavior with our own ending
        // token, space.
        return (((String) text).concat(" "));
    }
}
