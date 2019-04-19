package chenyue.arfyp.userviews;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;

import java.io.IOException;
import java.io.InputStream;

import chenyue.arfyp.navigation.CoordsCalculation;

public class MapOverlay extends SurfaceView implements SurfaceHolder.Callback, Runnable, View.OnClickListener {
    private static final String TAG = "floor-plan view";
    private Context context;
    private SurfaceHolder holder;
    private final Paint paint = new Paint();
    private Canvas canvas;
    public static boolean requireDraw = true;
    private static double PLAN_DEGREE_OFFEST = Math.toRadians(-51); // always positive
    private static double METER_PER_PIXEL = 3.6 / 80;
    private double[] coordsInPlan;

    public MapOverlay(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        holder = getHolder();
        this.context = context;
        setVisibility(SurfaceView.INVISIBLE);
        paint.setColor(Color.RED);
        paint.setStyle(Paint.Style.FILL);
        paint.setStrokeWidth(3.0f);
        coordsInPlan = new double[2];
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {

    }

    public void refreshView(Bitmap floorplan) {
        try {
            canvas = holder.lockCanvas();
            canvas.drawColor(0, PorterDuff.Mode.CLEAR);
            canvas.setBitmap(floorplan);
            canvas.drawCircle((float) coordsInPlan[0], (float) coordsInPlan[1], 3.0f, paint);
            canvas.drawPoint((float) coordsInPlan[0], (float) coordsInPlan[1], paint);
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "something wrong with drawing map!");
        } finally {
            if (canvas != null)
                holder.unlockCanvasAndPost(canvas);
        }
    }

    public void coordsFromWorldToMap(Bitmap floorplan) {
        double x, y;
        double distance = calculateDistance(CoordsCalculation.curPosition[0], CoordsCalculation.curPosition[1]) / METER_PER_PIXEL;
        double degree = Math.atan2(CoordsCalculation.curPosition[0], CoordsCalculation.curPosition[1]);
        x = distance * Math.sin(degree - PLAN_DEGREE_OFFEST) + floorplan.getWidth() / 2;
        y = floorplan.getHeight() / 2 - distance * Math.cos(degree - PLAN_DEGREE_OFFEST);
        if (x < 0) coordsInPlan[0] = 0;
        else if (x > floorplan.getWidth()) coordsInPlan[0] = floorplan.getWidth();
        else coordsInPlan[0] = x;
        if (y < 0) coordsInPlan[1] = 0;
        else if (y > floorplan.getHeight()) coordsInPlan[1] = floorplan.getHeight();
        else coordsInPlan[1] = floorplan.getHeight();
    }

    public void coordsFromMapToWorld(double x, double y) {
        double distance = calculateDistance(x, y);
        double degree = Math.atan2(x, y);
        double coordsN = distance * Math.cos(degree + PLAN_DEGREE_OFFEST) * METER_PER_PIXEL;
        double coordsE = distance * Math.sin(degree + PLAN_DEGREE_OFFEST) * METER_PER_PIXEL;
    }

    public static double calculateDistance(double E, double N) {
        return Math.sqrt(Math.pow(E, 2) + Math.pow(N, 2));
    }

    @Override
    public void run() {
        setClickable(true);
        AssetManager assets = context.getAssets();
        try {
            InputStream floorPlanInput = assets.open("floorplan/" + CoordsCalculation.floor + ".png");
            final Bitmap floorplan = Bitmap.createBitmap(BitmapFactory.decodeStream(floorPlanInput));
            while (requireDraw) {
                if (getVisibility() == SurfaceView.INVISIBLE)
                    setVisibility(SurfaceView.VISIBLE);
                coordsFromWorldToMap(floorplan);
                refreshView(floorplan);
                try {
                    // don't draw too fast
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            floorplan.recycle();
            floorPlanInput.close();
        } catch (IOException e) {
            Log.e(TAG, "cannot open file!");
            requireDraw = false;
            e.printStackTrace();
        }
    }

    @Override
    public void onClick(View v) {
        // TODO control the floor plan draw thread?
        setVisibility(SurfaceView.INVISIBLE);
    }
}