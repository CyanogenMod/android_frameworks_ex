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

import android.net.Uri;

/**
 * Represents one entry inside recipient auto-complete list.
 */
public class RecipientEntry {
    /*package*/ static final int INVALID_CONTACT = -1;
    /**
     * A GENERATED_CONTACT is one that was created based entirely on
     * information passed in to the RecipientEntry from an external source
     * that is not a real contact.
     */
    /*package*/ static final int GENERATED_CONTACT = -2;

    public static final int ENTRY_TYPE_PERSON = 0;
    public static final int ENTRY_TYPE_SEP_NORMAL = 1;
    public static final int ENTRY_TYPE_SEP_WITHIN_GROUP = 2;
    public static final int ENTRY_TYPE_WAITING_FOR_DIRECTORY_SEARCH = 3;

    public static final int ENTRY_TYPE_SIZE = 4;

    /** Separator entry dividing two persons or groups. */
    public static final RecipientEntry SEP_NORMAL =
            new RecipientEntry(ENTRY_TYPE_SEP_NORMAL);
    /** Separator entry dividing two entries inside a person or a group. */
    public static final RecipientEntry SEP_WITHIN_GROUP =
            new RecipientEntry(ENTRY_TYPE_SEP_WITHIN_GROUP);
    public static final RecipientEntry WAITING_FOR_DIRECTORY_SEARCH =
            new RecipientEntry(ENTRY_TYPE_WAITING_FOR_DIRECTORY_SEARCH);

    private final int mEntryType;

    /**
     * True when this entry is the first entry in a group, which should have a photo and display
     * name, while the second or later entries won't.
     */
    private boolean mIsFirstLevel;
    private final String mDisplayName;
    /** Destination for this contact entry. Would be an email address or a phone number. */
    private final String mDestination;
    /** ID for the person */
    private final long mContactId;
    /** ID for the destination */
    private final long mDataId;
    private final boolean mIsDivider;

    private final Uri mPhotoThumbnailUri;

    /**
     * This can be updated after this object being constructed, when the photo is fetched
     * from remote directories.
     */
    private byte[] mPhotoBytes;

    private RecipientEntry(int entryType) {
        mEntryType = entryType;
        mDisplayName = null;
        mDestination = null;
        mContactId = -1;
        mDataId = -1;
        mPhotoThumbnailUri = null;
        mPhotoBytes = null;
        mIsDivider = true;
    }

    private RecipientEntry(
            int entryType, String displayName, String destination, long contactId, long dataId,
            Uri photoThumbnailUri, boolean isFirstLevel) {
        mEntryType = entryType;
        mIsFirstLevel = isFirstLevel;
        mDisplayName = displayName;
        mDestination = destination;
        mContactId = contactId;
        mDataId = dataId;
        mPhotoThumbnailUri = photoThumbnailUri;
        mPhotoBytes = null;
        mIsDivider = false;
    }

    /**
     * Determine if this was a RecipientEntry created from recipient info or
     * an entry from contacts.
     */
    public static boolean isCreatedRecipient(long id) {
        return id == RecipientEntry.INVALID_CONTACT || id == RecipientEntry.GENERATED_CONTACT;
    }

    /**
     * Construct a RecipientEntry from just an address that has been entered.
     * This address has not been resolved to a contact and therefore does not
     * have a contact id or photo.
     */
    public static RecipientEntry constructFakeEntry(String address) {
        return new RecipientEntry(ENTRY_TYPE_PERSON, address, address, INVALID_CONTACT,
                INVALID_CONTACT, null, true);
    }

    /**
     * Construct a RecipientEntry from just an address that has been entered
     * with both an associated display name. This address has not been resolved
     * to a contact and therefore does not have a contact id or photo.
     */
    public static RecipientEntry constructGeneratedEntry(String display, String address) {
        return new RecipientEntry(ENTRY_TYPE_PERSON, display, address, GENERATED_CONTACT,
                GENERATED_CONTACT, null, true);
    }

    public static RecipientEntry constructTopLevelEntry(
            String displayName, String destination, long contactId, long dataId,
            Uri photoThumbnailUri) {
        return new RecipientEntry(ENTRY_TYPE_PERSON, displayName, destination, contactId, dataId,
                photoThumbnailUri, true);
    }

    public static RecipientEntry constructTopLevelEntry(
            String displayName, String destination, long contactId, long dataId,
            String thumbnailUriAsString) {
        return new RecipientEntry(
                ENTRY_TYPE_PERSON, displayName, destination, contactId, dataId,
                (thumbnailUriAsString != null ? Uri.parse(thumbnailUriAsString) : null), true);
    }

    public static RecipientEntry constructSecondLevelEntry(
            String displayName, String destination, long contactId, long dataId,
            String thumbnailUriAsString) {
        return new RecipientEntry(
                ENTRY_TYPE_PERSON, displayName, destination, contactId, dataId,
                (thumbnailUriAsString != null ? Uri.parse(thumbnailUriAsString) : null), false);
    }

    public int getEntryType() {
        return mEntryType;
    }

    public String getDisplayName() {
        return mDisplayName;
    }

    public String getDestination() {
        return mDestination;
    }

    public long getContactId() {
        return mContactId;
    }

    public long getDataId() {
        return mDataId;
    }

    public boolean isFirstLevel() {
        return mIsFirstLevel;
    }

    public Uri getPhotoThumbnailUri() {
        return mPhotoThumbnailUri;
    }

    /** This can be called outside main Looper thread. */
    public synchronized void setPhotoBytes(byte[] photoBytes) {
        mPhotoBytes = photoBytes;
    }

    /** This can be called outside main Looper thread. */
    public synchronized byte[] getPhotoBytes() {
        return mPhotoBytes;
    }

    public boolean isSeparator() {
        return mIsDivider;
    }

    public boolean isSelectable() {
        return mEntryType == ENTRY_TYPE_PERSON;
    }
}