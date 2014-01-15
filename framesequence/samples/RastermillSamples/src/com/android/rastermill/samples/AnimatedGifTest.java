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
package com.android.rastermill.samples;

import android.app.Activity;
import android.os.Bundle;
import android.support.rastermill.FrameSequence;
import android.support.rastermill.FrameSequenceDrawable;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.InputStream;

public class AnimatedGifTest extends Activity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.basic_test_activity);
        ImageView imageView = (ImageView) findViewById(R.id.imageview);

        InputStream is = getResources().openRawResource(R.raw.animated);

        FrameSequence fs = FrameSequence.decodeStream(is);
        final FrameSequenceDrawable drawable = new FrameSequenceDrawable(fs);
        drawable.setOnFinishedListener(new FrameSequenceDrawable.OnFinishedListener() {
            @Override
            public void onFinished(FrameSequenceDrawable drawable) {
                Toast.makeText(getApplicationContext(),
                        "THE ANIMATION HAS FINISHED", Toast.LENGTH_SHORT).show();
            }
        });
        imageView.setImageDrawable(drawable);

        findViewById(R.id.start).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                drawable.start();
            }
        });
        findViewById(R.id.stop).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                drawable.stop();
            }
        });
        findViewById(R.id.vis).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                drawable.setVisible(true, true);
            }
        });
        findViewById(R.id.invis).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                drawable.setVisible(false, true);
            }
        });
    }
}
