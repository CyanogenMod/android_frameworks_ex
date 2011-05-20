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

import android.accounts.Account;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Directory;
import android.text.TextUtils;
import android.text.util.Rfc822Token;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AutoCompleteTextView;
import android.widget.BaseAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Adapter for showing a recipient list.
 */
public abstract class BaseRecipientAdapter extends BaseAdapter implements Filterable {
    private static final String TAG = "BaseRecipientAdapter";

    /**
     * The preferred number of results to be retrieved. This number may be
     * exceeded if there are several directories configured, because we will use
     * the same limit for all directories.
     */
    private static final int DEFAULT_PREFERRED_MAX_RESULT_COUNT = 10;

    /**
     * The number of extra entries requested to allow for duplicates. Duplicates
     * are removed from the overall result.
     */
    private static final int ALLOWANCE_FOR_DUPLICATES = 5;

    /**
     * Model object for a {@link Directory} row.
     */
    public final static class DirectorySearchParams {
        public long directoryId;
        public String directoryType;
        public String displayName;
        public String accountName;
        public String accountType;
        public CharSequence constraint;
        public DirectoryFilter filter;
    }

    private static class EmailQuery {
        public static final String[] PROJECTION = {
            Contacts.DISPLAY_NAME,       // 0
            Email.DATA,                  // 1
            Email.CONTACT_ID,            // 2
            Contacts.PHOTO_THUMBNAIL_URI // 3
        };

        public static final int NAME = 0;
        public static final int ADDRESS = 1;
        public static final int CONTACT_ID = 2;
        public static final int PHOTO_THUMBNAIL_URI = 3;
    }

    // TODO: PhoneQuery

    private static class DirectoryListQuery {

        public static final Uri URI =
                Uri.withAppendedPath(ContactsContract.AUTHORITY_URI, "directories");
        public static final String[] PROJECTION = {
            Directory._ID,              // 0
            Directory.ACCOUNT_NAME,     // 1
            Directory.ACCOUNT_TYPE,     // 2
            Directory.DISPLAY_NAME,     // 3
            Directory.PACKAGE_NAME,     // 4
            Directory.TYPE_RESOURCE_ID, // 5
        };

        public static final int ID = 0;
        public static final int ACCOUNT_NAME = 1;
        public static final int ACCOUNT_TYPE = 2;
        public static final int DISPLAY_NAME = 3;
        public static final int PACKAGE_NAME = 4;
        public static final int TYPE_RESOURCE_ID = 5;
    }

    /**
     * An asynchronous filter used for loading two data sets: email rows from the local
     * contact provider and the list of {@link Directory}'s.
     */
    private final class DefaultFilter extends Filter {

        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            FilterResults results = new FilterResults();
            Cursor cursor = null;
            if (!TextUtils.isEmpty(constraint)) {
                Uri uri = Email.CONTENT_FILTER_URI.buildUpon()
                        .appendPath(constraint.toString())
                        .appendQueryParameter(ContactsContract.LIMIT_PARAM_KEY,
                                String.valueOf(mPreferredMaxResultCount))
                        .build();
                cursor = mContentResolver.query(uri, EmailQuery.PROJECTION, null, null, null);
                if (cursor != null) {
                    results.count = cursor.getCount();
                }
            }

            // TODO: implement group feature

            final Cursor directoryCursor = mContentResolver.query(
                    DirectoryListQuery.URI, DirectoryListQuery.PROJECTION, null, null, null);

            results.values = new Cursor[] { directoryCursor, cursor };
            return results;
        }

        @Override
        protected void publishResults(final CharSequence constraint, FilterResults results) {
            if (results.values != null) {
                final Cursor[] cursors = (Cursor[]) results.values;
                // Run on one thread.
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        onFirstDirectoryLoadFinished(constraint, cursors[0], cursors[1]);
                    }
                });
            }
            results.count = getCount();
        }

        @Override
        public CharSequence convertResultToString(Object resultValue) {
            final RecipientListEntry entry = (RecipientListEntry)resultValue;
            final String displayName = entry.getDisplayName();
            final String emailAddress = entry.getDestination();
            if (TextUtils.isEmpty(displayName) || TextUtils.equals(displayName, emailAddress)) {
                 return emailAddress;
            } else {
                return new Rfc822Token(displayName, emailAddress, null).toString();
            }
        }
    }

    /**
     * An asynchronous filter that performs search in a particular directory.
     */
    private final class DirectoryFilter extends Filter {
        private final int mDirectoryIndex;
        private final long mDirectoryId;
        private int mLimit;

        public DirectoryFilter(int directoryIndex, long directoryId) {
            this.mDirectoryIndex = directoryIndex;
            this.mDirectoryId = directoryId;
        }

        public synchronized void setLimit(int limit) {
            this.mLimit = limit;
        }

        public synchronized int getLimit() {
            return this.mLimit;
        }

        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            FilterResults results = new FilterResults();
            if (!TextUtils.isEmpty(constraint)) {
                Uri uri = Email.CONTENT_FILTER_URI.buildUpon()
                        .appendPath(constraint.toString())
                        .appendQueryParameter(ContactsContract.DIRECTORY_PARAM_KEY,
                                String.valueOf(mDirectoryId))
                        .appendQueryParameter(ContactsContract.LIMIT_PARAM_KEY,
                                String.valueOf(getLimit() + ALLOWANCE_FOR_DUPLICATES))
                        .build();
                Cursor cursor = mContentResolver.query(
                        uri, EmailQuery.PROJECTION, null, null, null);
                results.values = cursor;
            }

            // TODO: implement group feature

            return results;
        }

        @Override
        protected void publishResults(final CharSequence constraint, FilterResults results) {
            final Cursor cursor = (Cursor) results.values;
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    onDirectoryLoadFinished(constraint, mDirectoryIndex, cursor);
                }
            });
            results.count = getCount();
        }
    }

    private Context mContext;
    private final ContentResolver mContentResolver;
    private Account mAccount;
    private int mPreferredMaxResultCount;
    private final Handler mHandler = new Handler();

    /**
     * Each destination (an email address or a phone number) is first inserted into mEntryMap and
     * sorted. Duplicates are removed there. After that all the elems inside mEntryMap are copied
     * to mEntry, which will be used to find items in this Adapter.
     */
    private LinkedHashMap<Integer, List<RecipientListEntry>> mEntryMap;
    private List<RecipientListEntry> mEntries;

    private List<DirectorySearchParams> mDirectorySearchParams;

    public BaseRecipientAdapter(Context context) {
        this(context, DEFAULT_PREFERRED_MAX_RESULT_COUNT);
    }

    public BaseRecipientAdapter(Context context, int preferredMaxResultCount) {
        mContext = context;
        mContentResolver = context.getContentResolver();
        mPreferredMaxResultCount = preferredMaxResultCount;
        mEntryMap = new LinkedHashMap<Integer, List<RecipientListEntry>>();
        mDirectorySearchParams = new ArrayList<DirectorySearchParams>();
    }

    /**
     * Set the account when known. Causes the search to prioritize contacts from that account.
     */
    public void setAccount(Account account) {
        mAccount = account;
    }

    /** Will be called from {@link AutoCompleteTextView} to prepare auto-complete list. */
    @Override
    public Filter getFilter() {
        return new DefaultFilter();
    }

    /**
     * Handles the result of the initial call, which brings back the list of directories as well
     * as the search results for the local directories.
     */
    protected void onFirstDirectoryLoadFinished(
            CharSequence constraint, Cursor directoryCursor, Cursor defaultDirectoryCursor) {
        try {
            if (directoryCursor != null) {
                setupOtherDirectories(directoryCursor);
            }

            int limit = 0;

            if (defaultDirectoryCursor != null && defaultDirectoryCursor.getCount() > 0) {
                final int defaultDirectoryCount = defaultDirectoryCursor.getCount();
                mEntryMap.clear();
                putEntriesWithCursor(defaultDirectoryCursor);
                constructEntryList();
                limit = mPreferredMaxResultCount - getCount();
            }

            int count = mDirectorySearchParams.size();
            if (limit > 0) {
                searchOtherDirectories(constraint, limit);
            }
        } finally {
            if (directoryCursor != null) {
                directoryCursor.close();
            }
            if (defaultDirectoryCursor != null) {
                defaultDirectoryCursor.close();
            }
        }
    }

    private void setupOtherDirectories(Cursor directoryCursor) {
        final PackageManager packageManager = mContext.getPackageManager();
        final List<DirectorySearchParams> directories = new ArrayList<DirectorySearchParams>();
        DirectorySearchParams preferredDirectory = null;
        while (directoryCursor.moveToNext()) {
            final long id = directoryCursor.getLong(DirectoryListQuery.ID);

            // Skip the local invisible directory, because the default directory already includes
            // all local results.
            if (id == Directory.LOCAL_INVISIBLE) {
                continue;
            }

            final DirectorySearchParams params = new DirectorySearchParams();
            final String packageName = directoryCursor.getString(DirectoryListQuery.PACKAGE_NAME);
            final int resourceId = directoryCursor.getInt(DirectoryListQuery.TYPE_RESOURCE_ID);
            params.directoryId = id;
            params.displayName = directoryCursor.getString(DirectoryListQuery.DISPLAY_NAME);
            params.accountName = directoryCursor.getString(DirectoryListQuery.ACCOUNT_NAME);
            params.accountType = directoryCursor.getString(DirectoryListQuery.ACCOUNT_TYPE);
            if (packageName != null && resourceId != 0) {
                try {
                    final Resources resources =
                            packageManager.getResourcesForApplication(packageName);
                    params.directoryType = resources.getString(resourceId);
                    if (params.directoryType == null) {
                        Log.e(TAG, "Cannot resolve directory name: "
                                + resourceId + "@" + packageName);
                    }
                } catch (NameNotFoundException e) {
                    Log.e(TAG, "Cannot resolve directory name: "
                            + resourceId + "@" + packageName, e);
                }
            }

            // If an account has been provided and we found a directory that
            // corresponds to that account, place that directory second, directly
            // underneath the local contacts.
            if (mAccount != null && mAccount.name.equals(params.accountName) &&
                    mAccount.type.equals(params.accountType)) {
                preferredDirectory = params;
            } else {
                directories.add(params);
            }
        }

        if (preferredDirectory != null) {
            directories.add(1, preferredDirectory);
        }

        for (DirectorySearchParams partition : directories) {
            mDirectorySearchParams.add(partition);
        }
    }

    /**
     * Starts search in other directories
     */
    private void searchOtherDirectories(CharSequence constraint, int limit) {
        final int count = mDirectorySearchParams.size();
        // Note: skipping the default partition (index 0), which has already been loaded
        for (int i = 1; i < count; i++) {
            final DirectorySearchParams partition = mDirectorySearchParams.get(i);
            partition.constraint = constraint;
            if (partition.filter == null) {
                partition.filter = new DirectoryFilter(i, partition.directoryId);
            }
            partition.filter.setLimit(limit);
            partition.filter.filter(constraint);
        }
    }

    /**
     * Stores each contact information to {@link #mEntryMap}. {@link #mEntries} isn't touched here.
     *
     * In order to make the new information available from outside Adapter,
     * call {@link #constructEntryList()} after this method.
     */
    private void putEntriesWithCursor(Cursor cursor) {
        cursor.move(-1);
        while (cursor.moveToNext()) {
            final String displayName = cursor.getString(EmailQuery.NAME);
            final String emailAddress = cursor.getString(EmailQuery.ADDRESS);
            final int contactId = cursor.getInt(EmailQuery.CONTACT_ID);
            final String photoThumbnailUri = cursor.getString(EmailQuery.PHOTO_THUMBNAIL_URI);

            if (mEntryMap.containsKey(contactId)) {
                // We already have a section for the person.
                final List<RecipientListEntry> entryList = mEntryMap.get(contactId);
                boolean isDuplicate = false;
                for (RecipientListEntry entry : entryList) {
                    String registeredAddress = entry.getDestination();
                    if (TextUtils.equals(registeredAddress, emailAddress)) {
                        isDuplicate = true;
                        break;
                    }
                }
                if (!isDuplicate) {
                    entryList.add(RecipientListEntry.constructSecondLevelEntry(
                            displayName, emailAddress, contactId));
                }
            } else {
                byte[] photoBytes = null;
                if (photoThumbnailUri != null) {
                    // TODO: async
                    final Cursor photoCursor = mContentResolver.query(
                            Uri.parse(photoThumbnailUri),
                            new String[] {
                                Contacts.Photo.PHOTO
                            }, null, null, null);
                    if (photoCursor != null) {
                        try {
                            if (photoCursor.moveToFirst()) {
                                photoBytes = photoCursor.getBlob(0);
                            }
                        } finally {
                            photoCursor.close();
                        }
                    }
                }

                final List<RecipientListEntry> entryList = new ArrayList<RecipientListEntry>();
                entryList.add(RecipientListEntry.constructTopLevelEntry(
                        displayName, emailAddress, contactId, photoBytes));
                mEntryMap.put(contactId, entryList);
            }
        }
    }

    /**
     * Constructs an actual list for this Adapter using {@link #mEntryMap}.
     */
    private void constructEntryList() {
        mEntries = new ArrayList<RecipientListEntry>();
        for (Map.Entry<Integer, List<RecipientListEntry>> mapEntry : mEntryMap.entrySet()) {
            final List<RecipientListEntry> entryList = mapEntry.getValue();
            final int size = entryList.size();
            for (int i = 0; i < size; i++) {
                RecipientListEntry entry = entryList.get(i);
                mEntries.add(entry);
                if (i < size - 1) {
                    mEntries.add(RecipientListEntry.SEP_WITHIN_GROUP);
                }
            }
            mEntries.add(RecipientListEntry.SEP_NORMAL);
        }
        if (mEntries.size() > 1) {
            mEntries.remove(mEntries.size() - 1);
        }

        notifyDataSetChanged();
    }

    public void onDirectoryLoadFinished(
            CharSequence constraint, int partitionIndex, Cursor cursor) {
        if (cursor != null) {
            try {
                if (partitionIndex < mDirectorySearchParams.size()) {
                    final DirectorySearchParams params =
                            mDirectorySearchParams.get(partitionIndex);

                    // Check if the received result matches the current constraint
                    // If not - the user must have continued typing after the request was issued
                    if (TextUtils.equals(constraint, params.constraint)) {
                        putEntriesWithCursor(cursor);
                        constructEntryList();
                    }
                }
            } finally {
                cursor.close();
            }
        }
    }

    public void close() {
    }

    @Override
    public int getCount() {
        return mEntries != null ? mEntries.size() : 0;
    }

    @Override
    public Object getItem(int position) {
        return mEntries != null ? mEntries.get(position) : null;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (mEntries == null) {
            return null;
        }

        final RecipientListEntry entry = mEntries.get(position);
        if (entry.isSeparator()) {
            if (entry == RecipientListEntry.SEP_NORMAL) {
                return inflateSeparatorView(parent);
            } else if (entry == RecipientListEntry.SEP_WITHIN_GROUP) {
                return inflateSeparatorViewWithinGroup(parent);
            } else {
                Log.e(TAG, "Unknown divider type.");
                return null;
            }
        } else {
            String displayName = entry.getDisplayName();
            String emailAddress = entry.getDestination();
            if (TextUtils.isEmpty(displayName) || TextUtils.equals(displayName, emailAddress)) {
                displayName = emailAddress;
                emailAddress = null;
            }

            final View itemView = inflateItemView(parent);
            final TextView displayNameView = getDisplayNameView(itemView);
            final TextView emailAddressView = getDestinationView(itemView);
            final ImageView imageView = getPhotoView(itemView);
            final View photoContainerView = getPhotoContainerView(itemView);
            displayNameView.setText(displayName);
            if (!TextUtils.isEmpty(emailAddress)) {
                emailAddressView.setText(emailAddress);
            }
            if (imageView != null) {
                if (entry.isFirstLevel()) {
                    final byte[] photoBytes = entry.getPhotoBytes();
                    if (photoBytes != null && imageView != null) {
                        Bitmap photo = BitmapFactory.decodeByteArray(
                                photoBytes, 0, photoBytes.length);
                        imageView.setImageBitmap(photo);
                    } else {
                        imageView.setImageResource(getDefaultPhotoResource());
                    }
                } else {
                    displayNameView.setVisibility(View.GONE);
                    if (photoContainerView != null) {
                        photoContainerView.setVisibility(View.GONE);
                    }
                }
            }
            return itemView;
        }
    }

    /**
     * Inflates a View for each item inside auto-complete list. Subclasses must return the View
     * containing two TextViews (for display name and destination) and one ImageView (for photo).
     * The photo View should be surrounded by container (like FrameLayout)
     * @see #getDisplayNameView(View)
     * @see #getDestinationView(View)
     * @see #getPhotoView(View)
     * @see #getPhotoContainerView(View)
     */
    protected abstract View inflateItemView(ViewGroup parent);
    /** Inflates a View for a separator dividing two person or groups. */
    protected abstract View inflateSeparatorView(ViewGroup parent);
    /** Inflates a View for a separator dividing two destinations for a same person or group. */
    protected abstract View inflateSeparatorViewWithinGroup(ViewGroup parent);

    /** Returns TextView in itemView for showing a display name. */
    protected abstract TextView getDisplayNameView(View itemView);
    /**
     * Returns TextView in itemView for showing a destination (an email address or a phone number).
     */
    protected abstract TextView getDestinationView(View itemView);
    /** Returns ImageView in itemView for showing photo image for a person. */
    protected abstract ImageView getPhotoView(View itemView);
    /**
     * Returns a View containing ImageView given by {@link #getPhotoView(View)}. Can be null.
     */
    protected abstract View getPhotoContainerView(View itemView);

    /**
     * Returns a resource ID representing an image which should be shown when ther's no relevant
     * photo is available.
     */
    protected abstract int getDefaultPhotoResource();
}
