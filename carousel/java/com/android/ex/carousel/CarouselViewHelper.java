package com.android.ex.carousel;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.renderscript.Mesh;
import android.util.Log;

import com.android.ex.carousel.CarouselRS.CarouselCallback;

/**
 * CarouselViewHelper wraps all of the threading and event handling of the CarouselView,
 * providing a simpler interface.  Most users will just need to implement a handful of
 * methods to get an application working.
 *
 */
public class CarouselViewHelper implements CarouselCallback {
    private static final String TAG = "CarouselViewHelper";
    private static final int SET_TEXTURE_N = 1;
    private static final int SET_DETAIL_TEXTURE_N = 2;
    private static final int SET_GEOMETRY_N = 3;

    // This is an ordered list of base message ids to allow removal of a single item from the
    // list for a particular card. The implementation currently supports up to a million cards.
    private static final int REQUEST_TEXTURE_N = 1000000;
    private static final int REQUEST_DETAIL_TEXTURE_N = 2000000;
    private static final int REQUEST_GEOMETRY_N = 3000000;
    private static final int REQUEST_END = 4000000;

    private HandlerThread mHandlerThread;
    private Context mContext;
    private CarouselView mCarouselView;
    private boolean DBG = true;
    private long HOLDOFF_DELAY = 100;
    private Handler mAsyncHandler; // Background thread handler for reading textures, geometry, etc.
    private Handler mSyncHandler; // Synchronous handler for interacting with UI elements.

    public static class TextureParameters {
        public TextureParameters(Matrix _matrix) { matrix = _matrix; }
        public Matrix matrix;
    };

    public static class DetailTextureParameters {
        public DetailTextureParameters(float offX, float offY) {
            offsetX = offX;
            offsetY = offY;
        }
        public float offsetX;
        public float offsetY;
    };

    public void setCarouselView(CarouselView carouselView) {
        mCarouselView = carouselView;
        mCarouselView.setCallback(this);
    }

    public CarouselViewHelper(Context context, CarouselView carouselView) {
        this(context);
        setCarouselView(carouselView);
    }

    public CarouselViewHelper(Context context) {
        mContext = context;

        mHandlerThread = new HandlerThread(TAG + ".handler");
        mHandlerThread.start();

        mAsyncHandler = new AsyncHandler(mHandlerThread.getLooper());
        mSyncHandler = new SyncHandler(); // runs in calling thread
    }

    class AsyncHandler extends Handler {
        AsyncHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            int id = msg.arg1;
            if (id >= mCarouselView.getCardCount()) {
                Log.e(TAG, "Index out of range for get, card:" + id);
                return;
            }
            if (msg.what < REQUEST_TEXTURE_N || msg.what > REQUEST_END) {
                Log.e(TAG, "Unknown message: " + id);
                return;
            }
            if (msg.what < REQUEST_DETAIL_TEXTURE_N) {
                // REQUEST_TEXTURE_N
                final Bitmap bitmap = getTexture(id);
                if (bitmap != null) {
                    mSyncHandler.obtainMessage(SET_TEXTURE_N, id, 0, bitmap).sendToTarget();
                }
            } else if (msg.what < REQUEST_GEOMETRY_N) {
                // REQUEST_DETAIL_TEXTURE_N
                final Bitmap bitmap = getDetailTexture(id);
                if (bitmap != null) {
                    mSyncHandler.obtainMessage(SET_DETAIL_TEXTURE_N, id, 0, bitmap).sendToTarget();
                }
            } else if (msg.what < REQUEST_END) {
                // REQUEST_GEOMETRY_N
                Mesh mesh = getGeometry(id);
                if (mesh != null) {
                    mSyncHandler.obtainMessage(SET_GEOMETRY_N, id, 0, mesh).sendToTarget();
                }
            }
        }
    };

    class SyncHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            int id = msg.arg1;
            if (id >= mCarouselView.getCardCount()) {
                Log.e(TAG, "Index out of range for set, card:" + id);
                return;
            }

            switch (msg.what) {
                case SET_TEXTURE_N:
                    mCarouselView.setTextureForItem(id, (Bitmap) msg.obj);
                    break;

                case SET_DETAIL_TEXTURE_N:
                    DetailTextureParameters params = getDetailTextureParameters(id);
                    float x = params != null ? params.offsetX : 0.0f;
                    float y = params != null ? params.offsetY : 0.0f;
                    mCarouselView.setDetailTextureForItem(id, x, y, (Bitmap) msg.obj);
                    break;

                case SET_GEOMETRY_N:
                    mCarouselView.setGeometryForItem(id, (Mesh) msg.obj);
                    break;
            }
        }
    };

    /**
     * Implement this method if you want to load a texture for
     * the given card.  Most subclasses will implement this. Note: this will generally
     * <b>not</b> be called in the UI thread, so proper locking should be ensured.
     *
     * @param id of the texture to load
     * @return a valid bitmap
     */
    public Bitmap getTexture(int id) {
        return null;
    }

    /**
     * Implement this method if you want to load a detail texture for
     * the given card.  Most subclasses will implement this. Note: this will generally
     * <b>not</b> be called in the UI thread, so proper locking should be ensured.
     *
     * @param id
     * @return
     */
    public Bitmap getDetailTexture(int id) {
        return null;
    }

    /**
     * Implement this method if you want to load geometry for the given card.  Most subclasses
     * will implement this. Note: this will generally <b>not</b> be called in the UI thread,
     * so proper locking should be ensured.
     *
     * @param id
     * @return
     */
    public Mesh getGeometry(int id) {
        return null;
    }

    /**
     * Implement this method if you want custom texture parameters for
     * the given id. Note: this will generally
     * <b>not</b> be called in the UI thread, so proper locking should be ensured.
     *
     * @param id
     * @return texture parameters
     */
    public TextureParameters getTextureParameters(int id) {
        return null;
    }

    /**
     * Implement this method if you want custom detail texture parameters for
     * the given id. Note: this will generally
     * <b>not</b> be called in the UI thread, so proper locking should be ensured.
     *
     * @param id the id of the texture being requested
     * @return detail texture parameters
     */
    public DetailTextureParameters getDetailTextureParameters(int id) {
        return null;
    }

    public void onRequestTexture(int id) {
        if (DBG) Log.v(TAG, "onRequestTexture(" + id + ")" );
        mAsyncHandler.removeMessages(REQUEST_TEXTURE_N + id);
        Message message = mAsyncHandler.obtainMessage(REQUEST_TEXTURE_N + id, id, 0);
        mAsyncHandler.sendMessageDelayed(message, HOLDOFF_DELAY);
    }

    public void onInvalidateTexture(final int id) {
        if (DBG) Log.v(TAG, "onInvalidateTexture(" + id + ")");
        mAsyncHandler.removeMessages(REQUEST_TEXTURE_N + id);
    }

    public void onRequestGeometry(int id) {
        if (DBG) Log.v(TAG, "onRequestGeometry(" + id + ")");
        mAsyncHandler.removeMessages(REQUEST_GEOMETRY_N + id);
        mAsyncHandler.sendMessage(mAsyncHandler.obtainMessage(REQUEST_GEOMETRY_N + id, id, 0));
    }

    public void onInvalidateGeometry(int id) {
        if (DBG) Log.v(TAG, "onInvalidateGeometry(" + id + ")");
        mAsyncHandler.removeMessages(REQUEST_GEOMETRY_N + id);
    }

    public void onRequestDetailTexture(int id) {
        if (DBG) Log.v(TAG, "onRequestDetailTexture(" + id + ")" );
        mAsyncHandler.removeMessages(REQUEST_DETAIL_TEXTURE_N + id);
        Message message = mAsyncHandler.obtainMessage(REQUEST_DETAIL_TEXTURE_N + id, id, 0);
        mAsyncHandler.sendMessageDelayed(message, HOLDOFF_DELAY);
    }

    public void onInvalidateDetailTexture(int id) {
        if (DBG) Log.v(TAG, "onInvalidateDetailTexture(" + id + ")");
        mAsyncHandler.removeMessages(REQUEST_DETAIL_TEXTURE_N + id);
    }

    public void onCardSelected(int n) {
        if (DBG) Log.v(TAG, "onCardSelected(" + n + ")");
    }

    public void onAnimationStarted() {

    }

    public void onAnimationFinished() {

    }

    public void onReportFirstCardPosition(int n) {

    }

    public void onResume() {
        mCarouselView.onResume();
    }

    public void onPause() {
        mCarouselView.onPause();
    }

    public void onDestroy() {
        mHandlerThread.quit();
    }

    protected Handler getAsyncHandler() {
        return mAsyncHandler;
    }

    protected CarouselView getCarouselView() {
        return mCarouselView;
    }
}
