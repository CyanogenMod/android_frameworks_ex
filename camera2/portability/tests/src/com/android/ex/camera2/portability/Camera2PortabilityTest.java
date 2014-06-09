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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.android.ex.camera2.portability.AndroidCamera2Capabilities.IntegralStringifier;
import com.android.ex.camera2.portability.CameraCapabilities.FlashMode;
import com.android.ex.camera2.portability.CameraCapabilities.FocusMode;
import com.android.ex.camera2.portability.CameraCapabilities.SceneMode;
import com.android.ex.camera2.portability.CameraCapabilities.Stringifier;
import com.android.ex.camera2.portability.CameraCapabilities.WhiteBalance;
import org.junit.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;

public class Camera2PortabilityTest {
    private <E> void cameraCapabilitiesStringifierEach(Class<E> classy,
                                                       Stringifier strfy,
                                                       String call) throws Exception {
        for(E val : (E[]) classy.getMethod("values").invoke(null)) {
            String valString =
                    (String) Stringifier.class.getMethod("stringify", classy).invoke(strfy, val);
            assertEquals(val,
                    Stringifier.class.getMethod(call, String.class).invoke(strfy, valString));
        }
    }

    @Test
    public void cameraCapabilitiesStringifier() throws Exception {
        Stringifier strfy = new Stringifier();
        cameraCapabilitiesStringifierEach(FocusMode.class, strfy, "focusModeFromString");
        cameraCapabilitiesStringifierEach(FlashMode.class, strfy, "flashModeFromString");
        cameraCapabilitiesStringifierEach(SceneMode.class, strfy, "sceneModeFromString");
        cameraCapabilitiesStringifierEach(WhiteBalance.class, strfy, "whiteBalanceFromString");
    }

    @Test
    public void cameraCapabilitiesStringifierNull() throws Exception {
        Stringifier strfy = new Stringifier();
        assertEquals(strfy.focusModeFromString(null), FocusMode.AUTO);
        assertEquals(strfy.flashModeFromString(null), FlashMode.NO_FLASH);
        assertEquals(strfy.sceneModeFromString(null), SceneMode.NO_SCENE_MODE);
        assertEquals(strfy.whiteBalanceFromString(null), WhiteBalance.AUTO);
    }

    @Test
    public void cameraCapabilitiesStringifierInvalid() throws Exception {
        Stringifier strfy = new Stringifier();
        assertEquals(strfy.focusModeFromString("crap"), FocusMode.AUTO);
        assertEquals(strfy.flashModeFromString("crap"), FlashMode.NO_FLASH);
        assertEquals(strfy.sceneModeFromString("crap"), SceneMode.NO_SCENE_MODE);
        assertEquals(strfy.whiteBalanceFromString("crap"), WhiteBalance.AUTO);
    }

    private void cameraCapabilitiesIntifierEach(int apiVal,
                                                IntegralStringifier intfy,
                                                String call) throws Exception {
        Method toCall = IntegralStringifier.class.getMethod(call, int.class);
        Class<?> returnType = toCall.getReturnType();
        Object returnVal = toCall.invoke(intfy, apiVal);
        assertEquals(apiVal,
                IntegralStringifier.class.getMethod("intify", returnType).invoke(intfy, returnVal));
    }

    @Test
    public void cameraCapabilitiesIntifier() throws Exception {
        IntegralStringifier intstr = new IntegralStringifier();

        // Focus modes
        cameraCapabilitiesIntifierEach(CONTROL_AF_MODE_AUTO, intstr, "focusModeFromInt");
        cameraCapabilitiesIntifierEach(CONTROL_AF_MODE_CONTINUOUS_PICTURE, intstr,
                "focusModeFromInt");
        cameraCapabilitiesIntifierEach(CONTROL_AF_MODE_CONTINUOUS_VIDEO, intstr,
                "focusModeFromInt");
        cameraCapabilitiesIntifierEach(CONTROL_AF_MODE_EDOF, intstr, "focusModeFromInt");
        cameraCapabilitiesIntifierEach(CONTROL_AF_MODE_OFF, intstr, "focusModeFromInt");
        cameraCapabilitiesIntifierEach(CONTROL_AF_MODE_MACRO, intstr, "focusModeFromInt");

        // Flash modes
        cameraCapabilitiesIntifierEach(FLASH_MODE_OFF, intstr, "flashModeFromInt");
        cameraCapabilitiesIntifierEach(FLASH_MODE_SINGLE, intstr, "flashModeFromInt");
        cameraCapabilitiesIntifierEach(FLASH_MODE_TORCH, intstr, "flashModeFromInt");

        // Scene modes
        cameraCapabilitiesIntifierEach(CONTROL_SCENE_MODE_DISABLED, intstr, "sceneModeFromInt");
        cameraCapabilitiesIntifierEach(CONTROL_SCENE_MODE_ACTION, intstr, "sceneModeFromInt");
        cameraCapabilitiesIntifierEach(CONTROL_SCENE_MODE_BARCODE, intstr, "sceneModeFromInt");
        cameraCapabilitiesIntifierEach(CONTROL_SCENE_MODE_BEACH, intstr, "sceneModeFromInt");
        cameraCapabilitiesIntifierEach(CONTROL_SCENE_MODE_CANDLELIGHT, intstr, "sceneModeFromInt");
        cameraCapabilitiesIntifierEach(CONTROL_SCENE_MODE_FIREWORKS, intstr, "sceneModeFromInt");
        cameraCapabilitiesIntifierEach(CONTROL_SCENE_MODE_LANDSCAPE, intstr, "sceneModeFromInt");
        cameraCapabilitiesIntifierEach(CONTROL_SCENE_MODE_NIGHT, intstr, "sceneModeFromInt");
        cameraCapabilitiesIntifierEach(CONTROL_SCENE_MODE_PARTY, intstr, "sceneModeFromInt");
        cameraCapabilitiesIntifierEach(CONTROL_SCENE_MODE_PORTRAIT, intstr, "sceneModeFromInt");
        cameraCapabilitiesIntifierEach(CONTROL_SCENE_MODE_SNOW, intstr, "sceneModeFromInt");
        cameraCapabilitiesIntifierEach(CONTROL_SCENE_MODE_SPORTS, intstr, "sceneModeFromInt");
        cameraCapabilitiesIntifierEach(CONTROL_SCENE_MODE_STEADYPHOTO, intstr, "sceneModeFromInt");
        cameraCapabilitiesIntifierEach(CONTROL_SCENE_MODE_SUNSET, intstr, "sceneModeFromInt");
        cameraCapabilitiesIntifierEach(CONTROL_SCENE_MODE_THEATRE, intstr, "sceneModeFromInt");

        // White balances
        cameraCapabilitiesIntifierEach(CONTROL_AWB_MODE_AUTO, intstr, "whiteBalanceFromInt");
        cameraCapabilitiesIntifierEach(CONTROL_AWB_MODE_CLOUDY_DAYLIGHT, intstr,
                "whiteBalanceFromInt");
        cameraCapabilitiesIntifierEach(CONTROL_AWB_MODE_DAYLIGHT, intstr, "whiteBalanceFromInt");
        cameraCapabilitiesIntifierEach(CONTROL_AWB_MODE_FLUORESCENT, intstr,
                "whiteBalanceFromInt");
        cameraCapabilitiesIntifierEach(CONTROL_AWB_MODE_INCANDESCENT, intstr,
                "whiteBalanceFromInt");
        cameraCapabilitiesIntifierEach(CONTROL_AWB_MODE_SHADE, intstr, "whiteBalanceFromInt");
        cameraCapabilitiesIntifierEach(CONTROL_AWB_MODE_TWILIGHT, intstr, "whiteBalanceFromInt");
        cameraCapabilitiesIntifierEach(CONTROL_AWB_MODE_WARM_FLUORESCENT, intstr,
                "whiteBalanceFromInt");
    }

    // TODO: Add a test checking whether stringification matches API representation

    @Test
    public void cameraCapabilitiesIntsMatchApi2Representations() throws Exception {
        IntegralStringifier intstr = new IntegralStringifier();

        // Focus modes
        assertEquals(intstr.focusModeFromInt(CONTROL_AF_MODE_AUTO), FocusMode.AUTO);
        assertEquals(intstr.focusModeFromInt(CONTROL_AF_MODE_CONTINUOUS_PICTURE),
                FocusMode.CONTINUOUS_PICTURE);
        assertEquals(intstr.focusModeFromInt(CONTROL_AF_MODE_CONTINUOUS_VIDEO),
                FocusMode.CONTINUOUS_VIDEO);
        assertEquals(intstr.focusModeFromInt(CONTROL_AF_MODE_EDOF), FocusMode.EXTENDED_DOF);
        assertEquals(intstr.focusModeFromInt(CONTROL_AF_MODE_OFF), FocusMode.FIXED);
        assertEquals(intstr.focusModeFromInt(CONTROL_AF_MODE_MACRO), FocusMode.MACRO);

        // Flash modes
        assertEquals(intstr.flashModeFromInt(FLASH_MODE_OFF), FlashMode.OFF);
        assertEquals(intstr.flashModeFromInt(FLASH_MODE_SINGLE), FlashMode.ON);
        assertEquals(intstr.flashModeFromInt(FLASH_MODE_TORCH), FlashMode.TORCH);

        // Scene modes
        assertEquals(intstr.sceneModeFromInt(CONTROL_SCENE_MODE_DISABLED), SceneMode.AUTO);
        assertEquals(intstr.sceneModeFromInt(CONTROL_SCENE_MODE_ACTION), SceneMode.ACTION);
        assertEquals(intstr.sceneModeFromInt(CONTROL_SCENE_MODE_BARCODE), SceneMode.BARCODE);
        assertEquals(intstr.sceneModeFromInt(CONTROL_SCENE_MODE_BEACH), SceneMode.BEACH);
        assertEquals(intstr.sceneModeFromInt(CONTROL_SCENE_MODE_CANDLELIGHT),
                SceneMode.CANDLELIGHT);
        assertEquals(intstr.sceneModeFromInt(CONTROL_SCENE_MODE_FIREWORKS), SceneMode.FIREWORKS);
        assertEquals(intstr.sceneModeFromInt(CONTROL_SCENE_MODE_LANDSCAPE), SceneMode.LANDSCAPE);
        assertEquals(intstr.sceneModeFromInt(CONTROL_SCENE_MODE_NIGHT), SceneMode.NIGHT);
        assertEquals(intstr.sceneModeFromInt(CONTROL_SCENE_MODE_PARTY), SceneMode.PARTY);
        assertEquals(intstr.sceneModeFromInt(CONTROL_SCENE_MODE_PORTRAIT), SceneMode.PORTRAIT);
        assertEquals(intstr.sceneModeFromInt(CONTROL_SCENE_MODE_SNOW), SceneMode.SNOW);
        assertEquals(intstr.sceneModeFromInt(CONTROL_SCENE_MODE_SPORTS), SceneMode.SPORTS);
        assertEquals(intstr.sceneModeFromInt(CONTROL_SCENE_MODE_STEADYPHOTO),
                SceneMode.STEADYPHOTO);
        assertEquals(intstr.sceneModeFromInt(CONTROL_SCENE_MODE_SUNSET), SceneMode.SUNSET);
        assertEquals(intstr.sceneModeFromInt(CONTROL_SCENE_MODE_THEATRE), SceneMode.THEATRE);

        // White balances
        assertEquals(intstr.whiteBalanceFromInt(CONTROL_AWB_MODE_AUTO), WhiteBalance.AUTO);
        assertEquals(intstr.whiteBalanceFromInt(CONTROL_AWB_MODE_CLOUDY_DAYLIGHT),
                WhiteBalance.CLOUDY_DAYLIGHT);
        assertEquals(intstr.whiteBalanceFromInt(CONTROL_AWB_MODE_DAYLIGHT), WhiteBalance.DAYLIGHT);
        assertEquals(intstr.whiteBalanceFromInt(CONTROL_AWB_MODE_FLUORESCENT),
                WhiteBalance.FLUORESCENT);
        assertEquals(intstr.whiteBalanceFromInt(CONTROL_AWB_MODE_INCANDESCENT),
                WhiteBalance.INCANDESCENT);
        assertEquals(intstr.whiteBalanceFromInt(CONTROL_AWB_MODE_SHADE), WhiteBalance.SHADE);
        assertEquals(intstr.whiteBalanceFromInt(CONTROL_AWB_MODE_TWILIGHT), WhiteBalance.TWILIGHT);
        assertEquals(intstr.whiteBalanceFromInt(CONTROL_AWB_MODE_WARM_FLUORESCENT),
                WhiteBalance.WARM_FLUORESCENT);
    }
}
