package com.android.ex.camera2.portability;

import android.hardware.Camera;

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
    public interface Characteristics {
        /**
         * @return Whether the camera faces the back of the device.
         */
        boolean isFacingBack();

        /**
         * @return Whether the camera faces the device's screen.
         */
        boolean isFacingFront();

        /**
         * @return The camera image orientation, or the clockwise rotation angle
         *         that must be applied to display it in its natural orientation
         *         (in degrees, and always a multiple of 90).
         */
        int getSensorOrientation();

        /**
         * @return Whether the shutter sound can be disabled.
         */
        boolean canDisableShutterSound();
    }
}
