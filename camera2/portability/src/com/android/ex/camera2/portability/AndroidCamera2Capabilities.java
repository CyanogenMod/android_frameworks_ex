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

import static android.hardware.camera2.CameraCharacteristics.*;

import android.graphics.Point;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.util.Range;
import android.util.Rational;
import android.view.SurfaceHolder;

import com.android.ex.camera2.portability.debug.Log;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * The subclass of {@link CameraCapabilities} for Android Camera 2 API.
 */
public class AndroidCamera2Capabilities extends CameraCapabilities {
    private static Log.Tag TAG = new Log.Tag("AndCam2Capabs");

    private IntegralStringifier mIntStringifier;

    AndroidCamera2Capabilities(CameraCharacteristics p) {
        super(new IntegralStringifier());
        mIntStringifier = (IntegralStringifier) getStringifier();

        StreamConfigurationMap s = p.get(SCALER_STREAM_CONFIGURATION_MAP);

        for (Range<Integer> fpsRange : p.get(CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)) {
            mSupportedPreviewFpsRange.add(new int[] { fpsRange.getLower(), fpsRange.getUpper() });
        }

        // TODO: We only support SurfaceView preview rendering
        mSupportedPreviewSizes.addAll(Size.buildListFromAndroidSizes(Arrays.asList(
                s.getOutputSizes(SurfaceHolder.class))));
        for (int format : s.getOutputFormats()) {
            mSupportedPreviewFormats.add(format);
        }

        // TODO: We only support MediaRecorder videos capture
        mSupportedVideoSizes.addAll(Size.buildListFromAndroidSizes(Arrays.asList(
                s.getOutputSizes(MediaRecorder.class))));

        // TODO: We only support ImageReader image capture
        mSupportedPhotoSizes.addAll(Size.buildListFromAndroidSizes(Arrays.asList(
                s.getOutputSizes(ImageReader.class))));
        mSupportedPhotoFormats.addAll(mSupportedPreviewFormats);

        buildSceneModes(p);
        buildFlashModes(p);
        buildFocusModes(p);
        buildWhiteBalances(p);
        // TODO: Populate mSupportedFeatures

        // TODO: Populate mPreferredPreviewSizeForVideo

        Range<Integer> ecRange = p.get(CONTROL_AE_COMPENSATION_RANGE);
        mMinExposureCompensation = ecRange.getLower();
        mMaxExposureCompensation = ecRange.getUpper();

        Rational ecStep = p.get(CONTROL_AE_COMPENSATION_STEP);
        mExposureCompensationStep = (float) ecStep.getNumerator() / ecStep.getDenominator();

        mMaxNumOfFacesSupported = p.get(STATISTICS_INFO_MAX_FACE_COUNT);
        mMaxNumOfMeteringArea = p.get(CONTROL_MAX_REGIONS_AE);

        // TODO: Populate mMaxZoomRatio
        // TODO: Populate mHorizontalViewAngle
        // TODO: Populate mVerticalViewAngle
        // TODO: Populate mZoomRatioList
        // TODO: Populate mMaxZoomIndex

        if (supports(FocusMode.AUTO)) {
            mMaxNumOfFocusAreas = p.get(CONTROL_MAX_REGIONS_AF);
            if (mMaxNumOfFocusAreas > 0) {
                mSupportedFeatures.add(Feature.FOCUS_AREA);
            }
        }
        if (mMaxNumOfMeteringArea > 0) {
            mSupportedFeatures.add(Feature.METERING_AREA);
        }
    }

    public IntegralStringifier getIntegralStringifier() {
        return mIntStringifier;
    }

    private void buildSceneModes(CameraCharacteristics p) {
        for (int scene : p.get(CONTROL_AVAILABLE_SCENE_MODES)) {
            SceneMode equiv = mIntStringifier.sceneModeFromInt(scene);
            if (equiv != SceneMode.NO_SCENE_MODE) {
                // equiv isn't a default generated because we couldn't handle this mode, so add it
                mSupportedSceneModes.add(equiv);
            }
        }
    }

    private void buildFlashModes(CameraCharacteristics p) {
        mSupportedFlashModes.add(FlashMode.OFF);
        if (p.get(FLASH_INFO_AVAILABLE)) {
            mSupportedFlashModes.add(FlashMode.ON);
            mSupportedFlashModes.add(FlashMode.TORCH);
            // TODO: New modes aren't represented here
        }
    }

    private void buildFocusModes(CameraCharacteristics p) {
        for (int focus : p.get(CONTROL_AF_AVAILABLE_MODES)) {
            FocusMode equiv = mIntStringifier.focusModeFromInt(focus);
            if (equiv != FocusMode.AUTO || focus == CONTROL_AF_MODE_AUTO) {
                // equiv isn't a default generated because we couldn't handle this mode, so add it
                mSupportedFocusModes.add(equiv);
            }
        }
    }

    private void buildWhiteBalances(CameraCharacteristics p) {
        for (int bal : p.get(CONTROL_AWB_AVAILABLE_MODES)) {
            WhiteBalance equiv = mIntStringifier.whiteBalanceFromInt(bal);
            if (equiv != WhiteBalance.AUTO || bal == CONTROL_AWB_MODE_AUTO) {
                // equiv isn't a default generated because we couldn't handle this mode, so add it
                mSupportedWhiteBalances.add(equiv);
            }
        }
    }

    public static class IntegralStringifier extends Stringifier {
        /**
         * Converts the focus mode to API-related integer representation.
         *
         * @param fm The focus mode to convert.
         * @return The corresponding {@code int} used by the camera framework
         *         API, or {@link CONTROL_AF_MODE_AUTO} if that fails.
         */
        public int intify(FocusMode fm) {
            switch (fm) {
                case AUTO:
                    return CONTROL_AF_MODE_AUTO;
                case CONTINUOUS_PICTURE:
                    return CONTROL_AF_MODE_CONTINUOUS_PICTURE;
                case CONTINUOUS_VIDEO:
                    return CONTROL_AF_MODE_CONTINUOUS_VIDEO;
                case EXTENDED_DOF:
                    return CONTROL_AF_MODE_EDOF;
                case FIXED:
                    return CONTROL_AF_MODE_OFF;
                case MACRO:
                    return CONTROL_AF_MODE_MACRO;
                // TODO: New modes aren't represented here
            }
            return CONTROL_AF_MODE_AUTO;
        }

        /**
         * Converts the API-related integer representation of the focus mode to
         * the abstract representation.
         *
         * @param val The integral representation.
         * @return The mode represented by the input integer, or the focus mode
         *         with the lowest ordinal if it cannot be converted.
         */
        public FocusMode focusModeFromInt(int fm) {
            switch (fm) {
                case CONTROL_AF_MODE_AUTO:
                    return FocusMode.AUTO;
                case CONTROL_AF_MODE_CONTINUOUS_PICTURE:
                    return FocusMode.CONTINUOUS_PICTURE;
                case CONTROL_AF_MODE_CONTINUOUS_VIDEO:
                    return FocusMode.CONTINUOUS_VIDEO;
                case CONTROL_AF_MODE_EDOF:
                    return FocusMode.EXTENDED_DOF;
                case CONTROL_AF_MODE_OFF:
                    return FocusMode.FIXED;
                case CONTROL_AF_MODE_MACRO:
                    return FocusMode.MACRO;
                // TODO: New modes aren't represented here
            }
            return FocusMode.values()[0];
        }

        /**
         * Converts the flash mode to API-related integer representation.
         *
         * @param fm The flash mode to convert.
         * @return The corresponding {@code int} used by the camera framework
         *         API, or {@link CONTROL_AF_MODE_AUTO} if that fails.
         */
        public int intify(FlashMode flm) {
            switch (flm) {
                case OFF:
                    return FLASH_MODE_OFF;
                case ON:
                    return FLASH_MODE_SINGLE;
                case TORCH:
                    return FLASH_MODE_TORCH;
                // TODO: New modes aren't represented here
            }
            return FLASH_MODE_OFF;
        }

        /**
         * Converts the API-related integer representation of the flash mode to
         * the abstract representation.
         *
         * @param flm The integral representation.
         * @return The mode represented by the input integer, or the flash mode
         *         with the lowest ordinal if it cannot be converted.
         */
        public FlashMode flashModeFromInt(int flm) {
            switch (flm) {
                case FLASH_MODE_OFF:
                    return FlashMode.OFF;
                case FLASH_MODE_SINGLE:
                    return FlashMode.ON;
                case FLASH_MODE_TORCH:
                    return FlashMode.TORCH;
                // TODO: New modes aren't represented here
            }
            return FlashMode.values()[0];
        }

        /**
         * Converts the scene mode to API-related integer representation.
         *
         * @param fm The scene mode to convert.
         * @return The corresponding {@code int} used by the camera framework
         *         API, or {@link CONTROL_SCENE_MODE_DISABLED} if that fails.
         */
        public int intify(SceneMode sm) {
            switch (sm) {
                case AUTO:
                    return CONTROL_SCENE_MODE_DISABLED;
                case ACTION:
                    return CONTROL_SCENE_MODE_ACTION;
                case BARCODE:
                    return CONTROL_SCENE_MODE_BARCODE;
                case BEACH:
                    return CONTROL_SCENE_MODE_BEACH;
                case CANDLELIGHT:
                    return CONTROL_SCENE_MODE_CANDLELIGHT;
                case FIREWORKS:
                    return CONTROL_SCENE_MODE_FIREWORKS;
                case LANDSCAPE:
                    return CONTROL_SCENE_MODE_LANDSCAPE;
                case NIGHT:
                    return CONTROL_SCENE_MODE_NIGHT;
                case PARTY:
                    return CONTROL_SCENE_MODE_PARTY;
                case PORTRAIT:
                    return CONTROL_SCENE_MODE_PORTRAIT;
                case SNOW:
                    return CONTROL_SCENE_MODE_SNOW;
                case SPORTS:
                    return CONTROL_SCENE_MODE_SPORTS;
                case STEADYPHOTO:
                    return CONTROL_SCENE_MODE_STEADYPHOTO;
                case SUNSET:
                    return CONTROL_SCENE_MODE_SUNSET;
                case THEATRE:
                    return CONTROL_SCENE_MODE_THEATRE;
                // TODO: New modes aren't represented here
            }
            return CONTROL_SCENE_MODE_DISABLED;
        }

        /**
         * Converts the API-related integer representation of the scene mode to
         * the abstract representation.
         *
         * @param sm The integral representation.
         * @return The mode represented by the input integer, or the scene mode
         *         with the lowest ordinal if it cannot be converted.
         */
        public SceneMode sceneModeFromInt(int sm) {
            switch (sm) {
                case CONTROL_SCENE_MODE_DISABLED:
                    return SceneMode.AUTO;
                case CONTROL_SCENE_MODE_ACTION:
                    return SceneMode.ACTION;
                case CONTROL_SCENE_MODE_BARCODE:
                    return SceneMode.BARCODE;
                case CONTROL_SCENE_MODE_BEACH:
                    return SceneMode.BEACH;
                case CONTROL_SCENE_MODE_CANDLELIGHT:
                    return SceneMode.CANDLELIGHT;
                case CONTROL_SCENE_MODE_FIREWORKS:
                    return SceneMode.FIREWORKS;
                case CONTROL_SCENE_MODE_LANDSCAPE:
                    return SceneMode.LANDSCAPE;
                case CONTROL_SCENE_MODE_NIGHT:
                    return SceneMode.NIGHT;
                case CONTROL_SCENE_MODE_PARTY:
                    return SceneMode.PARTY;
                case CONTROL_SCENE_MODE_PORTRAIT:
                    return SceneMode.PORTRAIT;
                case CONTROL_SCENE_MODE_SNOW:
                    return SceneMode.SNOW;
                case CONTROL_SCENE_MODE_SPORTS:
                    return SceneMode.SPORTS;
                case CONTROL_SCENE_MODE_STEADYPHOTO:
                    return SceneMode.STEADYPHOTO;
                case CONTROL_SCENE_MODE_SUNSET:
                    return SceneMode.SUNSET;
                case CONTROL_SCENE_MODE_THEATRE:
                    return SceneMode.THEATRE;
                // TODO: New modes aren't represented here
            }
            return SceneMode.values()[0];
        }

        /**
         * Converts the white balance to API-related integer representation.
         *
         * @param fm The white balance to convert.
         * @return The corresponding {@code int} used by the camera framework
         *         API, or {@link CONTROL_SCENE_MODE_DISABLED} if that fails.
         */
        public int intify(WhiteBalance wb) {
            switch (wb) {
                case AUTO:
                    return CONTROL_AWB_MODE_AUTO;
                case CLOUDY_DAYLIGHT:
                    return CONTROL_AWB_MODE_CLOUDY_DAYLIGHT;
                case DAYLIGHT:
                    return CONTROL_AWB_MODE_DAYLIGHT;
                case FLUORESCENT:
                    return CONTROL_AWB_MODE_FLUORESCENT;
                case INCANDESCENT:
                    return CONTROL_AWB_MODE_INCANDESCENT;
                case SHADE:
                    return CONTROL_AWB_MODE_SHADE;
                case TWILIGHT:
                    return CONTROL_AWB_MODE_TWILIGHT;
                case WARM_FLUORESCENT:
                    return CONTROL_AWB_MODE_WARM_FLUORESCENT;
                // TODO: New modes aren't represented here
            }
            return CONTROL_AWB_MODE_AUTO;
        }

        /**
         * Converts the API-related integer representation of the white balance
         * to the abstract representation.
         *
         * @param wb The integral representation.
         * @return The balance represented by the input integer, or the white
         *         balance with the lowest ordinal if it cannot be converted.
         */
        public WhiteBalance whiteBalanceFromInt(int wb) {
            switch (wb) {
                case CONTROL_AWB_MODE_AUTO:
                    return WhiteBalance.AUTO;
                case CONTROL_AWB_MODE_CLOUDY_DAYLIGHT:
                    return WhiteBalance.CLOUDY_DAYLIGHT;
                case CONTROL_AWB_MODE_DAYLIGHT:
                    return WhiteBalance.DAYLIGHT;
                case CONTROL_AWB_MODE_FLUORESCENT:
                    return WhiteBalance.FLUORESCENT;
                case CONTROL_AWB_MODE_INCANDESCENT:
                    return WhiteBalance.INCANDESCENT;
                case CONTROL_AWB_MODE_SHADE:
                    return WhiteBalance.SHADE;
                case CONTROL_AWB_MODE_TWILIGHT:
                    return WhiteBalance.TWILIGHT;
                case CONTROL_AWB_MODE_WARM_FLUORESCENT:
                    return WhiteBalance.WARM_FLUORESCENT;
                // TODO: New modes aren't represented here
            }
            return WhiteBalance.values()[0];
        }
    }
}
