/* Copyright 2016 The TensorFlow Authors. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
==============================================================================*/

package com.chenyue.tensorflowdetection;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Cap;
import android.graphics.Paint.Join;
import android.graphics.Paint.Style;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.text.TextUtils;
import android.util.Pair;
import android.util.TypedValue;

import com.chenyue.tensorflowdetection.Classifier.Recognition;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class MultiBoxTracker {
    private final Logger logger = new Logger();

    private static final float TEXT_SIZE_DIP = 18;

    private static final float MIN_SIZE = 16.0f;

    private static final int[] COLORS = {
            Color.BLUE, Color.RED, Color.GREEN, Color.YELLOW, Color.CYAN, Color.MAGENTA, Color.WHITE,
            Color.parseColor("#55FF55"), Color.parseColor("#FFA500"), Color.parseColor("#FF8888"),
            Color.parseColor("#AAAAFF"), Color.parseColor("#FFFFAA"), Color.parseColor("#55AAAA"),
            Color.parseColor("#AA33AA"), Color.parseColor("#0D0068")
    };

    private final Queue<Integer> availableColors = new LinkedList<Integer>();

    final List<Pair<Float, RectF>> screenRects = new LinkedList<Pair<Float, RectF>>();
    private int screenWidth;
    private int screenHeight;


    private static class TrackedRecognition {
        RectF location;
        float detectionConfidence;
        int color;
        String title;
    }

    private final List<TrackedRecognition> trackedObjects = new LinkedList<TrackedRecognition>();

    private final Paint boxPaint = new Paint();

    private final float textSizePx;
    private final BorderedText borderedText;

    private Matrix frameToCanvasMatrix;

    private int frameWidth;
    private int frameHeight;

    private int sensorOrientation;
    private Context context;

    public MultiBoxTracker(final Context context) {
        this.context = context;
        for (final int color : COLORS) {
            availableColors.add(color);
        }

        boxPaint.setColor(Color.RED);
        boxPaint.setStyle(Style.STROKE);
        boxPaint.setStrokeWidth(12.0f);
        boxPaint.setStrokeCap(Cap.ROUND);
        boxPaint.setStrokeJoin(Join.ROUND);
        boxPaint.setStrokeMiter(100);

        textSizePx =
                TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, context.getResources().getDisplayMetrics());
        borderedText = new BorderedText(textSizePx);
    }

    private Matrix getFrameToCanvasMatrix() {
        return frameToCanvasMatrix;
    }

    public synchronized void trackResults(final List<Recognition> results) {
        processResults(results);
    }

    public synchronized void draw(final Canvas canvas) {
        // clear the previous marks
        canvas.drawColor(0, PorterDuff.Mode.CLEAR);
        final boolean rotated = sensorOrientation % 180 == 90;
        //logger.i("multiplier: %f", multiplier);
        screenWidth = rotated ? canvas.getWidth() : canvas.getHeight();
        screenHeight = rotated ? canvas.getHeight() : canvas.getWidth();
        //logger.i("width: %d, height: %d", screenWidth, screenHeight);

        frameToCanvasMatrix =
                TensorflowUtils.getTransformationMatrix(
                        frameWidth,
                        frameHeight,
                        screenWidth,
                        screenHeight,
                        sensorOrientation,
                        false);
        for (final TrackedRecognition recognition : trackedObjects) {
            final RectF trackedPos = new RectF(recognition.location);
            //logger.i("tracked rect position before transform %f %f", trackedPos.left, trackedPos.top);
            getFrameToCanvasMatrix().mapRect(trackedPos);
            //logger.i("tracked rect position before transform %f %f", trackedPos.left, trackedPos.top);
            boxPaint.setColor(recognition.color);

            final float cornerSize = Math.min(trackedPos.width(), trackedPos.height()) / 8.0f;
            canvas.drawRoundRect(trackedPos, cornerSize, cornerSize, boxPaint);

            final String labelString =
                    !TextUtils.isEmpty(recognition.title)
                            ? String.format("%s %.2f", recognition.title, recognition.detectionConfidence)
                            : String.format("%.2f", recognition.detectionConfidence);
            borderedText.drawText(canvas, trackedPos.left + cornerSize, trackedPos.bottom, labelString);
        }
    }

    private void processResults(final List<Recognition> results) {
        final List<Pair<Float, Recognition>> rectsToTrack = new LinkedList<Pair<Float, Recognition>>();

        screenRects.clear();
        final Matrix rgbFrameToScreen = new Matrix(getFrameToCanvasMatrix());  // transform rectangle from frame size into screen size

        for (final Recognition result : results) {
            if (result.getLocation() == null) {
                continue;
            }
            final RectF detectionFrameRect = new RectF(result.getLocation());

            final RectF detectionScreenRect = new RectF();
            rgbFrameToScreen.mapRect(detectionScreenRect, detectionFrameRect);

            logger.v(
                    "Result! Frame: " + result.getLocation() + " mapped to screen:" + detectionScreenRect);

            screenRects.add(new Pair<Float, RectF>(result.getConfidence(), detectionScreenRect));

            if (detectionFrameRect.width() < MIN_SIZE || detectionFrameRect.height() < MIN_SIZE) {
                logger.w("Degenerate rectangle! " + detectionFrameRect);
                continue;
            }

            rectsToTrack.add(new Pair<Float, Recognition>(result.getConfidence(), result));  // here rectsToTrack records those rect bigger than min_size
        }

        if (rectsToTrack.isEmpty()) {
            logger.v("Nothing to track, aborting.");
            return;
        }

        synchronized (trackedObjects) {
            trackedObjects.clear();
            for (final Pair<Float, Recognition> potential : rectsToTrack) {
                final TrackedRecognition trackedRecognition = new TrackedRecognition();
                trackedRecognition.detectionConfidence = potential.first;
                trackedRecognition.location = new RectF(potential.second.getLocation());
                trackedRecognition.title = potential.second.getTitle();
                trackedRecognition.color = COLORS[trackedObjects.size()];
                trackedObjects.add(trackedRecognition);

                if (trackedObjects.size() >= COLORS.length) {
                    break;
                }
            }
            trackedObjects.notifyAll();
        }
    }

    public void setFrameWidth(int width) {
        frameWidth = width;
    }

    public void setFrameHeight(int height) {
        frameHeight = height;
    }

    public int getScreenWidth() {
        return screenWidth;
    }

    public int getScreenHeight() {
        return screenHeight;
    }


    public void setSensorOrientation(int rotation) {
        this.sensorOrientation = rotation;
    }

    public List<TrackedRecognition> getTrackedObjects() {
        return trackedObjects;
    }

    public List<Pair<RectF, String>> getItemInTrackedObjects() {
        synchronized (trackedObjects) {
            if (screenHeight == 0 || screenWidth == 0 || trackedObjects.size() == 0) return null;
            List<Pair<RectF, String>> trackedList = new LinkedList<>();
            for (TrackedRecognition tracked : trackedObjects) {
                RectF location = tracked.location;
                // filter those objects near the edges.
                if (location.top < 10 || location.left < 10 || screenHeight - location.bottom < 10 || screenWidth - location.right < 10)
                    continue;
                trackedList.add(new Pair<RectF, String>(location, tracked.title));
            }
            return trackedList;
        }
    }

}