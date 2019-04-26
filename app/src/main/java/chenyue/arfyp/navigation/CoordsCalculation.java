package chenyue.arfyp.navigation;


import android.util.Log;

import com.google.ar.core.Camera;
import com.google.ar.core.Pose;
import com.google.ar.core.TrackingState;


/**
 * this class includes coordinates tracking functions
 */
public class CoordsCalculation implements Runnable {
    public final static String TAG = "coordsCalculation";
    public static double[] curPosition = new double[2];
    public static double DEGREE_OFFSET;
    public static double[] initCoordinates = new double[2];     // first for East, second for North
    public final static double[] initCameraCoords = new double[2];
    public static boolean readyForTracking = false;
    public static int floor;
    private static Camera camera;


    public static void prepareTracking(double E, double N, Camera ARcamera) {
        CoordsCalculation.setRotationOffset(DistanceEstimation.EulerDegrees[0]);
        CoordsCalculation.initCoordinates[0] = E;
        CoordsCalculation.initCoordinates[1] = N;
        camera = ARcamera;
        Pose initCameraPose = ARcamera.getDisplayOrientedPose();
        CoordsCalculation.initCameraCoords[0] = initCameraPose.tx();
        CoordsCalculation.initCameraCoords[1] = initCameraPose.tz();
        CoordsCalculation.readyForTracking = true;
    }

    // keep track of user's movement and update corresponding coordinates
    public static void coordsTracking() {
        // we won't update the coordinates until it gets ready
        if (!readyForTracking || camera.getTrackingState() == TrackingState.PAUSED) return;
        Pose curPose = camera.getDisplayOrientedPose();
        double displacement = Math.sqrt(Math.pow(curPose.tx() - initCameraCoords[0], 2) + Math.pow(curPose.tz() - initCameraCoords[1], 2));
        double walkingDirection = DistanceEstimation.adjustAngle(DEGREE_OFFSET + Math.atan2(curPose.tz() - initCameraCoords[1], curPose.tx() - initCameraCoords[0]));
        curPosition[0] = initCoordinates[0] + displacement * Math.sin(walkingDirection);
        curPosition[1] = initCoordinates[1] + displacement * Math.cos(walkingDirection);
    }

    public static void forcelyUpdateCoords(double[] coords) {
        // forcely update the current coordinates and initial coordinate accordingly
        curPosition[0] = coords[0];
        curPosition[1] = coords[1];
        Pose curPose = camera.getDisplayOrientedPose();
        initCameraCoords[0] = curPose.tx();
        initCameraCoords[1] = curPose.tz();
    }

    public static void setRotationOffset(float eulerDegree) {
        DEGREE_OFFSET = DistanceEstimation.adjustAngle(eulerDegree + 0.5 * Math.PI);
    }

    public static void saveTempCoords(double tempCoords[]) {
        tempCoords[0] = curPosition[0];
        tempCoords[1] = curPosition[1];
    }

    public void run() {
        Log.d(TAG, "coords calculation started");
        while (true) {
            if (!readyForTracking) {
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            while (readyForTracking) {
                coordsTracking();
                try {
                    // it is not needed to check and update the coordinates so frequently
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
