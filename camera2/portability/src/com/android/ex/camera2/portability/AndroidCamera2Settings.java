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

import android.hardware.camera2.CaptureRequest;

/**
 * The subclass of {@link CameraSettings} for Android Camera 2 API.
 */
public class AndroidCamera2Settings extends CameraSettings {
    // TODO: Implement more completely
    public AndroidCamera2Settings(AndroidCamera2Capabilities capabilities,
                                  CaptureRequest.Builder request,
                                  Size preview, Size photo) {
        // TODO: Support zoom
        setZoomRatio(1.0f);
        setZoomIndex(0);

        // TODO: Set exposure compensation

        AndroidCamera2Capabilities.IntegralStringifier stringifier =
                capabilities.getIntegralStringifier();
        setFocusMode(stringifier.focusModeFromInt(request.get(CaptureRequest.CONTROL_AF_MODE)));
        setFlashMode(stringifier.flashModeFromInt(request.get(CaptureRequest.FLASH_MODE)));
        setSceneMode(stringifier.sceneModeFromInt(request.get(CaptureRequest.CONTROL_SCENE_MODE)));

        // This is mutability-safe because those setters copy the Size objects
        setPreviewSize(preview);
        setPhotoSize(photo);

        // TODO: Initialize formats, too
    }
}
