package chenyue.arfyp.navigation;


import com.google.ar.core.Camera;
import com.google.ar.core.Pose;
import com.google.ar.core.TrackingState;


/**
 * this class includes the draw function(e.g. draw user's current position on the floor plan)
 */
public class CoordsCalculation extends Thread {
    private static double curPosition[];
    public static float DEGREE_OFFSET;
    public static double[] initCoordinates = new double[2];     // first for East, second for North
    public final static double[] cameraCoords = new double[2];
    public static boolean readyForTracking = false;
    public static int floor;
    private static Camera camera;

    public CoordsCalculation(Camera ARcamera) {
        curPosition = new double[2];
        camera = ARcamera;
    }

    // keep track of user's movement and update corresponding coordinates
    public static void coordsTracking() {
        // we won't update the coordinates until it gets ready
        if (!readyForTracking || camera.getTrackingState() != TrackingState.PAUSED) return;
        Pose curpose = camera.getDisplayOrientedPose();
        double displacement = Math.sqrt(curpose.tx() * curpose.tx() + curpose.tz() * curpose.tz());
        double walkingDirection = DistanceEstimation.adjustAngle(DEGREE_OFFSET - Math.atan2(curpose.tx(), curpose.tz()));
        curPosition[0] = initCoordinates[0] + displacement * Math.sin(walkingDirection);
        curPosition[1] = initCoordinates[1] + displacement * Math.cos(walkingDirection);
    }

    public static void forcelyUpdateCoords(double[] coords) {
        // forcely update the current coordinates and initial coordinate accordingly
        curPosition[0] = coords[0];
        curPosition[1] = coords[1];
        initCoordinates[0] = curPosition[0] - coords[0];
    }


    public static void setRotationOffset(float eulerDegree) {
        DEGREE_OFFSET = eulerDegree;
    }

    public void run() {
        while (true) {
            if (!readyForTracking) {
                try {
                    sleep(200);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            while (readyForTracking) {
                coordsTracking();
                try {
                    // it is not needed to check and update the coordinates so frequently
                    sleep(200);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
