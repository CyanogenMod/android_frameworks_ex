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

package com.android.carouseltest;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import com.android.carouseltest.MyCarouselView;
import com.android.ex.carousel.CarouselView;
import com.android.ex.carousel.CarouselRS.CarouselCallback;

import android.app.Activity;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.BitmapDrawable;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.renderscript.Float4;
import android.util.Log;

public class CarouselTestActivity extends Activity {
    private static final int HOLDOFF_DELAY = 0;
    private static final int CARD_SLOTS = 56;
    private static final int TOTAL_CARDS = 1000;
    private static final int TEXTURE_HEIGHT = 511;
    private static final int TEXTURE_WIDTH = 511;
    private static final String TAG = "CarouselTestActivity";

    private static final int SLOTS_VISIBLE = 7;
    protected static final boolean DBG = false;
    protected static final int SET_TEXTURE_N = 1000;
    protected static final int SET_DETAIL_TEXTURE_N = 1000;
    private static final int DETAIL_TEXTURE_WIDTH = 200;
    private static final int DETAIL_TEXTURE_HEIGHT = 80;
    private CarouselView mView;
    private Paint mPaint = new Paint();

    private Handler mTextureHandler;
    private Handler mDetailTextureHandler;
    private Handler mGeometryHandler;
    private Handler mSetTextureHandler;
    private Handler mSetDetailTextureHandler;
    private HandlerThread mHandlerThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mView = new MyCarouselView(this);
        mPaint.setColor(0xffffffff);
        final Resources res = getResources();

        mHandlerThread = new HandlerThread(TAG + ".handler");
        mHandlerThread.start();

        mTextureHandler = new Handler(mHandlerThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                if (msg.what < TOTAL_CARDS) {
                    final int id = (Integer) msg.arg1;
                    final int n = msg.what;
                    final Bitmap bitmap = createBitmap(id);
                    mSetTextureHandler.obtainMessage(SET_TEXTURE_N + n, bitmap).sendToTarget();
                }
            }
        };

        mDetailTextureHandler = new Handler(mHandlerThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                if (msg.what < TOTAL_CARDS) {
                    final int id = (Integer) msg.arg1;
                    final int n = msg.what;
                    final Bitmap bitmap = createDetailBitmap(id);
                    // writeBitmapToFile(bitmap, "detailBitmap" + n + ".png");
                    mSetDetailTextureHandler.obtainMessage(SET_DETAIL_TEXTURE_N + n, bitmap)
                            .sendToTarget();
                }
            }
        };

        mGeometryHandler = new Handler(mHandlerThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                // TODO
            }
        };

        mSetTextureHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                if (msg.what >= SET_TEXTURE_N) {
                    final Bitmap bitmap = (Bitmap) msg.obj;
                    mView.setTextureForItem(msg.what - SET_TEXTURE_N, bitmap);
                }
            }
        };

        mSetDetailTextureHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                if (msg.what >= SET_DETAIL_TEXTURE_N) {
                    final Bitmap bitmap = (Bitmap) msg.obj;
                    mView.setDetailTextureForItem(msg.what - SET_TEXTURE_N, 5.0f, 5.0f, bitmap);
                }
            }
        };

        mView.setCallback(mCarouselCallback);
        mView.setSlotCount(CARD_SLOTS);
        mView.createCards(TOTAL_CARDS);
        mView.setVisibleSlots(SLOTS_VISIBLE);
        mView.setStartAngle((float) -(2.0f*Math.PI * 5 / CARD_SLOTS));
        mView.setDefaultBitmap(BitmapFactory.decodeResource(res, R.drawable.unknown));
        mView.setLoadingBitmap(BitmapFactory.decodeResource(res, R.drawable.wait));
        mView.setBackgroundColor(0.25f, 0.25f, 0.5f, 1.0f);

        /*
        int flags = WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER;
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
        mView.getWidth(), mView.getHeight(), WindowManager.LayoutParams.TYPE_APPLICATION,
            flags, PixelFormat.TRANSLUCENT);
        mView.setBackgroundColor(0x80000000);

        getWindow().setAttributes(lp);
        */
        setContentView(mView);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mView.onPause();
    }

    Bitmap createBitmap(int n) {
        Bitmap bitmap = Bitmap.createBitmap(TEXTURE_WIDTH, TEXTURE_HEIGHT,
                Bitmap.Config.RGB_565);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawARGB(255, 128, 128, 128);
        mPaint.setTextSize(100.0f);
        mPaint.setAntiAlias(true);
        canvas.drawText(""+n, 0, TEXTURE_HEIGHT-10, mPaint);
        return bitmap;
    }

    Bitmap createDetailBitmap(int n) {
        Bitmap bitmap = Bitmap.createBitmap(DETAIL_TEXTURE_WIDTH, DETAIL_TEXTURE_HEIGHT,
                Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawARGB(200, 200, 200, 255);
        mPaint.setTextSize(15.0f);
        mPaint.setAntiAlias(true);
        canvas.drawText("Detail text for card " + n, 0, DETAIL_TEXTURE_HEIGHT/2, mPaint);
        return bitmap;
    }

    void writeBitmapToFile(Bitmap bitmap, String fname) {
        File path = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES);
        File file = new File(path, fname);

        try {
            path.mkdirs();
            OutputStream os = new FileOutputStream(file);
            MediaScannerConnection.scanFile(this, new String[] { file.toString() }, null,
                    new MediaScannerConnection.OnScanCompletedListener() {

                public void onScanCompleted(String path, Uri uri) {

                }
            });
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, os);
        } catch (IOException e) {
            Log.w("ExternalStorage", "Error writing " + file, e);
        }
    }

    private CarouselCallback mCarouselCallback = new CarouselCallback() {

        public void onRequestTexture(int n) {
            if (DBG) Log.v(TAG, "onRequestTexture(" + n + ")" );
            mTextureHandler.removeMessages(n);
            Message message = mTextureHandler.obtainMessage(n, n, 0);
            mTextureHandler.sendMessageDelayed(message, HOLDOFF_DELAY);
        }

        public void onInvalidateTexture(final int n) {
            if (DBG) Log.v(TAG, "onInvalidateTexture(" + n + ")");
            mTextureHandler.removeMessages(n);
        }

        public void onRequestGeometry(int n) {
            if (DBG) Log.v(TAG, "onRequestGeometry(" + n + ")");
            mGeometryHandler.removeMessages(n);
            mGeometryHandler.sendMessage(mGeometryHandler.obtainMessage(n));
        }

        public void onRequestDetailTexture(int n) {
            if (DBG) Log.v(TAG, "onRequestDetailTexture(" + n + ")" );
            mDetailTextureHandler.removeMessages(n);
            Message message = mDetailTextureHandler.obtainMessage(n, n, 0);
            mDetailTextureHandler.sendMessageDelayed(message, HOLDOFF_DELAY);
        }

        public void onInvalidateDetailTexture(int n) {
            if (DBG) Log.v(TAG, "onInvalidateDetailTexture(" + n + ")");
            mDetailTextureHandler.removeMessages(n);
        }

        public void onInvalidateGeometry(int n) {
            if (DBG) Log.v(TAG, "onInvalidateGeometry(" + n + ")");
            mGeometryHandler.removeMessages(n);
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
    };

}
