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

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.text.TextUtils;

import java.util.Collection;

public class ChipsUtil {

    /**
     * @return true when the caller can use Chips UI in its environment.
     */
    public static boolean supportsChipsUi() {
        // TODO: should use Build.VERSION_SDK_INT when it is determined.
        return TextUtils.equals("IceCreamSandwich", Build.VERSION.RELEASE);
    }

    // TODO: check this works
    public static void updateRecencyInfo(RecipientEditTextView view) {
        final Context context = view.getContext();
        final ContentResolver resolver = context.getContentResolver();
        final long currentTimeMillis = System.currentTimeMillis();

        final Collection<Integer> contactIds = view.getContactIds();
        if (contactIds != null) {
            for (Integer contactId : contactIds) {
                final Uri uri = ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId);
                final ContentValues values = new ContentValues();
                values.put(Contacts.LAST_TIME_CONTACTED, currentTimeMillis);
                resolver.update(uri, values, null, null);
            }
        }

        /* Not effective yet.
        final Collection<Integer> dataIds = view.getDataIds();
        if (dataIds != null) {
            for (Integer dataId : dataIds) {
                Uri uri = ContentUris.withAppendedId(Data.CONTENT_URI, dataId);
                final ContentValues values = new ContentValues();
                values.put("last_time_contacted", currentTimeMillis);
                resolver.update(uri, values, null, null);
            }
        }*/
    }
}