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

package com.android.ex.variablespeed;

import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * Encapsulates the parameters required to configure the audio engine.
 * <p>
 * You should not need to use this class directly, it exists for the benefit of
 * this package and the classes contained therein.
 */
@Immutable
/*package*/ final class EngineParameters {
    private final int mChannels;
    private final int mSampleRate;
    private final int mTargetFrames;
    private final int mMaxPlayBufferCount;
    private final float mWindowDuration;
    private final float mWindowOverlapDuration;
    private final float mInitialRate;
    private final int mDecodeBufferInitialSize;
    private final int mDecodeBufferMaxSize;
    private final int mStartPositionMillis;

    public int getChannels() {
        return mChannels;
    }

    public int getSampleRate() {
        return mSampleRate;
    }

    public int getTargetFrames() {
        return mTargetFrames;
    }

    public int getMaxPlayBufferCount() {
        return mMaxPlayBufferCount;
    }

    public float getWindowDuration() {
        return mWindowDuration;
    }

    public float getWindowOverlapDuration() {
        return mWindowOverlapDuration;
    }

    public float getInitialRate() {
        return mInitialRate;
    }

    public int getDecodeBufferInitialSize() {
        return mDecodeBufferInitialSize;
    }

    public int getDecodeBufferMaxSize() {
        return mDecodeBufferMaxSize;
    }

    public int getStartPositionMillis() {
        return mStartPositionMillis;
    }

    private EngineParameters(int channels, int sampleRate, int targetFrames,
            int maxPlayBufferCount, float windowDuration, float windowOverlapDuration,
            float initialRate, int decodeBufferInitialSize, int decodeBufferMaxSize,
            int startPositionMillis) {
        mChannels = channels;
        mSampleRate = sampleRate;
        mTargetFrames = targetFrames;
        mMaxPlayBufferCount = maxPlayBufferCount;
        mWindowDuration = windowDuration;
        mWindowOverlapDuration = windowOverlapDuration;
        mInitialRate = initialRate;
        mDecodeBufferInitialSize = decodeBufferInitialSize;
        mDecodeBufferMaxSize = decodeBufferMaxSize;
        mStartPositionMillis = startPositionMillis;
    }

    /**
     * We use the builder pattern to construct an {@link EngineParameters}
     * object.
     * <p>
     * This class is not thread safe, you should confine its use to one thread
     * or provide your own synchronization.
     */
    @NotThreadSafe
    public static class Builder {
        private int mChannels = 2;
        private int mSampleRate = 44100;
        private int mTargetFrames = 1000;
        private int mMaxPlayBufferCount = 2;
        private float mWindowDuration = 0.08f;
        private float mWindowOverlapDuration = 0.008f;
        private float mInitialRate = 1.0f;
        private int mDecodeBufferInitialSize = 5 * 1024;
        private int mDecodeBufferMaxSize = 20 * 1024;
        private int mStartPositionMillis = 0;

        public EngineParameters build() {
            return new EngineParameters(mChannels, mSampleRate, mTargetFrames, mMaxPlayBufferCount,
                    mWindowDuration, mWindowOverlapDuration, mInitialRate,
                    mDecodeBufferInitialSize, mDecodeBufferMaxSize, mStartPositionMillis);
        }

        public Builder channels(int channels) {
            mChannels = channels;
            return this;
        }

        public Builder sampleRate(int sampleRate) {
            mSampleRate = sampleRate;
            return this;
        }

        public Builder targetFrames(int targetFrames) {
            mTargetFrames = targetFrames;
            return this;
        }

        public Builder maxPlayBufferCount(int maxPlayBufferCount) {
            mMaxPlayBufferCount = maxPlayBufferCount;
            return this;
        }

        public Builder windowDuration(int windowDuration) {
            mWindowDuration = windowDuration;
            return this;
        }

        public Builder windowOverlapDuration(int windowOverlapDuration) {
            mWindowOverlapDuration = windowOverlapDuration;
            return this;
        }

        public Builder initialRate(float initialRate) {
            mInitialRate = initialRate;
            return this;
        }

        public Builder decodeBufferInitialSize(int decodeBufferInitialSize) {
            mDecodeBufferInitialSize = decodeBufferInitialSize;
            return this;
        }

        public Builder decodeBufferMaxSize(int decodeBufferMaxSize) {
            mDecodeBufferMaxSize = decodeBufferMaxSize;
            return this;
        }

        public Builder startPositionMillis(int startPositionMillis) {
            mStartPositionMillis = startPositionMillis;
            return this;
        }
    }
}
