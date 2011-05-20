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

/**
 * Represents one entry inside recipient auto-complete list.
 */
public class RecipientListEntry {
    /** Separator entry dividing two persons or groups. */
    public static final RecipientListEntry SEP_NORMAL = new RecipientListEntry();
    /** Separator entry dividing two entries inside a person or a group. */
    public static final RecipientListEntry SEP_WITHIN_GROUP = new RecipientListEntry();

    /**
     * True when this entry is the first entry in a group, which should have a photo and display
     * name, while the second or later entries won't.
     */
    private boolean mIsFirstLevel;
    private final String mDisplayName;
    /** Destination for this contact entry. Would be an email address or a phone number. */
    private final String mDestination;
    private final int mContactId;
    private final boolean mIsDivider;
    private byte[] mPhotoBytes;

    private RecipientListEntry() {
        mDisplayName = null;
        mDestination = null;
        mContactId = -1;
        mPhotoBytes = null;
        mIsDivider = true;
    }

    private RecipientListEntry(String displayName, String destination, int contactId) {
        mIsFirstLevel = false;
        mDisplayName = displayName;
        mDestination = destination;
        mContactId = contactId;
        mIsDivider = false;
    }

    private RecipientListEntry(
            String displayName, String destination, int contactId, byte[] photoBytes) {
        mIsFirstLevel = true;
        mDisplayName = displayName;
        mDestination = destination;
        mContactId = contactId;
        mIsDivider = false;
        mPhotoBytes = photoBytes;
    }

    public static RecipientListEntry constructTopLevelEntry(
            String displayName, String destination, int contactId, byte[] photoBytes) {
        return new RecipientListEntry(displayName, destination, contactId, photoBytes);
    }

    public static RecipientListEntry constructSecondLevelEntry(
            String displayName, String destination, int contactId) {
        return new RecipientListEntry(displayName, destination, contactId);
    }

    public String getDisplayName() {
        return mDisplayName;
    }

    public String getDestination() {
        return mDestination;
    }

    public int getContactId() {
        return mContactId;
    }

    public boolean isFirstLevel() {
        return mIsFirstLevel;
    }

    public byte[] getPhotoBytes() {
        return mPhotoBytes;
    }

    public boolean isSeparator() {
        return mIsDivider;
    }
}