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

import android.view.View;
import com.android.ex.carousel.CarouselRS.CarouselCallback;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.Bitmap.Config;
import android.renderscript.FileA3D;
import android.renderscript.Float4;
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
    private final int DEFAULT_VISIBLE_DETAIL_COUNT = 3;
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
            new int[] {0x00000000, 0xffffffff, 0x00000000}, 0, 3, 3, 1, Bitmap.Config.ARGB_4444);
    private Mesh mDefaultGeometry;
    private Mesh mLoadingGeometry;
    private int mCardCount = 0;
    private int mVisibleSlots = 0;
    private int mVisibleDetails = DEFAULT_VISIBLE_DETAIL_COUNT;
    private boolean mDrawDetailBelowCard = false;
    private boolean mDrawRuler = true;
    private float mStartAngle;
    private float mRadius = DEFAULT_RADIUS;
    private float mCardRotation = 0.0f;
    private boolean mCardsFaceTangent = false;
    private float mSwaySensitivity = DEFAULT_SWAY_SENSITIVITY;
    private float mFrictionCoefficient = DEFAULT_FRICTION_COEFFICIENT;
    private float mDragFactor = DEFAULT_DRAG_FACTOR;
    private int mSlotCount = DEFAULT_SLOT_COUNT;
    private float mEye[] = { 20.6829f, 2.77081f, 16.7314f };
    private float mAt[] = { 14.7255f, -3.40001f, -1.30184f };
    private float mUp[] = { 0.0f, 1.0f, 0.0f };
    private Float4 mBackgroundColor = new Float4(0.0f, 0.0f, 0.0f, 1.0f);
    private CarouselCallback mCarouselCallback;
    private float mRezInCardCount = 0.0f;
    private long mFadeInDuration = 250L;
    private Bitmap mDetailLoadingBitmap = Bitmap.createBitmap(
            new int[] {0}, 0, 1, 1, 1, Bitmap.Config.ARGB_4444);

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

        setOnLongClickListener(new OnLongClickListener() {
            public boolean onLongClick(View v) {
                if (interpretLongPressEvents()) {
                    mRenderScript.doLongPress();
                    return true;
                } else {
                    return false;
                }
            }
        });
    }

    private void ensureRenderScript() {
        if (mRS == null) {
            RenderScriptGL.SurfaceConfig sc = new RenderScriptGL.SurfaceConfig();
            sc.setDepth(16, 24);
            mRS = createRenderScript(sc);
        }
        if (mRenderScript == null) {
            mRenderScript = new CarouselRS(mRS, getResources(), getRenderScriptInfo().resId);
            mRenderScript.resumeRendering();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        super.surfaceChanged(holder, format, w, h);
        //mRS.contextSetSurface(w, h, holder.getSurface());
        mRenderScript.resumeRendering();
        setSlotCount(mSlotCount);
        createCards(mCardCount);
        setVisibleSlots(mVisibleSlots);
        setVisibleDetails(mVisibleDetails);
        setDrawDetailBelowCard(mDrawDetailBelowCard);
        setDrawRuler(mDrawRuler);
        setCallback(mCarouselCallback);
        setDefaultBitmap(mDefaultBitmap);
        setLoadingBitmap(mLoadingBitmap);
        setDefaultGeometry(mDefaultGeometry);
        setLoadingGeometry(mLoadingGeometry);
        setBackgroundColor(mBackgroundColor.x, mBackgroundColor.y, mBackgroundColor.z,
                mBackgroundColor.w);
        setBackgroundBitmap(mBackgroundBitmap);
        setDetailLineBitmap(mDefaultLineBitmap);
        setStartAngle(mStartAngle);
        setRadius(mRadius);
        setCardRotation(mCardRotation);
        setCardsFaceTangent(mCardsFaceTangent);
        setSwaySensitivity(mSwaySensitivity);
        setFrictionCoefficient(mFrictionCoefficient);
        setDragFactor(mDragFactor);
        setLookAt(mEye, mAt, mUp);
        setRezInCardCount(mRezInCardCount);
        setFadeInDuration(mFadeInDuration);
        setDetailLoadingBitmap(mDetailLoadingBitmap);
    }

    /**
     * Do I want to interpret the long-press gesture? If so, long-presses will cancel the
     * current selection and call the appropriate callbacks. Otherwise, a long press will
     * not be handled any way other than as a continued drag.
     *
     * @return True if we interpret long-presses
     */
    public boolean interpretLongPressEvents() {
        return false;
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
     * Set the number of detail textures that can be visible at one time.
     *
     * @param n the number of slots
     */
    public void setVisibleDetails(int n) {
        mVisibleDetails = n;
        if (mRenderScript != null) {
            mRenderScript.setVisibleDetails(n);
        }
    }

    /**
     * Set whether to draw the detail texture above or below the card.
     *
     * @param below False for above, true for below.
     */
    public void setDrawDetailBelowCard(boolean below) {
        mDrawDetailBelowCard = below;
        if (mRenderScript != null) {
            mRenderScript.setDrawDetailBelowCard(below);
        }
    }

    /**
     * Set whether to draw a ruler from the card to the detail texture
     *
     * @param drawRuler True to draw a ruler, false to draw nothing where the ruler would go.
     */
    public void setDrawRuler(boolean drawRuler) {
        mDrawRuler = drawRuler;
        if (mRenderScript != null) {
            mRenderScript.setDrawRuler(drawRuler);
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

    public int getCardCount() {
        return mCardCount;
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
     * @param n the card to set detail texture for
     * @param offx an optional offset to apply to the texture (in pixels) from top of detail line
     * @param offy an optional offset to apply to the texture (in pixels) from top of detail line
     * @param loffx an optional offset to apply to the line (in pixels) from left edge of card
     * @param loffy an optional offset to apply to the line (in pixels) from top of screen
     * @param bitmap the bitmap to show as the detail
     */
    public void setDetailTextureForItem(int n, float offx, float offy, float loffx, float loffy,
            Bitmap bitmap) {
        if (mRenderScript != null) {
            if (DBG) Log.v(TAG, "setDetailTextureForItem(" + n + ")");
            mRenderScript.setDetailTexture(n, offx, offy, loffx, loffy, bitmap);
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
     * Sets background to specified color.  If a background texture is specified with
     * {@link CarouselView#setBackgroundBitmap(Bitmap)}, then this call has no effect.
     *
     * @param red the amount of red
     * @param green the amount of green
     * @param blue the amount of blue
     * @param alpha the amount of alpha
     */
    public void setBackgroundColor(float red, float green, float blue, float alpha) {
        mBackgroundColor = new Float4(red, green, blue, alpha);
        if (mRenderScript != null) {
            mRenderScript.setBackgroundColor(mBackgroundColor);
        }
    }

    /**
     * Can be used to optionally set the background to a bitmap. When set to something other than
     * null, this overrides {@link CarouselView#setBackgroundColor(Float4)}.
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
     * Can be used to optionally set a "loading" detail bitmap. Typically, this is just a black
     * texture with alpha = 0 to allow details to slowly fade in.
     *
     * @param bitmap
     */
    public void setDetailLoadingBitmap(Bitmap bitmap) {
        mDetailLoadingBitmap = bitmap;
        if (mRenderScript != null) {
            mRenderScript.setDetailLoadingTexture(bitmap);
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

    public void setCardsFaceTangent(boolean faceTangent) {
        mCardsFaceTangent = faceTangent;
        if (mRenderScript != null) {
          mRenderScript.setCardsFaceTangent(faceTangent);
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

    /**
     * This sets the number of cards in the distance that will be shown "rezzing in".
     * These alpha values will be faded in from the background to the foreground over
     * 'n' cards.  A floating point value is used to allow subtly changing the rezzing in
     * position.
     *
     * @param n the number of cards to rez in.
     */
    public void setRezInCardCount(float n) {
        mRezInCardCount = n;
        if (mRenderScript != null) {
            mRenderScript.setRezInCardCount(n);
        }
    }

    /**
     * This sets the duration (in ms) that a card takes to fade in when loaded via a call
     * to {@link CarouselView#setTextureForItem(int, Bitmap)}. The timer starts the
     * moment {@link CarouselView#setTextureForItem(int, Bitmap)} is called and continues
     * until all of the cards have faded in.  Note: using large values will extend the
     * animation until all cards have faded in.
     *
     * @param t
     */
    public void setFadeInDuration(long t) {
        mFadeInDuration = t;
        if (mRenderScript != null) {
            mRenderScript.setFadeInDuration(t);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mRenderScript = null;
        if (mRS != null) {
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
        super.onTouchEvent(event);
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
}
