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

import com.android.ex.carousel.CarouselRS.CarouselCallback;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.renderscript.FileA3D;
import android.renderscript.Mesh;
import android.renderscript.RSSurfaceView;
import android.renderscript.RenderScriptGL;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;

/**
 * <p>
 * This class represents the basic building block for using a 3D Carousel. The Carousel is
 * basically a scene of cards and slots.  The spacing between cards is dictated by the number
 * of slots and the radius. The number of visible cards dictates how far the Carousel can be moved.
 * If the number of cards exceeds the number of slots, then the Carousel will continue to go
 * around until the last card can be seen.
 */
public abstract class CarouselView extends RSSurfaceView {
    private static final boolean USE_DEPTH_BUFFER = true;
    private final int DEFAULT_SLOT_COUNT = 10;
    private final float DEFAULT_RADIUS = 20.0f;
    private final float DEFAULT_SWAY_SENSITIVITY = 0.0f;
    private final float DEFAULT_FRICTION_COEFFICIENT = 10.0f;
    private final float DEFAULT_DRAG_FACTOR = 0.25f;
    private static final String TAG = "CarouselView";
    private static final boolean DBG = false;
    private CarouselRS mRenderScript;
    private RenderScriptGL mRS;
    private Context mContext;
    private boolean mTracking;

    // These shadow the state of the renderer in case the surface changes so the surface
    // can be restored to its previous state.
    private Bitmap mDefaultBitmap;
    private Bitmap mLoadingBitmap;
    private Bitmap mBackgroundBitmap;
    private Bitmap mDefaultLineBitmap = Bitmap.createBitmap(
            new int[] {0x80ffffff, 0xffffffff, 0x80ffffff}, 0, 3, 3, 1, Bitmap.Config.ARGB_4444);
    private Mesh mDefaultGeometry;
    private Mesh mLoadingGeometry;
    private int mCardCount = 0;
    private int mVisibleSlots = 0;
    private float mStartAngle;
    private float mRadius = DEFAULT_RADIUS;
    private float mCardRotation = 0.0f;
    private float mSwaySensitivity = DEFAULT_SWAY_SENSITIVITY;
    private float mFrictionCoefficient = DEFAULT_FRICTION_COEFFICIENT;
    private float mDragFactor = DEFAULT_DRAG_FACTOR;
    private int mSlotCount = DEFAULT_SLOT_COUNT;
    private float mEye[] = { 20.6829f, 2.77081f, 16.7314f };
    private float mAt[] = { 14.7255f, -3.40001f, -1.30184f };
    private float mUp[] = { 0.0f, 1.0f, 0.0f };

    public static class Info {
        public Info(int _resId) { resId = _resId; }
        public int resId; // resource for renderscript resource (e.g. R.raw.carousel)
    }

    public abstract Info getRenderScriptInfo();

    public CarouselView(Context context) {
        this(context, null);
    }

    /**
     * Constructor used when this widget is created from a layout file.
     */
    public CarouselView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        boolean useDepthBuffer = true;
        ensureRenderScript();
        // TODO: add parameters to layout
    }

    private void ensureRenderScript() {
        mRS = createRenderScript(USE_DEPTH_BUFFER);
        mRenderScript = new CarouselRS();
        mRenderScript.init(mRS, getResources(), getRenderScriptInfo().resId);
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        super.surfaceChanged(holder, format, w, h);
        //mRS.contextSetSurface(w, h, holder.getSurface());
        mRenderScript.init(mRS, getResources(), getRenderScriptInfo().resId);
        setSlotCount(mSlotCount);
        createCards(mCardCount);
        setVisibleSlots(mVisibleSlots);
        setCallback(mCarouselCallback);
        setDefaultBitmap(mDefaultBitmap);
        setLoadingBitmap(mLoadingBitmap);
        setDefaultGeometry(mDefaultGeometry);
        setLoadingGeometry(mLoadingGeometry);
        setBackgroundBitmap(mBackgroundBitmap);
        setDetailLineBitmap(mDefaultLineBitmap);
        setStartAngle(mStartAngle);
        setRadius(mRadius);
        setCardRotation(mCardRotation);
        setSwaySensitivity(mSwaySensitivity);
        setFrictionCoefficient(mFrictionCoefficient);
        setDragFactor(mDragFactor);
        setLookAt(mEye, mAt, mUp);
    }

    /**
     * Loads geometry from a resource id.
     *
     * @param resId
     * @return the loaded mesh or null if it cannot be loaded
     */
    public Mesh loadGeometry(int resId) {
        Resources res = mContext.getResources();
        FileA3D model = FileA3D.createFromResource(mRS, res, resId);
        FileA3D.IndexEntry entry = model.getIndexEntry(0);
        if(entry == null || entry.getClassID() != FileA3D.ClassID.MESH) {
            return null;
        }
        return (Mesh) entry.getObject();
    }

    /**
     * Load A3D file from resource.  If resId == 0, will clear geometry for this item.
     * @param n
     * @param resId
     */
    public void setGeometryForItem(int n, Mesh mesh) {
        if (mRenderScript != null) {
            mRenderScript.setGeometry(n, mesh);
        }
    }

    /**
     * Set the number of slots around the Carousel. Basically equivalent to the poles horses
     * might attach to on a real Carousel.
     *
     * @param n the number of slots
     */
    public void setSlotCount(int n) {
        mSlotCount = n;
        if (mRenderScript != null) {
            mRenderScript.setSlotCount(n);
        }
    }

    /**
     * Sets the number of visible slots around the Carousel.  This is primarily used as a cheap
     * form of clipping. The Carousel will never show more than this many cards.
     * @param n the number of visible slots
     */
    public void setVisibleSlots(int n) {
        mVisibleSlots = n;
        if (mRenderScript != null) {
            mRenderScript.setVisibleSlots(n);
        }
    }

    /**
     * This dictates how many cards are in the deck.  If the number of cards is greater than the
     * number of slots, then the Carousel goes around n / slot_count times.
     *
     * Can be called again to increase or decrease the number of cards.
     *
     * @param n the number of cards to create.
     */
    public void createCards(int n) {
        mCardCount = n;
        if (mRenderScript != null) {
            mRenderScript.createCards(n);
        }
    }

    /**
     * This sets the texture on card n.  It should only be called in response to
     * {@link CarouselCallback#onRequestTexture(int)}.  Since there's no guarantee
     * that a given texture is still on the screen, replacing this texture should be done
     * by first setting it to null and then waiting for the next
     * {@link CarouselCallback#onRequestTexture(int)} to swap it with the new one.
     *
     * @param n the card given by {@link CarouselCallback#onRequestTexture(int)}
     * @param bitmap the bitmap image to show
     */
    public void setTextureForItem(int n, Bitmap bitmap) {
        // Also check against mRS, to handle the case where the result is being delivered by a
        // background thread but the sender no longer exists.
        if (mRenderScript != null && mRS != null) {
            if (DBG) Log.v(TAG, "setTextureForItem(" + n + ")");
            mRenderScript.setTexture(n, bitmap);
            if (DBG) Log.v(TAG, "done");
        }
    }

    /**
     * This sets the detail texture that floats above card n. It should only be called in response
     * to {@link CarouselCallback#onRequestDetailTexture(int)}.  Since there's no guarantee
     * that a given texture is still on the screen, replacing this texture should be done
     * by first setting it to null and then waiting for the next
     * {@link CarouselCallback#onRequestDetailTexture(int)} to swap it with the new one.
     *
     * @param n the card to set the help text
     * @param offx an optional offset to apply to the texture, in pixel coordinates
     * @param offy an optional offset to apply to the texture, in pixel coordinates
     * @param bitmap the bitmap to show as the detail
     */
    public void setDetailTextureForItem(int n, float offx, float offy, Bitmap bitmap) {
        if (mRenderScript != null) {
            if (DBG) Log.v(TAG, "setDetailTextureForItem(" + n + ")");
            mRenderScript.setDetailTexture(n, offx, offy, bitmap);
            if (DBG) Log.v(TAG, "done");
        }
    }

    /**
     * Sets the bitmap to show on a card when the card draws the very first time.
     * Generally, this bitmap will only be seen during the first few frames of startup
     * or when the number of cards are changed.  It can be ignored in most cases,
     * as the cards will generally only be in the loading or loaded state.
     *
     * @param bitmap
     */
    public void setDefaultBitmap(Bitmap bitmap) {
        mDefaultBitmap = bitmap;
        if (mRenderScript != null) {
            mRenderScript.setDefaultBitmap(bitmap);
        }
    }

    /**
     * Sets the bitmap to show on the card while the texture is loading. It is set to this
     * value just before {@link CarouselCallback#onRequestTexture(int)} is called and changed
     * when {@link CarouselView#setTextureForItem(int, Bitmap)} is called. It is shared by all
     * cards.
     *
     * @param bitmap
     */
    public void setLoadingBitmap(Bitmap bitmap) {
        mLoadingBitmap = bitmap;
        if (mRenderScript != null) {
            mRenderScript.setLoadingBitmap(bitmap);
        }
    }

    /**
     * Can be used to optionally set the background to a bitmap.
     *
     * @param bitmap
     */
    public void setBackgroundBitmap(Bitmap bitmap) {
        mBackgroundBitmap = bitmap;
        if (mRenderScript != null) {
            mRenderScript.setBackgroundTexture(bitmap);
        }
    }

    /**
     * This texture is used to draw a line from the card alongside the texture detail. The line
     * will be as wide as the texture. It can be used to give the line glow effects as well as
     * allowing other blending effects. It is typically one dimensional, e.g. 3x1.
     *
     * @param bitmap
     */
    public void setDetailLineBitmap(Bitmap bitmap) {
        mDefaultLineBitmap = bitmap;
        if (mRenderScript != null) {
            mRenderScript.setDetailLineTexture(bitmap);
        }
    }

    /**
     * This geometry will be shown when no geometry has been loaded for a given slot. If not set,
     * a quad will be drawn in its place. It is shared for all cards.
     *
     * @param mesh
     */
    public void setDefaultGeometry(Mesh mesh) {
        mDefaultGeometry = mesh;
        if (mRenderScript != null) {
            mRenderScript.setDefaultGeometry(mesh);
        }
    }

    /**
     * This is an intermediate version of the object to show while geometry is loading. If not set,
     * a quad will be drawn in its place.  It is shared for all cards.
     *
     * @param mesh
     */
    public void setLoadingGeometry(Mesh mesh) {
        mLoadingGeometry = mesh;
        if (mRenderScript != null) {
            mRenderScript.setLoadingGeometry(mesh);
        }
    }

    /**
     * Sets the callback for receiving events from RenderScript.
     *
     * @param callback
     */
    public void setCallback(CarouselCallback callback)
    {
        mCarouselCallback = callback;
        if (mRenderScript != null) {
            mRenderScript.setCallback(callback);
        }
    }

    /**
     * Sets the startAngle for the Carousel. The start angle is the first position of the first
     * slot draw.  Cards will be drawn from this angle in a counter-clockwise manner around the
     * Carousel.
     *
     * @param angle the angle, in radians.
     */
    public void setStartAngle(float angle)
    {
        mStartAngle = angle;
        if (mRenderScript != null) {
            mRenderScript.setStartAngle(angle);
        }
    }

    public void setRadius(float radius) {
        mRadius = radius;
        if (mRenderScript != null) {
            mRenderScript.setRadius(radius);
        }
    }

    public void setCardRotation(float cardRotation) {
        mCardRotation = cardRotation;
        if (mRenderScript != null) {
            mRenderScript.setCardRotation(cardRotation);
        }
    }

    public void setSwaySensitivity(float swaySensitivity) {
        mSwaySensitivity = swaySensitivity;
        if (mRenderScript != null) {
            mRenderScript.setSwaySensitivity(swaySensitivity);
        }
    }

    public void setFrictionCoefficient(float frictionCoefficient) {
        mFrictionCoefficient = frictionCoefficient;
        if (mRenderScript != null) {
            mRenderScript.setFrictionCoefficient(frictionCoefficient);
        }
    }

    public void setDragFactor(float dragFactor) {
        mDragFactor = dragFactor;
        if (mRenderScript != null) {
            mRenderScript.setDragFactor(dragFactor);
        }
    }

    public void setLookAt(float[] eye, float[] at, float[] up) {
        mEye = eye;
        mAt = at;
        mUp = up;
        if (mRenderScript != null) {
            mRenderScript.setLookAt(eye, at, up);
        }
    }

    public void requestFirstCardPosition() {
        if (mRenderScript != null) {
            mRenderScript.requestFirstCardPosition();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if(mRS != null) {
            mRS = null;
            destroyRenderScript();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        ensureRenderScript();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        final int action = event.getAction();
        final float x = event.getX();
        final float y = event.getY();

        if (mRenderScript == null) {
            return true;
        }

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                mTracking = true;
                mRenderScript.doStart(x, y);
                break;

            case MotionEvent.ACTION_MOVE:
                if (mTracking) {
                    mRenderScript.doMotion(x, y);
                }
                break;

            case MotionEvent.ACTION_UP:
                mRenderScript.doStop(x, y);
                mTracking = false;
                break;
        }

        return true;
    }

    private final CarouselCallback DEBUG_CALLBACK = new CarouselCallback() {
        @Override
        public void onAnimationStarted() {
            if (DBG) Log.v(TAG, "onAnimationStarted()");
        }

        @Override
        public void onAnimationFinished() {
            if (DBG) Log.v(TAG, "onAnimationFinished()");
        }

        @Override
        public void onCardSelected(int n) {
            if (DBG) Log.v(TAG, "onCardSelected(" + n + ")");
        }

        @Override
        public void onRequestGeometry(int n) {
            if (DBG) Log.v(TAG, "onRequestGeometry(" + n + ")");
        }

        @Override
        public void onInvalidateGeometry(int n) {
            if (DBG) Log.v(TAG, "onInvalidateGeometry(" + n + ")");
        }

        @Override
        public void onRequestTexture(int n) {
            if (DBG) Log.v(TAG, "onRequestTexture(" + n + ")");
        }

        @Override
        public void onInvalidateTexture(int n) {
            if (DBG) Log.v(TAG, "onInvalidateTexture(" + n + ")");
        }

        public void onRequestDetailTexture(int n) {
            if (DBG) Log.v(TAG, "onRequestDetailTexture(" + n + ")");
        }

        public void onInvalidateDetailTexture(int n) {
            if (DBG) Log.v(TAG, "onInvalidateDetailTexture(" + n + ")");
        }

        @Override
        public void onReportFirstCardPosition(int n) {
            Log.v(TAG, "onReportFirstCardPosition(" + n + ")");
        }
    };

    private CarouselCallback mCarouselCallback = DEBUG_CALLBACK;

}
