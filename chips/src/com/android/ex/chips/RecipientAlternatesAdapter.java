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

import com.android.ex.chips.BaseRecipientAdapter.EmailQuery;

import android.content.Context;
import android.database.Cursor;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.ImageView;
import android.widget.TextView;

public class RecipientAlternatesAdapter extends CursorAdapter {
    private final LayoutInflater mLayoutInflater;

    private final int mLayoutId;

    private final int mSelectedLayoutId;

    private final long mCurrentId;

    public RecipientAlternatesAdapter(Context context, long contactId, long currentId, int viewId,
            int selectedViewId) {
        super(context, context.getContentResolver().query(Email.CONTENT_URI, EmailQuery.PROJECTION,
                Email.CONTACT_ID + " =?", new String[] {
                    String.valueOf(contactId)
                }, null), 0);
        mLayoutInflater = LayoutInflater.from(context);
        mLayoutId = viewId;
        mSelectedLayoutId = selectedViewId;
        mCurrentId = currentId;
    }

    @Override
    public long getItemId(int position) {
        Cursor c = getCursor();
        c.moveToPosition(position);
        return c.getLong(EmailQuery.DATA_ID);
    }

    public RecipientEntry getRecipientEntry(int position) {
        Cursor c = getCursor();
        c.moveToPosition(position);
        return RecipientEntry.constructTopLevelEntry(c.getString(EmailQuery.NAME), c
                .getString(EmailQuery.ADDRESS), c.getLong(EmailQuery.CONTACT_ID), c
                .getLong(EmailQuery.DATA_ID), c.getString(EmailQuery.PHOTO_THUMBNAIL_URI));
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Cursor cursor = getCursor();
        cursor.moveToPosition(position);
        if (convertView == null) {
            convertView = newView(cursor.getLong(EmailQuery.DATA_ID) == mCurrentId);
        }
        bindView(convertView, convertView.getContext(), cursor);
        return convertView;
    }

    // TODO: this is VERY similar to the BaseRecipientAdapter. Can we combine
    // somehow?
    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        int position = cursor.getPosition();

        TextView display = (TextView) view.findViewById(android.R.id.text1);
        ImageView imageView = (ImageView) view.findViewById(android.R.id.icon);
        RecipientEntry entry = getRecipientEntry(position);
        if (position == 0) {
            display.setText(cursor.getString(EmailQuery.NAME));
            display.setVisibility(View.VISIBLE);
            // TODO: see if this needs to be done outside the main thread
            // as it may be too slow to get immediately.
            imageView.setImageURI(entry.getPhotoThumbnailUri());
            imageView.setVisibility(View.VISIBLE);
        } else {
            display.setVisibility(View.GONE);
            imageView.setVisibility(View.GONE);
        }
        TextView destination = (TextView) view.findViewById(android.R.id.text2);
        destination.setText(cursor.getString(EmailQuery.ADDRESS));
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        return newView(false);
    }

    private View newView(boolean isSelected) {
        return isSelected ? mLayoutInflater.inflate(mSelectedLayoutId, null) : mLayoutInflater
                .inflate(mLayoutId, null);
    }
}
