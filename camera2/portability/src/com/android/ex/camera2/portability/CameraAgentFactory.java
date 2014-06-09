/*
 * Copyright (C) 2013 The Android Open Source Project
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

import android.content.Context;

/**
 * A factory class for {@link CameraAgent}.
 */
public class CameraAgentFactory {

    private static CameraAgent sAndroidCameraAgent;
    private static int sAndroidCameraAgentClientCount;

    /**
     * Returns the android camera implementation of {@link com.android.camera.cameradevice.CameraAgent}.
     *
     * @return The {@link CameraAgent} to control the camera device.
     */
    public static synchronized CameraAgent getAndroidCameraAgent(Context context) {
        if (sAndroidCameraAgent == null) {
            if (false) {
                sAndroidCameraAgent = new AndroidCamera2AgentImpl(context);
            } else {
                sAndroidCameraAgent = new AndroidCameraAgentImpl();
            }
            sAndroidCameraAgentClientCount = 1;
        } else {
            ++sAndroidCameraAgentClientCount;
        }
        return sAndroidCameraAgent;
    }

    /**
     * Recycles the resources. Always call this method when the activity is
     * stopped.
     */
    public static synchronized void recycle() {
        if (--sAndroidCameraAgentClientCount == 0 && sAndroidCameraAgent != null) {
            sAndroidCameraAgent.recycle();
            sAndroidCameraAgent = null;
        }
    }
}
