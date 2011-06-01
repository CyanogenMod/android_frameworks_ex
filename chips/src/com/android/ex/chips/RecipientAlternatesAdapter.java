
package com.android.ex.chips;

import com.android.ex.chips.BaseRecipientAdapter.EmailQuery;

import android.content.Context;
import android.database.Cursor;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
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
        Cursor c = getCursor();
        c.moveToPosition(position);
        if (convertView == null) {
            convertView = newView(c.getLong(EmailQuery.DATA_ID) == mCurrentId);
        }

        bindView(convertView, convertView.getContext(), getCursor());
        return convertView;
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        TextView destination = (TextView) view.findViewById(android.R.id.text1);
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
