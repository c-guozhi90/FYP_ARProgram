package chenyue.arfyp.userviews;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;

import com.chenyue.tensorflowdetection.TensorflowUtils;

import java.io.IOException;
import java.io.InputStream;

import chenyue.arfyp.navigation.CoordsCalculation;
import chenyue.arfyp.navigation.DistanceEstimation;

public class MapOverlay extends SurfaceView implements SurfaceHolder.Callback, Runnable, View.OnClickListener {
    private static final String TAG = "floor-plan view";
    private Context context;
    private SurfaceHolder holder;
    private final Paint paint = new Paint();
    private Canvas canvas;
    public static boolean requireDraw = true;
    private static double PLAN_DEGREE_OFFEST = 0;//Math.toRadians(-51); // always positive
    private static double METER_PER_PIXEL = 3.6 / 80;
    private double[] coordsInPlan;
    private Matrix planToScreenTransform;
    private int screenHeight = 2138;
    private int screenWidth = 1080;
    private Bitmap tempBitmap;
    private Bitmap floorplan;

    public MapOverlay(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        holder = getHolder();
        this.context = context;
        setVisibility(SurfaceView.INVISIBLE);
        paint.setColor(Color.RED);
        paint.setStyle(Paint.Style.FILL);
        paint.setStrokeWidth(3.0f);
        coordsInPlan = new double[2];
        this.setOnClickListener(this);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        try {
            Canvas canvas = holder.lockCanvas();
            if (canvas != null) {
                screenWidth = canvas.getWidth();
                screenHeight = canvas.getHeight();
                Log.d(TAG, String.format("screen size %f %f", screenWidth, screenWidth));
            }
        } catch (Exception e) {
        } finally {
            if (canvas != null)
                holder.unlockCanvasAndPost(canvas);
        }

    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        screenWidth = width;
        screenHeight = height;
        Log.d(TAG, String.format("screen size %d %d", screenWidth, screenHeight));
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {

    }

    public void refreshView(Bitmap floorplan) {
        try {
            Log.d(TAG, "map start draw");
            canvas = holder.lockCanvas();
            if (canvas == null) return;
            canvas.drawColor(0, PorterDuff.Mode.CLEAR);
            tempBitmap = Bitmap.createBitmap(floorplan, 0, 0, floorplan.getWidth(), floorplan.getHeight(), planToScreenTransform, false);
            canvas.drawBitmap(tempBitmap, 0, 0, null);
            float[] temp = {(float) coordsInPlan[0], (float) coordsInPlan[1]};
            drawDirectionTriangle(temp, canvas);
            planToScreenTransform.mapPoints(temp);
            canvas.drawCircle(temp[0], temp[1], 15f, paint);

            //canvas.drawPoint((float) coordsInPlan[0], (float) coordsInPlan[1], paint);
//            tempBitmap.recycle();
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "something wrong with drawing map!");
        } finally {
            if (canvas != null) {
                holder.unlockCanvasAndPost(canvas);
            }

            Log.d(TAG, "map finish draw");
        }
    }

    public void coordsFromWorldToMap(Bitmap floorplan) {
        double x, y;
        double distance = DistanceEstimation.calculateDistance(CoordsCalculation.curPosition[0], CoordsCalculation.curPosition[1]) /
                METER_PER_PIXEL;
        double degree = Math.atan2(CoordsCalculation.curPosition[0], CoordsCalculation.curPosition[1]);
        x = distance * Math.sin(degree - PLAN_DEGREE_OFFEST) + (double) floorplan.getWidth() / 2;
        y = (double) floorplan.getHeight() / 2 - distance * Math.cos(degree - PLAN_DEGREE_OFFEST);
        if (x < 0) coordsInPlan[0] = 0;
        else if (x > floorplan.getWidth()) coordsInPlan[0] = floorplan.getWidth();
        else coordsInPlan[0] = x;
        if (y < 0) coordsInPlan[1] = 0;
        else if (y > floorplan.getHeight()) coordsInPlan[1] = floorplan.getHeight();
        else coordsInPlan[1] = y;
    }

    public void coordsFromMapToWorld(double x, double y) {
        double distance = DistanceEstimation.calculateDistance(x, y);
        double degree = Math.atan2(x, y);
        double coordsN = distance * Math.cos(degree + PLAN_DEGREE_OFFEST) * METER_PER_PIXEL;
        double coordsE = distance * Math.sin(degree + PLAN_DEGREE_OFFEST) * METER_PER_PIXEL;
    }

    @Override
    public void run() {
        Log.d(TAG, "draw map thread started");
        AssetManager assets = context.getAssets();
        try {
            while (!CoordsCalculation.readyForTracking) {
                Thread.sleep(100);
            }
            InputStream floorPlanInput = assets.open("floorplan/" + CoordsCalculation.floor + ".png");
            //final Bitmap floorplan = Bitmap.createBitmap(BitmapFactory.decodeStream(floorPlanInput));
            floorplan = BitmapFactory.decodeStream(floorPlanInput);
            planToScreenTransform = TensorflowUtils.getTransformationMatrix(floorplan.getWidth(), floorplan.getHeight(), screenWidth, screenHeight, 0, false);
            while (requireDraw) {
                coordsFromWorldToMap(floorplan);
                Log.d(TAG, String.format("coords on map: %f %f", coordsInPlan[0], coordsInPlan[1]));
                refreshView(floorplan);
                try {
                    // don't draw too fast
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            floorplan.recycle();
            tempBitmap.recycle();
            floorPlanInput.close();
        } catch (IOException | InterruptedException e) {
            Log.e(TAG, "cannot open file or something went wrong!");
            requireDraw = false;
            e.printStackTrace();
        }
    }

    @Override
    public void onClick(View v) {
        setClickable(false);
        setVisibility(SurfaceView.INVISIBLE);
    }

    public void drawDirectionTriangle(float startPoint[], Canvas canvas) {
        double direction = CoordsCalculation.curOrientationInBuildingSys;
        Log.d(TAG, "direction: " + direction);
        float[] pointFront = new float[2];
        float[] pointLeft = new float[2];
        float[] pointRight = new float[2];

        pointFront[0] = (float) (startPoint[0] + 30 * Math.sin(direction));
        pointFront[1] = (float) (startPoint[1] - 30 * Math.cos(direction));
        pointLeft[0] = pointFront[0] + 20 * (float) (Math.sin(DistanceEstimation.adjustAngle(DistanceEstimation.adjustAngle(direction - Math.PI) + Math.toRadians(25))));
        pointLeft[1] = pointFront[1] - 20 * (float) (Math.cos(DistanceEstimation.adjustAngle(DistanceEstimation.adjustAngle(direction - Math.PI) + Math.toRadians(25))));
        pointRight[0] = pointFront[0] + 20 * (float) (Math.sin(DistanceEstimation.adjustAngle(DistanceEstimation.adjustAngle(direction - Math.PI) - Math.toRadians(25))));
        pointRight[1] = pointFront[1] - 20 * (float) (Math.cos(DistanceEstimation.adjustAngle(DistanceEstimation.adjustAngle(direction - Math.PI) - Math.toRadians(25))));
//        Log.d(TAG, "point front: " + pointFront[0] + " " + pointFront[1]);
//        Log.d(TAG, "point left: " + pointLeft[0] + " " + pointLeft[1]);
//        Log.d(TAG, "point right: " + pointRight[0] + " " + pointRight[1]);
        planToScreenTransform.mapPoints(pointFront);
        planToScreenTransform.mapPoints(pointLeft);
        planToScreenTransform.mapPoints(pointRight);
        Path path = new Path();
        path.moveTo(pointFront[0], pointFront[1]);
        path.lineTo(pointLeft[0], pointLeft[1]);
        path.lineTo(pointRight[0], pointRight[1]);
        path.close();
        canvas.drawPath(path, paint);
    }
}