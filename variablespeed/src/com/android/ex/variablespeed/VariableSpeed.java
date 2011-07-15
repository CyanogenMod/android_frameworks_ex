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

import android.content.Context;
import android.media.MediaPlayer;
import android.net.Uri;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;

/**
 * This class behaves in a similar fashion to the MediaPlayer, but by using
 * native code it is able to use variable-speed playback.
 * <p>
 * This class is thread-safe. It's not yet perfect though, see the unit tests
 * for details - there is insufficient testing for the concurrent logic. You are
 * probably best advised to use thread confinment until the unit tests are more
 * complete with regards to threading.
 * <p>
 * The easiest way to ensure that calls to this class are not made concurrently
 * (besides only ever accessing it from one thread) is to wrap it in a
 * {@link SingleThreadedMediaPlayerProxy}, designed just for this purpose.
 */
// TODO: There are a couple of NYI still to iron out in this file.
@ThreadSafe
public class VariableSpeed implements MediaPlayerProxy {
    private final Executor mExecutor;
    private final Object lock = new Object();
    @GuardedBy("lock") private MediaPlayerDataSource mDataSource;
    @GuardedBy("lock") private boolean mIsPrepared;
    @GuardedBy("lock") private boolean mHasDuration;
    @GuardedBy("lock") private boolean mHasStartedPlayback;
    @GuardedBy("lock") private CountDownLatch mEngineInitializedLatch;
    @GuardedBy("lock") private CountDownLatch mPlaybackFinishedLatch;
    @GuardedBy("lock") private boolean mHasBeenReleased = true;
    @GuardedBy("lock") private boolean mIsReadyToReUse = true;
    @GuardedBy("lock") private boolean mSkipCompletionReport;
    @GuardedBy("lock") private int mStartPosition;
    @GuardedBy("lock") private float mCurrentPlaybackRate = 1.0f;
    @GuardedBy("lock") private int mDuration;
    @GuardedBy("lock") private MediaPlayer.OnCompletionListener mCompletionListener;

    private VariableSpeed(Executor executor) throws UnsupportedOperationException {
        mExecutor = executor;
        try {
            VariableSpeedNative.loadLibrary();
        } catch (UnsatisfiedLinkError e) {
            throw new UnsupportedOperationException("could not load library", e);
        } catch (SecurityException e) {
            throw new UnsupportedOperationException("could not load library", e);
        }
        reset();
    }

    public static MediaPlayerProxy createVariableSpeed(Executor executor)
            throws UnsupportedOperationException {
        return new SingleThreadedMediaPlayerProxy(new VariableSpeed(executor));
    }

    @Override
    public void setOnCompletionListener(MediaPlayer.OnCompletionListener listener) {
        synchronized (lock) {
            check(!mHasBeenReleased, "has been released, reset before use");
            mCompletionListener = listener;
        }
    }

    @Override
    public void setOnErrorListener(MediaPlayer.OnErrorListener listener) {
        synchronized (lock) {
            check(!mHasBeenReleased, "has been released, reset before use");
            // NYI
        }
    }

    @Override
    public void release() {
        synchronized (lock) {
            if (mHasBeenReleased) {
                return;
            }
            mHasBeenReleased = true;
        }
        stopCurrentPlayback();
        boolean requiresShutdown = false;
        synchronized (lock) {
            requiresShutdown = hasEngineBeenInitialized();
        }
        if (requiresShutdown) {
            VariableSpeedNative.shutdownEngine();
        }
        synchronized (lock) {
            mIsReadyToReUse = true;
        }
    }

    private boolean hasEngineBeenInitialized() {
        return mEngineInitializedLatch.getCount() <= 0;
    }

    private boolean hasPlaybackFinished() {
        return mPlaybackFinishedLatch.getCount() <= 0;
    }

    /**
     * Stops the current playback, returns once it has stopped.
     */
    private void stopCurrentPlayback() {
        boolean isPlaying;
        CountDownLatch engineInitializedLatch;
        CountDownLatch playbackFinishedLatch;
        synchronized (lock) {
            isPlaying = mHasStartedPlayback && !hasPlaybackFinished();
            engineInitializedLatch = mEngineInitializedLatch;
            playbackFinishedLatch = mPlaybackFinishedLatch;
            if (isPlaying) {
                mSkipCompletionReport = true;
            }
        }
        if (isPlaying) {
            waitForLatch(engineInitializedLatch);
            VariableSpeedNative.stopPlayback();
            waitForLatch(playbackFinishedLatch);
        }
    }

    private void waitForLatch(CountDownLatch latch) {
        try {
            boolean success = latch.await(10, TimeUnit.SECONDS);
            if (!success) {
                reportException(new TimeoutException("waited too long"));
            }
        } catch (InterruptedException e) {
            // Preserve the interrupt status, though this is unexpected.
            Thread.currentThread().interrupt();
            reportException(e);
        }
    }

    @Override
    public void setDataSource(Context context, Uri intentUri) {
        checkNotNull(context, "context");
        checkNotNull(intentUri, "intentUri");
        innerSetDataSource(new MediaPlayerDataSource(context, intentUri));
    }

    @Override
    public void setDataSource(String path) {
        checkNotNull(path, "path");
        innerSetDataSource(new MediaPlayerDataSource(path));
    }

    private void innerSetDataSource(MediaPlayerDataSource source) {
        checkNotNull(source, "source");
        synchronized (lock) {
            check(!mHasBeenReleased, "has been released, reset before use");
            check(mDataSource == null, "cannot setDataSource more than once");
            mDataSource = source;
        }
    }

    @Override
    public void reset() {
        boolean requiresRelease;
        synchronized (lock) {
            requiresRelease = !mHasBeenReleased;
        }
        if (requiresRelease) {
            release();
        }
        synchronized (lock) {
            check(mHasBeenReleased && mIsReadyToReUse, "to re-use, must call reset after release");
            mDataSource = null;
            mIsPrepared = false;
            mHasDuration = false;
            mHasStartedPlayback = false;
            mEngineInitializedLatch = new CountDownLatch(1);
            mPlaybackFinishedLatch = new CountDownLatch(1);
            mHasBeenReleased = false;
            mIsReadyToReUse = false;
            mSkipCompletionReport = false;
            mStartPosition = 0;
            mDuration = 0;
        }
    }

    @Override
    public void prepare() throws IOException {
        MediaPlayerDataSource dataSource;
        synchronized (lock) {
            check(!mHasBeenReleased, "has been released, reset before use");
            check(mDataSource != null, "must setDataSource before you prepare");
            check(!mIsPrepared, "cannot prepare more than once");
            mIsPrepared = true;
            dataSource = mDataSource;
        }
        // NYI This should become another executable that we can wait on.
        MediaPlayer mediaPlayer = new MediaPlayer();
        dataSource.setAsSourceFor(mediaPlayer);
        mediaPlayer.prepare();
        synchronized (lock) {
            check(!mHasDuration, "can't have duration, this is impossible");
            mHasDuration = true;
            mDuration = mediaPlayer.getDuration();
        }
        mediaPlayer.release();
    }

    @Override
    public int getDuration() {
        synchronized (lock) {
            check(!mHasBeenReleased, "has been released, reset before use");
            check(mHasDuration, "you haven't called prepare, can't get the duration");
            return mDuration;
        }
    }

    @Override
    public void seekTo(int startPosition) {
        boolean currentlyPlaying;
        MediaPlayerDataSource dataSource;
        synchronized (lock) {
            check(!mHasBeenReleased, "has been released, reset before use");
            check(mHasDuration, "you can't seek until you have prepared");
            currentlyPlaying = mHasStartedPlayback && !hasPlaybackFinished();
            mStartPosition = Math.min(startPosition, mDuration);
            dataSource = mDataSource;
        }
        if (currentlyPlaying) {
            stopAndStartPlayingAgain(dataSource);
        }
    }

    private void stopAndStartPlayingAgain(MediaPlayerDataSource source) {
        stopCurrentPlayback();
        reset();
        innerSetDataSource(source);
        try {
            prepare();
        } catch (IOException e) {
            reportException(e);
            return;
        }
        start();
        return;
    }

    private void reportException(Exception e) {
        // NYI
        e.printStackTrace(System.err);
    }

    @Override
    public void start() {
        MediaPlayerDataSource restartWithThisDataSource = null;
        synchronized (lock) {
            check(!mHasBeenReleased, "has been released, reset before use");
            check(mIsPrepared, "must have prepared before you can start");
            if (!mHasStartedPlayback) {
                // Playback has not started. Start it.
                mHasStartedPlayback = true;
                // TODO: This will be dynamically calculated soon, waiting for a bugfix in media.
                EngineParameters engineParameters = new EngineParameters.Builder()
                        .sampleRate(11025).channels(1)
//                        .sampleRate(44100).channels(2)
                        .initialRate(mCurrentPlaybackRate)
                        .startPositionMillis(mStartPosition).build();
                mExecutor.execute(new PlaybackRunnable(mDataSource, engineParameters));
            } else {
                // Playback has already started. Restart it, without holding the
                // lock.
                restartWithThisDataSource = mDataSource;
            }
        }
        if (restartWithThisDataSource != null) {
            stopAndStartPlayingAgain(restartWithThisDataSource);
        }
    }

    /** A Runnable capable of driving the native audio playback methods. */
    private final class PlaybackRunnable implements Runnable {
        private final MediaPlayerDataSource mInnerSource;
        private final EngineParameters mEngineParameters;

        public PlaybackRunnable(MediaPlayerDataSource source, EngineParameters engineParameters) {
            mInnerSource = source;
            mEngineParameters = engineParameters;
        }

        @Override
        public void run() {
            synchronized (lock) {
                VariableSpeedNative.initializeEngine(mEngineParameters);
                mEngineInitializedLatch.countDown();
            }
            try {
                VariableSpeedNative.startPlayback();
                mInnerSource.playNative();
            } catch (IOException e) {
                // NYI exception handling.
            }
            MediaPlayer.OnCompletionListener completionListener;
            boolean skipThisCompletionReport;
            synchronized (lock) {
                completionListener = mCompletionListener;
                skipThisCompletionReport = mSkipCompletionReport;
                mPlaybackFinishedLatch.countDown();
            }
            if (!skipThisCompletionReport && completionListener != null) {
                completionListener.onCompletion(null);
            }
            // NYI exception handling.
        }
    }

    @Override
    public boolean isPlaying() {
        synchronized (lock) {
            return mHasStartedPlayback && !hasPlaybackFinished();
        }
    }

    @Override
    public int getCurrentPosition() {
        synchronized (lock) {
            if (mHasBeenReleased) {
                return 0;
            }
            if (!mHasStartedPlayback) {
                return 0;
            }
            if (!hasEngineBeenInitialized()) {
                return 0;
            }
            if (!hasPlaybackFinished()) {
                return VariableSpeedNative.getCurrentPosition();
            }
            return mDuration;
        }
    }

    @Override
    public void pause() {
        synchronized (lock) {
            check(!mHasBeenReleased, "has been released, reset before use");
        }
        stopCurrentPlayback();
    }

    public void setVariableSpeed(float rate) {
        // NYI are there situations in which the engine has been destroyed, so
        // that this will segfault?
        synchronized (lock) {
            check(!mHasBeenReleased, "has been released, reset before use");
            if (mHasStartedPlayback) {
                VariableSpeedNative.setVariableSpeed(rate);
            }
            mCurrentPlaybackRate = rate;
        }
    }

    private void check(boolean condition, String exception) {
        if (!condition) {
            throw new IllegalStateException(exception);
        }
    }

    private void checkNotNull(Object argument, String argumentName) {
        if (argument == null) {
            throw new IllegalArgumentException(argumentName + " must not be null");
        }
    }
}
