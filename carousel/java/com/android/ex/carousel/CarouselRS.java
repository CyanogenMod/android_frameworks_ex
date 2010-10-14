/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.ex.carousel;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.renderscript.*;
import android.renderscript.RenderScript.RSMessage;
import android.util.Log;

import static android.renderscript.Element.*;

/**
 * This is a support class for Carousel renderscript.  It handles most of the low-level interactions
 * with Renderscript as well as dispatching events.
 *
 */
public class CarouselRS  {
    private static final int DEFAULT_VISIBLE_SLOTS = 1;
    private static final int DEFAULT_CARD_COUNT = 0;

    // Client messages *** THIS LIST MUST MATCH THOSE IN carousel.rs ***
    public static final int CMD_CARD_SELECTED = 100;
    public static final int CMD_CARD_LONGPRESS = 110;
    public static final int CMD_REQUEST_TEXTURE = 200;
    public static final int CMD_INVALIDATE_TEXTURE = 210;
    public static final int CMD_REQUEST_GEOMETRY = 300;
    public static final int CMD_INVALIDATE_GEOMETRY = 310;
    public static final int CMD_ANIMATION_STARTED = 400;
    public static final int CMD_ANIMATION_FINISHED = 500;
    public static final int CMD_REQUEST_DETAIL_TEXTURE = 600;
    public static final int CMD_INVALIDATE_DETAIL_TEXTURE = 610;
    public static final int CMD_REPORT_FIRST_CARD_POSITION = 700;
    public static final int CMD_PING = 1000; // for debugging

    private static final String TAG = "CarouselRS";
    private static final int DEFAULT_SLOT_COUNT = 10;
    private static final boolean MIPMAP = false;
    private static final boolean DBG = false;

    private RenderScriptGL mRS;
    private Resources mRes;
    private ScriptC_carousel mScript;
    private ScriptField_Card mCards;
    private ScriptField_FragmentShaderConstants_s mFSConst;
    private ProgramStore mProgramStore;
    private ProgramStore mProgramStoreOpaque;
    private ProgramStore mProgramStoreDetail;
    private ProgramFragment mSingleTextureFragmentProgram;
    private ProgramFragment mMultiTextureFragmentProgram;
    private ProgramVertex mVertexProgram;
    private ProgramRaster mRasterProgram;
    private CarouselCallback mCallback;
    private float[] mEyePoint = new float[] { 2.0f, 0.0f, 0.0f };
    private float[] mAtPoint = new float[] { 0.0f, 0.0f, 0.0f };
    private float[] mUp = new float[] { 0.0f, 1.0f, 0.0f };

    private static final String mSingleTextureShader = new String(
            "varying vec2 varTex0;" +
            "void main() {" +
            "vec2 t0 = varTex0.xy;" +
            "vec4 col = texture2D(UNI_Tex0, t0);" +
            "gl_FragColor = col; " +
            "}");

    private static final String mMultiTextureShader = new String(
            "varying vec2 varTex0;" +
            "void main() {" +
            "vec2 t0 = varTex0.xy;" +
            "vec4 col = texture2D(UNI_Tex0, t0);" +
            "vec4 col2 = texture2D(UNI_Tex1, t0);" +
            "gl_FragColor = mix(col, col2, UNI_fadeAmount);}");

    public static interface CarouselCallback {
        /**
         * Called when a card is selected
         * @param n the id of the card
         */
        void onCardSelected(int n);

        /**
         * Called when a card is long-pressed
         * @param n the id of the card
         */
        void onCardLongPress(int n);

        /**
         * Called when texture is needed for card n.  This happens when the given card becomes
         * visible.
         * @param n the id of the card
         */
        void onRequestTexture(int n);

        /**
         * Called when a texture is no longer needed for card n.  This happens when the card
         * goes out of view.
         * @param n the id of the card
         */
        void onInvalidateTexture(int n);

        /**
         * Called when detail texture is needed for card n.  This happens when the given card
         * becomes visible.
         * @param n the id of the card
         */
        void onRequestDetailTexture(int n);

        /**
         * Called when a detail texture is no longer needed for card n.  This happens when the card
         * goes out of view.
         * @param n the id of the card
         */
        void onInvalidateDetailTexture(int n);

        /**
         * Called when geometry is needed for card n.
         * @param n the id of the card.
         */
        void onRequestGeometry(int n);

        /**
         * Called when geometry is no longer needed for card n. This happens when the card goes
         * out of view.
         * @param n the id of the card
         */
        void onInvalidateGeometry(int n);

        /**
         * Called when card animation (e.g. a fling) has started.
         */
        void onAnimationStarted();

        /**
         * Called when card animation has stopped.
         */
        void onAnimationFinished();

        /**
         * Called when the current position has been requested.
         */
        void onReportFirstCardPosition(int n);
    };

    private RSMessage mRsMessage = new RSMessage() {
        public void run() {
            if (mCallback == null) return;
            switch (mID) {
                case CMD_CARD_SELECTED:
                    mCallback.onCardSelected(mData[0]);
                    break;

                case CMD_CARD_LONGPRESS:
                    mCallback.onCardLongPress(mData[0]);
                    break;

                case CMD_REQUEST_TEXTURE:
                    mCallback.onRequestTexture(mData[0]);
                    break;

                case CMD_INVALIDATE_TEXTURE:
                    setTexture(mData[0], null);
                    mCallback.onInvalidateTexture(mData[0]);
                    break;

                case CMD_REQUEST_DETAIL_TEXTURE:
                    mCallback.onRequestDetailTexture(mData[0]);
                    break;

                case CMD_INVALIDATE_DETAIL_TEXTURE:
                    setDetailTexture(mData[0], 0.0f, 0.0f, 0.0f, 0.0f, null);
                    mCallback.onInvalidateDetailTexture(mData[0]);
                    break;

                case CMD_REQUEST_GEOMETRY:
                    mCallback.onRequestGeometry(mData[0]);
                    break;

                case CMD_INVALIDATE_GEOMETRY:
                    setGeometry(mData[0], null);
                    mCallback.onInvalidateGeometry(mData[0]);
                    break;

                case CMD_ANIMATION_STARTED:
                    mCallback.onAnimationStarted();
                    break;

                case CMD_ANIMATION_FINISHED:
                    mCallback.onAnimationFinished();
                    break;

                case CMD_REPORT_FIRST_CARD_POSITION:
                    mCallback.onReportFirstCardPosition(mData[0]);
                    break;

                case CMD_PING:
                    if (DBG) Log.v(TAG, "PING...");
                    break;

                default:
                    Log.e(TAG, "Unknown RSMessage: " + mID);
            }
        }
    };

    public CarouselRS(RenderScriptGL rs, Resources res, int resId) {
        mRS = rs;
        mRes = res;

        // create the script object
        mScript = new ScriptC_carousel(mRS, mRes, resId, true);
        mRS.mMessageCallback = mRsMessage;
        initProgramStore();
        initFragmentProgram();
        initRasterProgram();
        initVertexProgram();
        setSlotCount(DEFAULT_SLOT_COUNT);
        setVisibleSlots(DEFAULT_VISIBLE_SLOTS);
        createCards(DEFAULT_CARD_COUNT);
        setStartAngle(0.0f);
        setRadius(1.0f);
        setLookAt(mEyePoint, mAtPoint, mUp);
        setRadius(20.0f);
        // Fov: 25
    }

    public void setLookAt(float[] eye, float[] at, float[] up) {
        for (int i = 0; i < 3; i++) {
            mEyePoint[i] = eye[i];
            mAtPoint[i] = at[i];
            mUp[i] = up[i];
        }
        mScript.invoke_lookAt(eye[0], eye[1], eye[2], at[0], at[1], at[2], up[0], up[1], up[2]);
    }

    public void setRadius(float radius) {
        mScript.set_radius(radius);
    }

    public void setCardRotation(float cardRotation) {
        mScript.set_cardRotation(cardRotation);
    }

    public void setCardsFaceTangent(boolean faceTangent) {
        mScript.set_cardsFaceTangent(faceTangent);
    }

    public void setSwaySensitivity(float swaySensitivity) {
        mScript.set_swaySensitivity(swaySensitivity);
    }

    public void setFrictionCoefficient(float frictionCoeff) {
        mScript.set_frictionCoeff(frictionCoeff);
    }

    public void setDragFactor(float dragFactor) {
        mScript.set_dragFactor(dragFactor);
    }

    private void initVertexProgram() {
        ProgramVertex.Builder pvb = new ProgramVertex.Builder(mRS, null, null);
        mVertexProgram = pvb.create();
        ProgramVertex.MatrixAllocation pva = new ProgramVertex.MatrixAllocation(mRS);
        mVertexProgram.bindAllocation(pva);
        pva.setupProjectionNormalized(1, 1);
        mScript.set_vertexProgram(mVertexProgram);
    }

    private void initRasterProgram() {
        ProgramRaster.Builder programRasterBuilder = new ProgramRaster.Builder(mRS);
        mRasterProgram = programRasterBuilder.create();
        //mRasterProgram.setCullMode(CullMode.NONE);
        mScript.set_rasterProgram(mRasterProgram);
    }

    private void initFragmentProgram() {
        //
        // Single texture program
        //
        ProgramFragment.ShaderBuilder pfbSingle = new ProgramFragment.ShaderBuilder(mRS);
        // Specify the resource that contains the shader string
        pfbSingle.setShader(mSingleTextureShader);
        // Tell the builder how many textures we have
        pfbSingle.setTextureCount(1);
        mSingleTextureFragmentProgram = pfbSingle.create();
        // Bind the source of constant data
        mSingleTextureFragmentProgram.bindSampler(Sampler.CLAMP_LINEAR(mRS), 0);

        //
        // Multi texture program
        //
        mFSConst = new ScriptField_FragmentShaderConstants_s(mRS, 1);
        mScript.bind_shaderConstants(mFSConst);
        ProgramFragment.ShaderBuilder pfbMulti = new ProgramFragment.ShaderBuilder(mRS);
        // Specify the resource that contains the shader string
        pfbMulti.setShader(mMultiTextureShader);
        // Tell the builder how many textures we have
        pfbMulti.setTextureCount(2);
        // Define the constant input layout
        pfbMulti.addConstant(mFSConst.getAllocation().getType());
        mMultiTextureFragmentProgram = pfbMulti.create();
        // Bind the source of constant data
        mMultiTextureFragmentProgram.bindConstants(mFSConst.getAllocation(), 0);
        mMultiTextureFragmentProgram.bindSampler(Sampler.CLAMP_LINEAR(mRS), 0);
        mMultiTextureFragmentProgram.bindSampler(Sampler.CLAMP_LINEAR(mRS), 1);

        mScript.set_linearClamp(Sampler.CLAMP_LINEAR(mRS));
        mScript.set_singleTextureFragmentProgram(mSingleTextureFragmentProgram);
        mScript.set_multiTextureFragmentProgram(mMultiTextureFragmentProgram);
    }

    private void initProgramStore() {
        ProgramStore.Builder programStoreBuilder = new ProgramStore.Builder(mRS, null, null);
        programStoreBuilder.setDepthFunc(ProgramStore.DepthFunc.LESS);
        programStoreBuilder.setBlendFunc(ProgramStore.BlendSrcFunc.ONE,
                ProgramStore.BlendDstFunc.ONE_MINUS_SRC_ALPHA);
        programStoreBuilder.setDitherEnable(true);
        programStoreBuilder.setDepthMask(true);
        mProgramStore = programStoreBuilder.create();
        mScript.set_programStore(mProgramStore);

        programStoreBuilder.setBlendFunc(ProgramStore.BlendSrcFunc.ONE,
                ProgramStore.BlendDstFunc.ZERO);
        mProgramStoreOpaque = programStoreBuilder.create();
        mScript.set_programStoreOpaque(mProgramStoreOpaque);

        mProgramStoreDetail = ProgramStore.BLEND_ALPHA_DEPTH_NO_DEPTH(mRS);
        mScript.set_programStoreDetail(mProgramStoreDetail);
    }

    public void createCards(int count)
    {
        // Because RenderScript can't have allocations with 0 dimensions, we always create
        // an allocation of at least one card. This relies on invoke_createCards() to keep
        // track of when the allocation is not valid.
        if (mCards != null) {
            // resize the array
            ScriptField_Card tmpcards = new ScriptField_Card(mRS, count > 0 ? count : 1);
            mScript.bind_tmpCards(tmpcards);
            mScript.invoke_copyCards(count);
            mScript.bind_cards(tmpcards);
            mScript.bind_tmpCards(null);
            mCards = tmpcards;
        } else {
            // create array from scratch
            mCards = new ScriptField_Card(mRS, count > 0 ? count : 1);
            mScript.bind_cards(mCards);
            mScript.invoke_createCards(count);
        }
    }

    public void setVisibleSlots(int count)
    {
        mScript.set_visibleSlotCount(count);
    }

    public void setVisibleDetails(int count) {
        mScript.set_visibleDetailCount(count);
    }

    public void setPrefetchCardCount(int count) {
        mScript.set_prefetchCardCount(count);
    }


    public void setDrawDetailBelowCard(boolean below) {
        mScript.set_drawDetailBelowCard(below);
    }

    public void setDetailTexturesCentered(boolean centered) {
        mScript.set_detailTexturesCentered(centered);
    }

    public void setDrawCardsWithBlending(boolean enabled) {
        mScript.set_drawCardsWithBlending(enabled);
    }

    public void setDrawRuler(boolean drawRuler) {
        mScript.set_drawRuler(drawRuler);
    }

    public void setDefaultBitmap(Bitmap bitmap)
    {
        mScript.set_defaultTexture(allocationFromBitmap(bitmap, MIPMAP));
    }

    public void setLoadingBitmap(Bitmap bitmap)
    {
        mScript.set_loadingTexture(allocationFromBitmap(bitmap, MIPMAP));
    }

    public void setDefaultGeometry(Mesh mesh)
    {
        mScript.set_defaultGeometry(mesh);
    }

    public void setLoadingGeometry(Mesh mesh)
    {
        mScript.set_loadingGeometry(mesh);
    }

    public void setStartAngle(float theta)
    {
        mScript.set_startAngle(theta);
    }

    public void setCallback(CarouselCallback callback)
    {
        mCallback = callback;
    }

    private Allocation allocationFromBitmap(Bitmap bitmap, boolean mipmap)
    {
        if (bitmap == null) return null;
        Allocation allocation = Allocation.createFromBitmap(mRS, bitmap,
                elementForBitmap(bitmap, Bitmap.Config.ARGB_4444), mipmap);
        allocation.uploadToTexture(0);
        return allocation;
    }

    public void setTexture(int n, Bitmap bitmap)
    {
        if (n < 0) throw new IllegalArgumentException("Index cannot be negative");

        synchronized(this) {
            ScriptField_Card.Item item = mCards.get(n);
            if (item == null) {
                if (DBG) Log.v(TAG, "setTexture(): no item at index " + n);
                item = new ScriptField_Card.Item();
            }
            if (bitmap != null) {
                if (DBG) Log.v(TAG, "creating new bitmap");
                item.texture = Allocation.createFromBitmap(mRS, bitmap,
                        elementForBitmap(bitmap, Bitmap.Config.ARGB_4444), MIPMAP);
                if (DBG) Log.v(TAG, "uploadToTexture(" + n + ")");
                item.texture.uploadToTexture(0);
                if (DBG) Log.v(TAG, "done...");
            } else {
                if (item.texture != null) {
                    if (DBG) Log.v(TAG, "unloading texture " + n);
                    // Don't wait for GC to free native memory.
                    // Only works if textures are not shared.
                    item.texture.destroy();
                    item.texture = null;
                }
            }
            mCards.set(item, n, false); // This is primarily used for reference counting.
            mScript.invoke_setTexture(n, item.texture);
        }
    }

    void setDetailTexture(int n, float offx, float offy, float loffx, float loffy, Bitmap bitmap)
    {
        if (n < 0) throw new IllegalArgumentException("Index cannot be negative");

        synchronized(this) {
            ScriptField_Card.Item item = mCards.get(n);
            if (item == null) {
                if (DBG) Log.v(TAG, "setDetailTexture(): no item at index " + n);
                item = new ScriptField_Card.Item();
            }
            float width = 0.0f;
            float height = 0.0f;
            if (bitmap != null) {
                item.detailTexture = Allocation.createFromBitmap(mRS, bitmap,
                        elementForBitmap(bitmap, Bitmap.Config.ARGB_4444), MIPMAP);
                item.detailTexture.uploadToTexture(0);
                width = bitmap.getWidth();
                height = bitmap.getHeight();
            } else {
                if (item.detailTexture != null) {
                    if (DBG) Log.v(TAG, "unloading texture " + n);
                    // Don't wait for GC to free native memory.
                    // Only works if textures are not shared.
                    item.detailTexture.destroy();
                    item.detailTexture = null;
                }
            }
            mCards.set(item, n, false); // This is primarily used for reference counting.
            mScript.invoke_setDetailTexture(n, offx, offy, loffx, loffy, item.detailTexture);
        }
    }

    public void setGeometry(int n, Mesh geometry)
    {
        if (n < 0) throw new IllegalArgumentException("Index cannot be negative");

        synchronized(this) {
            final boolean mipmap = false;
            ScriptField_Card.Item item = mCards.get(n);
            if (item == null) {
                if (DBG) Log.v(TAG, "setGeometry(): no item at index " + n);
                item = new ScriptField_Card.Item();
            }
            if (geometry != null) {
                item.geometry = geometry;
            } else {
                if (DBG) Log.v(TAG, "unloading geometry " + n);
                if (item.geometry != null) {
                    // item.geometry.destroy();
                    item.geometry = null;
                }
            }
            mCards.set(item, n, false);
            mScript.invoke_setGeometry(n, item.geometry);
        }
    }

    public void setBackgroundColor(Float4 color) {
        mScript.set_backgroundColor(color);
    }

    public void setBackgroundTexture(Bitmap bitmap) {
        Allocation texture = null;
        if (bitmap != null) {
            texture = Allocation.createFromBitmap(mRS, bitmap,
                    elementForBitmap(bitmap, Bitmap.Config.RGB_565), MIPMAP);
            texture.uploadToTexture(0);
        }
        mScript.set_backgroundTexture(texture);
    }

    public void setDetailLineTexture(Bitmap bitmap) {
        Allocation texture = null;
        if (bitmap != null) {
            texture = Allocation.createFromBitmap(mRS, bitmap,
                    elementForBitmap(bitmap, Bitmap.Config.ARGB_4444), MIPMAP);
            texture.uploadToTexture(0);
        }
        mScript.set_detailLineTexture(texture);
    }

    public void setDetailLoadingTexture(Bitmap bitmap) {
        Allocation texture = null;
        if (bitmap != null) {
            texture = Allocation.createFromBitmap(mRS, bitmap,
                    elementForBitmap(bitmap, Bitmap.Config.ARGB_4444), MIPMAP);
            texture.uploadToTexture(0);
        }
        mScript.set_detailLoadingTexture(texture);
    }

    public void pauseRendering() {
        // Used to update multiple states at once w/o redrawing for each.
        mRS.contextBindRootScript(null);
    }

    public void resumeRendering() {
        mRS.contextBindRootScript(mScript);
    }

    public void doLongPress() {
        mScript.invoke_doLongPress();
    }

    public void doMotion(float x, float y) {
        mScript.invoke_doMotion(x,y);
    }

    public void doSelection(float x, float y) {
        mScript.invoke_doSelection(x, y);
    }

    public void doStart(float x, float y) {
        mScript.invoke_doStart(x, y);
    }

    public void doStop(float x, float y) {
        mScript.invoke_doStop(x, y);
    }

    public void setSlotCount(int n) {
        mScript.set_slotCount(n);
    }

    public void requestFirstCardPosition() {
        mScript.invoke_requestFirstCardPosition();
    }

    public void setRezInCardCount(float alpha) {
        mScript.set_rezInCardCount(alpha);
    }

    public void setFadeInDuration(long t) {
        mScript.set_fadeInDuration((int)t); // TODO: Remove cast when RS supports exporting longs
    }

    private Element elementForBitmap(Bitmap bitmap, Bitmap.Config defaultConfig) {
        Bitmap.Config config = bitmap.getConfig();
        if (config == null) {
            config = defaultConfig;
        }
        if (config == Bitmap.Config.ALPHA_8) {
            return A_8(mRS);
        } else if (config == Bitmap.Config.RGB_565) {
            return RGB_565(mRS);
        } else if (config == Bitmap.Config.ARGB_4444) {
            return RGBA_4444(mRS);
        } else if (config == Bitmap.Config.ARGB_8888) {
            return RGBA_8888(mRS);
        } else {
            throw new IllegalArgumentException("Unknown configuration");
        }
    }
}
