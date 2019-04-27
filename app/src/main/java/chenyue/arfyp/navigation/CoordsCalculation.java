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


    public static void prepareTracking(double E, double N, Camera ARcamera, double orientationInBuildingSys) {

        CoordsCalculation.initCoordinates[0] = E;
        CoordsCalculation.initCoordinates[1] = N;
        camera = ARcamera;
        Pose initVirtualCameraPose=camera.getDisplayOrientedPose();
        Pose initPhysicalCameraPose = ARcamera.getPose();
        float[] initRotaionQuaternion = initPhysicalCameraPose.getRotationQuaternion();
        double[] initEulerAngles = toEulerAngle(initRotaionQuaternion);
        CoordsCalculation.setRotationOffset((float) initEulerAngles[0], (float) orientationInBuildingSys);
        CoordsCalculation.initCameraCoords[0] = initVirtualCameraPose.tx();
        CoordsCalculation.initCameraCoords[1] = initVirtualCameraPose.tz();
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

    public static void setRotationOffset(float eulerDegree, float orientationInBuilding) {
        DEGREE_OFFSET = DistanceEstimation.adjustAngle(orientationInBuilding + DistanceEstimation.adjustAngle(0.5 * Math.PI - eulerDegree));
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

    public static double[] toEulerAngle(float q[]) {
        // roll (x-axis rotation)  ***use camera physical pose, the x axis is pointing up,y axis is pointing left
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
}
