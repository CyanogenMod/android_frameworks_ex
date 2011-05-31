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
import android.os.HandlerThread;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.Photo;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Directory;
import android.text.TextUtils;
import android.text.util.Rfc822Token;
import android.util.Log;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AutoCompleteTextView;
import android.widget.BaseAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Adapter for showing a recipient list.
 */
public abstract class BaseRecipientAdapter extends BaseAdapter implements Filterable {
    private static final String TAG = "BaseRecipientAdapter";
    private static final boolean DEBUG = false;

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

    // This is ContactsContract.PRIMARY_ACCOUNT_NAME. Available from ICS as hidden
    private static final String PRIMARY_ACCOUNT_NAME = "name_for_primary_account";
    // This is ContactsContract.PRIMARY_ACCOUNT_TYPE. Available from ICS as hidden
    private static final String PRIMARY_ACCOUNT_TYPE = "type_for_primary_account";

    /** The number of photos cached in this Adapter. */
    private static final int PHOTO_CACHE_SIZE = 20;

    public static final int QUERY_TYPE_EMAIL = 0;
    public static final int QUERY_TYPE_PHONE = 1;

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
            Email._ID,                   // 3
            Contacts.PHOTO_THUMBNAIL_URI // 4
        };

        public static final int NAME = 0;
        public static final int ADDRESS = 1;
        public static final int CONTACT_ID = 2;
        public static final int DATA_ID = 3;
        public static final int PHOTO_THUMBNAIL_URI = 4;
    }

    private static class PhoneQuery {
        public static final String[] PROJECTION = {
            Contacts.DISPLAY_NAME,       // 0
            Phone.DATA,                  // 1
            Phone.CONTACT_ID,            // 2
            Phone._ID,                   // 3
            Contacts.PHOTO_THUMBNAIL_URI // 4
        };
        public static final int NAME = 0;
        public static final int NUMBER = 1;
        public static final int CONTACT_ID = 2;
        public static final int DATA_ID = 3;
        public static final int PHOTO_THUMBNAIL_URI = 3;
    }

    private static class PhotoQuery {
        public static final String[] PROJECTION = {
            Photo.PHOTO
        };

        public static final int PHOTO = 0;
    }

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
            final FilterResults results = new FilterResults();
            Cursor cursor = null;
            if (!TextUtils.isEmpty(constraint)) {
                cursor = doQuery(constraint, mPreferredMaxResultCount, null);
                if (cursor != null) {
                    results.count = cursor.getCount();
                }
            }

            // TODO: implement group feature

            final Cursor directoryCursor = mContentResolver.query(
                    DirectoryListQuery.URI, DirectoryListQuery.PROJECTION, null, null, null);

            if (DEBUG && cursor == null) {
                Log.w(TAG, "null cursor returned for default Email filter query.");
            }
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
            final RecipientEntry entry = (RecipientEntry)resultValue;
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
        private final DirectorySearchParams mParams;
        private int mLimit;

        public DirectoryFilter(DirectorySearchParams params) {
            this.mParams = params;
        }

        public synchronized void setLimit(int limit) {
            this.mLimit = limit;
        }

        public synchronized int getLimit() {
            return this.mLimit;
        }

        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            final FilterResults results = new FilterResults();
            if (!TextUtils.isEmpty(constraint)) {
                final Cursor cursor = doQuery(constraint, getLimit(), mParams.directoryId);
                if (cursor != null) {
                    results.values = cursor;
                }
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
                    onDirectoryLoadFinished(constraint, mParams, cursor);
                }
            });
            results.count = getCount();
        }
    }

    private final Context mContext;
    private final ContentResolver mContentResolver;
    private final LayoutInflater mInflater;
    private final int mQueryType;
    private Account mAccount;
    private final int mPreferredMaxResultCount;
    private final Handler mHandler = new Handler();

    /**
     * Each destination (an email address or a phone number) with a valid contactId is first
     * inserted into {@link #mEntryMap} and grouped by the contactId.
     * Destinations without valid contactId (possible if they aren't in local storage) are stored
     * in {@link #mNonAggregatedEntries}.
     * Duplicates are removed using {@link #mExistingDestinations}.
     *
     * After having all results from ContentResolver, all elements in mEntryMap are copied to
     * mEntry, which will be used to find items in this Adapter. If the number of contacts in
     * mEntries are less than mPreferredMaxResultCount, contacts in
     * mNonAggregatedEntries are also used.
     */
    private final LinkedHashMap<Integer, List<RecipientEntry>> mEntryMap;
    private final List<RecipientEntry> mNonAggregatedEntries;
    private final List<RecipientEntry> mEntries;
    private final Set<String> mExistingDestinations;

    /**
     * Used to ignore asynchronous queries with a different constraint, which may appear when
     * users type characters quickly.
     */
    private CharSequence mCurrentConstraint;

    private final HandlerThread mPhotoHandlerThread;
    private final Handler mPhotoHandler;
    private final LruCache<Uri, byte[]> mPhotoCacheMap;

    /**
     * Constructor for email queries.
     */
    public BaseRecipientAdapter(Context context) {
        this(context, QUERY_TYPE_EMAIL, DEFAULT_PREFERRED_MAX_RESULT_COUNT);
    }

    public BaseRecipientAdapter(Context context, int queryType) {
        this(context, queryType, DEFAULT_PREFERRED_MAX_RESULT_COUNT);
    }

    public BaseRecipientAdapter(Context context, int queryType, int preferredMaxResultCount) {
        mContext = context;
        mContentResolver = context.getContentResolver();
        mInflater = LayoutInflater.from(context);
        mQueryType = queryType;
        mPreferredMaxResultCount = preferredMaxResultCount;
        mEntryMap = new LinkedHashMap<Integer, List<RecipientEntry>>();
        mNonAggregatedEntries = new ArrayList<RecipientEntry>();
        mEntries = new ArrayList<RecipientEntry>();
        mExistingDestinations = new HashSet<String>();
        mPhotoHandlerThread = new HandlerThread("photo_handler");
        mPhotoHandlerThread.start();
        mPhotoHandler = new Handler(mPhotoHandlerThread.getLooper());
        mPhotoCacheMap = new LruCache<Uri, byte[]>(PHOTO_CACHE_SIZE);
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
     *
     * Must be inside a default Looper thread to avoid synchronization problem.
     */
    protected void onFirstDirectoryLoadFinished(
            CharSequence constraint, Cursor directoryCursor, Cursor defaultDirectoryCursor) {
        mCurrentConstraint = constraint;

        try {
            final List<DirectorySearchParams> paramsList;
            if (directoryCursor != null) {
                paramsList = setupOtherDirectories(directoryCursor);
            } else {
                paramsList = null;
            }

            int limit = 0;

            if (defaultDirectoryCursor != null) {
                mEntryMap.clear();
                mNonAggregatedEntries.clear();
                mExistingDestinations.clear();
                putEntriesWithCursor(defaultDirectoryCursor, true);
                constructEntryList();
                limit = mPreferredMaxResultCount - getCount();
            }

            if (limit > 0 && paramsList != null) {
                searchOtherDirectories(constraint, paramsList, limit);
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

    private List<DirectorySearchParams> setupOtherDirectories(Cursor directoryCursor) {
        final PackageManager packageManager = mContext.getPackageManager();
        final List<DirectorySearchParams> paramsList = new ArrayList<DirectorySearchParams>();
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
                paramsList.add(params);
            }
        }

        if (preferredDirectory != null) {
            paramsList.add(1, preferredDirectory);
        }

        return paramsList;
    }

    /**
     * Starts search in other directories
     */
    private void searchOtherDirectories(
            CharSequence constraint, List<DirectorySearchParams> paramsList, int limit) {
        final int count = paramsList.size();
        // Note: skipping the default partition (index 0), which has already been loaded
        for (int i = 1; i < count; i++) {
            final DirectorySearchParams params = paramsList.get(i);
            params.constraint = constraint;
            if (params.filter == null) {
                params.filter = new DirectoryFilter(params);
            }
            params.filter.setLimit(limit);
            params.filter.filter(constraint);
        }
    }

    /** Must be inside a default Looper thread to avoid synchronization problem. */
    public void onDirectoryLoadFinished(
            CharSequence constraint, DirectorySearchParams params, Cursor cursor) {
        if (cursor != null) {
            try {
                if (DEBUG) {
                    Log.v(TAG, "finished loading directory \"" + params.displayName + "\"" +
                            " with query " + constraint);
                }

                // Check if the received result matches the current constraint
                // If not - the user must have continued typing after the request was issued
                final boolean usesSameConstraint;
                usesSameConstraint = TextUtils.equals(constraint, mCurrentConstraint);
                if (usesSameConstraint) {
                    putEntriesWithCursor(cursor, params.directoryId == Directory.DEFAULT);
                    constructEntryList();
                }
            } finally {
                cursor.close();
            }
        }
    }

    /**
     * Stores each contact information to {@link #mEntryMap}. {@link #mEntries} isn't touched here.
     *
     * In order to make the new information available from outside Adapter,
     * call {@link #constructEntryList()} after this method.
     */
    private void putEntriesWithCursor(Cursor cursor, boolean validContactId) {
        cursor.move(-1);
        while (cursor.moveToNext()) {
            final String displayName;
            final String destination;
            final int contactId;
            final int dataId;
            final String thumbnailUriString;
            if (mQueryType == QUERY_TYPE_EMAIL) {
                displayName = cursor.getString(EmailQuery.NAME);
                destination = cursor.getString(EmailQuery.ADDRESS);
                contactId = cursor.getInt(EmailQuery.CONTACT_ID);
                dataId = cursor.getInt(EmailQuery.DATA_ID);
                thumbnailUriString = cursor.getString(EmailQuery.PHOTO_THUMBNAIL_URI);
            } else if (mQueryType == QUERY_TYPE_PHONE) {
                displayName = cursor.getString(PhoneQuery.NAME);
                destination = cursor.getString(PhoneQuery.NUMBER);
                contactId = cursor.getInt(PhoneQuery.CONTACT_ID);
                dataId = cursor.getInt(PhoneQuery.DATA_ID);
                thumbnailUriString = cursor.getString(PhoneQuery.PHOTO_THUMBNAIL_URI);
            } else {
                throw new IndexOutOfBoundsException("Unexpected query type: " + mQueryType);
            }

            // Note: At this point each entry doesn't contain have any photo (thus getPhotoBytes()
            // returns null).

            if (mExistingDestinations.contains(destination)) {
                continue;
            }
            mExistingDestinations.add(destination);

            if (!validContactId) {
                mNonAggregatedEntries.add(RecipientEntry.constructTopLevelEntry(
                        displayName, destination, contactId, dataId, thumbnailUriString));
            } else if (mEntryMap.containsKey(contactId)) {
                // We already have a section for the person.
                final List<RecipientEntry> entryList = mEntryMap.get(contactId);
                entryList.add(RecipientEntry.constructSecondLevelEntry(
                        displayName, destination, contactId, dataId));
            } else {
                final List<RecipientEntry> entryList = new ArrayList<RecipientEntry>();
                entryList.add(RecipientEntry.constructTopLevelEntry(
                        displayName, destination, contactId, dataId, thumbnailUriString));
                mEntryMap.put(contactId, entryList);
            }
        }
    }

    /**
     * Constructs an actual list for this Adapter using {@link #mEntryMap}. Also tries to
     * fetch a cached photo for each contact entry (other than separators), or request another
     * thread to get one from directories. The thread ({@link #mPhotoHandlerThread}) will
     * request {@link #notifyDataSetChanged()} after having the photo asynchronously.
     */
    private void constructEntryList() {
        mEntries.clear();
        int validEntryCount = 0;
        for (Map.Entry<Integer, List<RecipientEntry>> mapEntry : mEntryMap.entrySet()) {
            final List<RecipientEntry> entryList = mapEntry.getValue();
            final int size = entryList.size();
            for (int i = 0; i < size; i++) {
                RecipientEntry entry = entryList.get(i);
                mEntries.add(entry);
                tryFetchPhoto(entry);
                validEntryCount++;
                if (i < size - 1) {
                    mEntries.add(RecipientEntry.SEP_WITHIN_GROUP);
                }
            }
            mEntries.add(RecipientEntry.SEP_NORMAL);
            if (validEntryCount > mPreferredMaxResultCount) {
                break;
            }
        }
        if (validEntryCount <= mPreferredMaxResultCount) {
            for (RecipientEntry entry : mNonAggregatedEntries) {
                if (validEntryCount > mPreferredMaxResultCount) {
                    break;
                }
                mEntries.add(entry);
                tryFetchPhoto(entry);

                mEntries.add(RecipientEntry.SEP_NORMAL);
                validEntryCount++;
            }
        }

        // Remove last divider
        if (mEntries.size() > 1) {
            mEntries.remove(mEntries.size() - 1);
        }
        notifyDataSetChanged();
    }

    private void tryFetchPhoto(final RecipientEntry entry) {
        final Uri photoThumbnailUri = entry.getPhotoThumbnailUri();
        if (photoThumbnailUri != null) {
            final byte[] photoBytes = mPhotoCacheMap.get(photoThumbnailUri);
            if (photoBytes != null) {
                entry.setPhotoBytes(photoBytes);
                // notifyDataSetChanged() should be called by a caller.
            } else {
                if (DEBUG) {
                    Log.d(TAG, "No photo cache for " + entry.getDisplayName()
                            + ". Fetch one asynchronously");
                }
                fetchPhotoAsync(entry, photoThumbnailUri);
            }
        }
    }

    private void fetchPhotoAsync(final RecipientEntry entry, final Uri photoThumbnailUri) {
        mPhotoHandler.post(new Runnable() {
            @Override
            public void run() {
                final Cursor photoCursor = mContentResolver.query(
                        photoThumbnailUri, PhotoQuery.PROJECTION, null, null, null);
                if (photoCursor != null) {
                    try {
                        if (photoCursor.moveToFirst()) {
                            final byte[] photoBytes = photoCursor.getBlob(PhotoQuery.PHOTO);
                            entry.setPhotoBytes(photoBytes);

                            mHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    mPhotoCacheMap.put(photoThumbnailUri, photoBytes);
                                    notifyDataSetChanged();
                                }
                            });
                        }
                    } finally {
                        photoCursor.close();
                    }
                }
            }
        });
    }

    private Cursor doQuery(CharSequence constraint, int limit, Long directoryId) {
        final Cursor cursor;
        if (mQueryType == QUERY_TYPE_EMAIL) {
            final Uri.Builder builder = Email.CONTENT_FILTER_URI.buildUpon()
                    .appendPath(constraint.toString())
                    .appendQueryParameter(ContactsContract.LIMIT_PARAM_KEY,
                            String.valueOf(limit + ALLOWANCE_FOR_DUPLICATES));
            if (directoryId != null) {
                builder.appendQueryParameter(ContactsContract.DIRECTORY_PARAM_KEY,
                        String.valueOf(directoryId));
            }
            if (mAccount != null) {
                builder.appendQueryParameter(PRIMARY_ACCOUNT_NAME, mAccount.name);
                builder.appendQueryParameter(PRIMARY_ACCOUNT_TYPE, mAccount.type);
            }
            cursor = mContentResolver.query(
                    builder.build(), EmailQuery.PROJECTION, null, null, null);
        } else if (mQueryType == QUERY_TYPE_PHONE){
            final Uri.Builder builder = Phone.CONTENT_FILTER_URI.buildUpon()
                    .appendPath(constraint.toString())
                    .appendQueryParameter(ContactsContract.LIMIT_PARAM_KEY,
                            String.valueOf(limit + ALLOWANCE_FOR_DUPLICATES));
            if (directoryId != null) {
                builder.appendQueryParameter(ContactsContract.DIRECTORY_PARAM_KEY,
                        String.valueOf(directoryId));
            }
            if (mAccount != null) {
                builder.appendQueryParameter(PRIMARY_ACCOUNT_NAME, mAccount.name);
                builder.appendQueryParameter(PRIMARY_ACCOUNT_TYPE, mAccount.type);
            }
            cursor = mContentResolver.query(
                    builder.build(), PhoneQuery.PROJECTION, null, null, null);
        } else {
            cursor = null;
        }
        return cursor;
    }

    public void close() {
        mEntryMap.clear();
        mNonAggregatedEntries.clear();
        mExistingDestinations.clear();
        mEntries.clear();
        mPhotoCacheMap.evictAll();
        if (!mPhotoHandlerThread.quit()) {
            Log.w(TAG, "Failed to quit photo handler thread, ignoring it.");
        }
    }

    @Override
    public int getCount() {
        return mEntries.size();
    }

    @Override
    public Object getItem(int position) {
        return mEntries.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public int getViewTypeCount() {
        return RecipientEntry.ENTRY_TYPE_SIZE;
    }

    @Override
    public int getItemViewType(int position) {
        return mEntries.get(position).getEntryType();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        final RecipientEntry entry = mEntries.get(position);
        switch (entry.getEntryType()) {
            case RecipientEntry.ENTRY_TYPE_SEP_NORMAL: {
                return convertView != null ? convertView
                        : mInflater.inflate(getSeparatorLayout(), parent, false);
            }
            case RecipientEntry.ENTRY_TYPE_SEP_WITHIN_GROUP: {
                return convertView != null ? convertView
                        : mInflater.inflate(getSeparatorWithinGroupLayout(), parent, false);
            }
            default: {
                String displayName = entry.getDisplayName();
                String emailAddress = entry.getDestination();
                if (TextUtils.isEmpty(displayName)
                        || TextUtils.equals(displayName, emailAddress)) {
                    displayName = emailAddress;
                    emailAddress = null;
                }

                final View itemView = convertView != null ? convertView
                        : mInflater.inflate(getItemLayout(), parent, false);
                final TextView displayNameView =
                        (TextView)itemView.findViewById(getDisplayNameId());
                final TextView emailAddressView =
                        (TextView)itemView.findViewById(getDestinationId());
                final ImageView imageView = (ImageView)itemView.findViewById(getPhotoId());
                displayNameView.setText(displayName);
                if (!TextUtils.isEmpty(emailAddress)) {
                    emailAddressView.setText(emailAddress);
                }
                if (entry.isFirstLevel()) {
                    displayNameView.setVisibility(View.VISIBLE);
                    if (imageView != null) {
                        imageView.setVisibility(View.VISIBLE);
                        final byte[] photoBytes = entry.getPhotoBytes();
                        if (photoBytes != null && imageView != null) {
                            final Bitmap photo = BitmapFactory.decodeByteArray(
                                    photoBytes, 0, photoBytes.length);
                            imageView.setImageBitmap(photo);
                        } else {
                            imageView.setImageResource(getDefaultPhotoResource());
                        }
                    }
                } else {
                    displayNameView.setVisibility(View.GONE);
                    if (imageView != null) imageView.setVisibility(View.GONE);
                }
                return itemView;
            }
        }
    }

    /**
     * Returns a layout id for each item inside auto-complete list.
     *
     * Each View must contain two TextViews (for display name and destination) and one ImageView
     * (for photo). Ids for those should be available via {@link #getDisplayNameId()},
     * {@link #getDestinationId()}, and {@link #getPhotoId()}.
     */
    protected abstract int getItemLayout();
    /** Returns a layout id for a separator dividing two person or groups. */
    protected abstract int getSeparatorLayout();
    /**
     * Returns a layout id for a separator dividing two destinations for a same person or group.
     */
    protected abstract int getSeparatorWithinGroupLayout();

    /**
     * Returns a resource ID representing an image which should be shown when ther's no relevant
     * photo is available.
     */
    protected abstract int getDefaultPhotoResource();

    /**
     * Returns an id for TextView in an item View for showing a display name. In default
     * {@link android.R.id#text1} is returned.
     */
    protected int getDisplayNameId() {
        return android.R.id.text1;
    }

    /**
     * Returns an id for TextView in an item View for showing a destination
     * (an email address or a phone number).
     * In default {@link android.R.id#text2} is returned.
     */
    protected int getDestinationId() {
        return android.R.id.text2;
    }

    /**
     * Returns an id for ImageView in an item View for showing photo image for a person. In default
     * {@link android.R.id#icon} is returned.
     */
    protected int getPhotoId() {
        return android.R.id.icon;
    }
}
