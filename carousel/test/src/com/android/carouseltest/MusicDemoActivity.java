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

import com.android.ex.carousel.CarouselView;
import com.android.ex.carousel.CarouselRS.CarouselCallback;

import android.app.Activity;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.renderscript.Mesh;
import android.util.Log;

public class MusicDemoActivity extends Activity {
    private static final int HOLDOFF_DELAY = 0; // larger # gives smoother animation but lag
    private static final int CD_GEOMETRY = R.raw.book;
    private static final int VISIBLE_SLOTS = 7;
    private static final int CARD_SLOTS = 56;
    private static final int TOTAL_CARDS = 10000;
    private static final String TAG = "MusicDemoActivity";
    protected static final boolean DBG = true;
    protected static final int SET_TEXTURE_N = 1000;
    private CarouselView mView;
    private int imageResources[] = {
        R.drawable.emo_im_angel,
        R.drawable.emo_im_cool,
        R.drawable.emo_im_crying,
        R.drawable.emo_im_foot_in_mouth,
        R.drawable.emo_im_happy,
        R.drawable.emo_im_kissing,
        R.drawable.emo_im_laughing,
        R.drawable.emo_im_lips_are_sealed,
        R.drawable.emo_im_money_mouth,
        R.drawable.emo_im_sad,
        R.drawable.emo_im_surprised,
        R.drawable.emo_im_tongue_sticking_out,
        R.drawable.emo_im_undecided,
        R.drawable.emo_im_winking,
        R.drawable.emo_im_wtf,
        R.drawable.emo_im_yelling
    };
    private Bitmap mSpecularMap;
    private HandlerThread mHandlerThread;
    private Handler mTextureHandler;
    private Handler mGeometryHandler;
    private Handler mSetTextureHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mHandlerThread = new HandlerThread(TAG + ".handler");
        mHandlerThread.start();

        mTextureHandler = new Handler(mHandlerThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                if (msg.what < TOTAL_CARDS) {
                    final int resId = (Integer) msg.arg1;
                    final int n = msg.what;
                    final Bitmap bitmap = compositeBitmap(resId);
                    mSetTextureHandler.obtainMessage(SET_TEXTURE_N + n, bitmap).sendToTarget();
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

        final Resources res = getResources();
        setContentView(R.layout.music_demo);
        mView = (CarouselView) findViewById(R.id.carousel);
        mView.setCallback(mCallback);
        mView.setSlotCount(CARD_SLOTS);
        mView.createCards(TOTAL_CARDS);
        mView.setVisibleSlots(VISIBLE_SLOTS);
        mView.setStartAngle((float) -(2.0f*Math.PI * 5 / CARD_SLOTS));
        mView.setDefaultBitmap(BitmapFactory.decodeResource(res, R.drawable.wait));
        mView.setLoadingBitmap(BitmapFactory.decodeResource(res, R.drawable.blank_album));
        mView.setBackgroundBitmap(BitmapFactory.decodeResource(res, R.drawable.background));
        mView.setDefaultGeometry(mView.loadGeometry(CD_GEOMETRY));
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

    private Bitmap compositeBitmap(int id) {
        final Resources res = getResources();
        if (mSpecularMap == null) {
            mSpecularMap = BitmapFactory.decodeResource(res, R.drawable.specularmap);
        }

        final int width = mSpecularMap.getWidth();
        final int height = mSpecularMap.getHeight();

        Log.v(TAG, "Width = " + width + ", height = " + height);

        final Bitmap artwork = BitmapFactory.decodeResource(res, id);

        /*
        final Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
        //result.setHasAlpha(false);
        final Canvas canvas = new Canvas(result);
        canvas.setDrawFilter(new PaintFlagsDrawFilter(Paint.DITHER_FLAG,
                Paint.FILTER_BITMAP_FLAG));

        Paint paint = new Paint();
        paint.setFilterBitmap(false);

        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC));
        canvas.drawBitmap(artwork, 0, 0, paint);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.ADD));
        canvas.drawBitmap(mSpecularMap, 0, 0, paint);

        writeBitmapToFile(result, "test" + id + ".png");
        */

        return artwork;
    }

    private CarouselCallback mCallback = new CarouselCallback() {

        public void onRequestTexture(int n) {
            if (DBG) Log.v(TAG, "onRequestTexture(" + n + ")" );
            mTextureHandler.removeMessages(n);
            int resId = imageResources[n%imageResources.length];
            Message message = mTextureHandler.obtainMessage(n, resId, 0);
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

        public void onInvalidateGeometry(int n) {
            if (DBG) Log.v(TAG, "onInvalidateGeometry(" + n + ")");
            mGeometryHandler.removeMessages(n);
        }

        public void onRequestDetailTexture(int n) {
            if (DBG) Log.v(TAG, "onRequestDetailTexture(" + n + ")" );
            //mDetailTextureHandler.removeMessages(n);
            //Message message = mDetailTextureHandler.obtainMessage(n, n, 0);
            //mDetailTextureHandler.sendMessageDelayed(message, HOLDOFF_DELAY);
        }

        public void onInvalidateDetailTexture(int n) {
            if (DBG) Log.v(TAG, "onInvalidateDetailTexture(" + n + ")");
            //mDetailTextureHandler.removeMessages(n);
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
