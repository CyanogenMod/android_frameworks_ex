/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.ex.camera2.portability;

import android.hardware.Camera;

import com.android.ex.camera2.portability.debug.Log;

/**
 * The device info for all attached cameras.
 */
public interface CameraDeviceInfo {

    static final int NO_DEVICE = -1;

    /**
     * @param cameraId Which device to interrogate.
     * @return The static characteristics of the specified device, or {@code null} on error.
     */
    Characteristics getCharacteristics(int cameraId);

    /**
     * @return The total number of the available camera devices.
     */
    int getNumberOfCameras();

    /**
     * @return The first (lowest) ID of the back cameras or {@code NO_DEVICE}
     *         if not available.
     */
    int getFirstBackCameraId();

    /**
     * @return The first (lowest) ID of the front cameras or {@code NO_DEVICE}
     *         if not available.
     */
    int getFirstFrontCameraId();

    /**
     * Device characteristics for a single camera.
     */
    public abstract class Characteristics {
        private static final Log.Tag TAG = new Log.Tag("CamDvcInfChar");

        /**
         * @return Whether the camera faces the back of the device.
         */
        public abstract boolean isFacingBack();

        /**
         * @return Whether the camera faces the device's screen.
         */
        public abstract boolean isFacingFront();

        /**
         * @return The camera image orientation, or the clockwise rotation angle
         *         that must be applied to display it in its natural orientation
         *         (in degrees, always a multiple of 90, and between [90,270]).
         */
        public abstract int getSensorOrientation();

        /**
         * @param currentDisplayOrientation
         *          The current display orientation, as measured clockwise from
         *          the device's natural orientation (in degrees, always a
         *          multiple of 90, and between 0 and 270, inclusive).
         * @return
         *          The relative preview image orientation, or the clockwise
         *          rotation angle that must be applied to display preview
         *          frames in the matching orientation, accounting for implicit
         *          mirroring, if applicable (in degrees, always a multiple of
         *          90, and between 0 and 270, inclusive).
         */
        public int getPreviewOrientation(int currentDisplayOrientation) {
            // Drivers tend to mirror the image during front camera preview.
            return getRelativeImageOrientation(currentDisplayOrientation, true);
        }

        /**
         * @param currentDisplayOrientation
         *          The current display orientation, as measured clockwise from
         *          the device's natural orientation (in degrees, always a
         *          multiple of 90, and between 0 and 270, inclusive).
         * @return
         *          The relative capture image orientation, or the clockwise
         *          rotation angle that must be applied to display these frames
         *          in the matching orientation (in degrees, always a multiple
         *          of 90, and between 0 and 270, inclusive).
         */
        public int getJpegOrientation(int currentDisplayOrientation) {
            // Don't mirror during capture!
            return getRelativeImageOrientation(currentDisplayOrientation, false);
        }

        /**
         * @param currentDisplayOrientaiton
         *          {@link #getPreviewOrientation}, {@link #getJpegOrientation}
         * @param compensateForMirroring
         *          Whether to account for mirroring in the case of front-facing
         *          cameras, which is necessary iff the OS/driver is
         *          automatically reflecting the image.
         * @return
         *          {@link #getPreviewOrientation}, {@link #getJpegOrientation}
         *
         * @see android.hardware.Camera.setDisplayOrientation
         */
        protected int getRelativeImageOrientation(int currentDisplayOrientation,
                                                  boolean compensateForMirroring) {
            if (currentDisplayOrientation % 90 != 0) {
                Log.e(TAG, "Provided display orientation is not divisible by 90");
            }
            if (currentDisplayOrientation < 0 || currentDisplayOrientation > 270) {
                Log.e(TAG, "Provided display orientation is outside expected range");
            }

            int result = 0;
            if (isFacingFront()) {
                result = (getSensorOrientation() + currentDisplayOrientation) % 360;
                if (compensateForMirroring) {
                    result = (360 - result) % 360;
                }
            } else if (isFacingBack()) {
                result = (getSensorOrientation() - currentDisplayOrientation + 360) % 360;
            } else {
                Log.e(TAG, "Camera is facing unhandled direction");
            }
            return result;
        }

        /**
         * @return Whether the shutter sound can be disabled.
         */
        public abstract boolean canDisableShutterSound();
    }
}
