package chenyue.arfyp.navigation;


import android.content.Context;
import android.util.Log;

import com.google.ar.core.Camera;
import com.google.ar.core.Pose;
import com.google.ar.core.TrackingState;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;


/**
 * this class includes coordinates tracking functions
 */
public class CoordsCalculation implements Runnable {
    public final static String TAG = "coordsCalculation";
    public static double[] curPosition = new double[2];
    // all these angles are in radians
    public static double initOrientationInBuildingSys;
    public static double curOrientationInBuildingSys;
    public static double initOrientationInIndividualSys;
    public static double curOrientationInIndividualSys;
    public static double xAxisDirectionInBuildingSys;
    public static double[] initCoordinates = new double[2];     // first for East, second for North
    public final static double[] initCameraCoords = new double[2];
    public static boolean readyForTracking = false;
    public static int floor;
    private static Camera camera;
    private final Context context;

    public CoordsCalculation(Context context) {
        this.context = context;
    }

    public static void prepareTracking(double E, double N, Camera ARcamera, double orientationInBuildingSys) {

        CoordsCalculation.initCoordinates[0] = E;
        CoordsCalculation.initCoordinates[1] = N;
        initOrientationInBuildingSys = orientationInBuildingSys;

        camera = ARcamera;
        Pose initVirtualCameraPose = camera.getDisplayOrientedPose();
        double[] initEulerAngles = toEulerAngle();
        initOrientationInIndividualSys = initEulerAngles[0];
        xAxisDirectionInBuildingSys =
                DistanceEstimation.adjustAngle(initOrientationInBuildingSys + 0.5 * DistanceEstimation.adjustAngle(Math.PI - initOrientationInIndividualSys));

        CoordsCalculation.initCameraCoords[0] = initVirtualCameraPose.tx();
        CoordsCalculation.initCameraCoords[1] = initVirtualCameraPose.tz();
        CoordsCalculation.readyForTracking = true;
    }

    // keep track of user's movement and update corresponding coordinates
    public static void coordsTracking() {
        // we won't update the coordinates until it gets ready
        if (!readyForTracking || camera.getTrackingState() == TrackingState.PAUSED) return;
        Pose curPose = camera.getDisplayOrientedPose();
        double displacement = DistanceEstimation.calculateDistance(initCameraCoords[0], initCameraCoords[1], curPose.tx(), curPose.tz());
        double walkingDirection = DistanceEstimation.adjustAngle(xAxisDirectionInBuildingSys + Math.atan2(curPose.tz() - initCameraCoords[1], curPose.tx() - initCameraCoords[0]));
        curPosition[0] = initCoordinates[0] + displacement * Math.sin(walkingDirection);
        curPosition[1] = initCoordinates[1] + displacement * Math.cos(walkingDirection);
        curOrientationInIndividualSys = toEulerAngle()[0];
        curOrientationInBuildingSys = calculateCurOrientationInBuildingSys();
    }

    public static void forcelyUpdateCoords(double[] coords) {
        // forcely update the current coordinates and initial coordinate accordingly
        curPosition[0] = coords[0];
        curPosition[1] = coords[1];
        Pose curPose = camera.getDisplayOrientedPose();
        initCameraCoords[0] = curPose.tx();
        initCameraCoords[1] = curPose.tz();
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
                logDown();
                try {
                    // it is not needed to check and update the coordinates so frequently
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static double[] toEulerAngle() {
        // roll (x-axis rotation)  ***use camera physical pose, the x axis is pointing up,y axis is pointing left***
        float q[] = camera.getPose().getRotationQuaternion();
        double sinr_cosp = +2.0 * (q[3] * q[0] + q[1] * q[2]);
        double cosr_cosp = +1.0 - 2.0 * (q[0] * q[0] + q[1] * q[1]);
        double roll = Math.atan2(sinr_cosp, cosr_cosp);

        // pitch (y-axis rotation)
        double sinp = +2.0 * (q[3] * q[1] - q[2] * q[0]);
        double pitch;
        if (Math.abs(sinp) >= 1) {
            if (sinp < 0)// use 90 degrees if out of range
                pitch = -Math.PI / 2;
            else
                pitch = Math.PI / 2;
        } else
            pitch = Math.asin(sinp);

        // yaw (z-axis rotation)
        double yaw;
        double siny_cosp = +2.0 * (q[3] * q[2] + q[0] * q[1]);
        double cosy_cosp = +1.0 - 2.0 * (q[1] * q[1] + q[2] * q[2]);
        yaw = Math.atan2(siny_cosp, cosy_cosp);
        double[] results = {roll, pitch, yaw};
        return results;
    }

    public static double calculateCurOrientationInBuildingSys() {
        double[] rotationEuler = toEulerAngle();
        return rotationEuler[0] - initOrientationInIndividualSys + initOrientationInBuildingSys; // cur-init = cur-init
    }

    /**
     * log down the coordinates related information for analysis
     * q
     */
    public void logDown() {
        String logPath = context.getExternalCacheDir().getAbsolutePath() + "/log-coords.txt";
        Log.d(TAG, "log file here: " + logPath);
        try {
            BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(logPath, true)));
            bw.write(String.format("coords:x % f, y % f ", curPosition[0], curPosition[1]));
            bw.write("rotation in individual: " + curOrientationInIndividualSys);
            bw.write("rotation in building: " + curOrientationInBuildingSys);
            bw.flush();
            bw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
