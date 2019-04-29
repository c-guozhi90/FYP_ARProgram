package chenyue.arfyp.navigation;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.LinkedList;

public class Navigation implements Runnable {
    private static String TAG = "navigationThread";
    public final static LinkedList<Node> path = new LinkedList<>();
    private static boolean START_NAVIGATION = false;
    public static double navigationAngle = 0;   // from -PI to PI
    public static boolean targetReached = false;
    private final Context context;
    private double pointsDirection;
    private double orientationOffset;
    private double distance;

    public Navigation(Context context) {
        this.context = context;
    }

    public static void setPath(JSONArray jsonArray) throws JSONException {
        if (path.size() != 0) path.clear();
        for (int idx = 0; idx < jsonArray.length(); idx++) {
            Node newNode = new Node();
            JSONObject tempObject = jsonArray.getJSONObject(idx);
            newNode.nodeName = tempObject.getString("nodeName");
            newNode.floor = tempObject.getInt("floor");
            JSONArray tempArray = (JSONArray) tempObject.get("coordinates");
            newNode.coordinates[0] = (double) tempArray.get(0);
            newNode.coordinates[1] = (double) tempArray.get(1);
            path.add(newNode);
        }
    }

    public static void setStart(boolean toStart) {
        START_NAVIGATION = toStart;
    }

    public static float[] returnTargetCoordinates() {
        Node lastNode = path.getLast();
        float[] coords = {(float) lastNode.coordinates[0], (float) lastNode.coordinates[1]};
        return coords;
    }

    static class Node {
        String nodeName;
        double[] coordinates = new double[2];
        int floor;

    }

    public static void calculateNavigationAngle() {
        Node aheadNode = path.getFirst();
        double pointDegree = Math.atan2(aheadNode.coordinates[0] - CoordsCalculation.curPosition[0], aheadNode.coordinates[1] - CoordsCalculation.curPosition[1]);

    }

    public void run() {
        while (true) {
            if (!START_NAVIGATION) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                continue;
            }
            Node aheadNode = path.get(0);
            distance = DistanceEstimation.calculateDistance(CoordsCalculation.curPosition[0], CoordsCalculation.curPosition[1], aheadNode.coordinates[0], aheadNode.coordinates[1]);
            synchronized (path) {
                if (distance < 1) {
                    path.removeFirst();
                }
                if (path.size() == 0) {
                    targetReached = true;
                    break;
                }
            }
            calculateNavigationAngle();
        }
    }

    /**
     * log down the navigation related data for analysis
     * q
     */
    public void logDown() {
        String logPath = context.getExternalCacheDir().getAbsolutePath() + "/log-navigation.txt";
        Log.d(TAG, "log file here: " + logPath);
        try {
            BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(logPath, true)));
            bw.write("current target node: " + path.getFirst().nodeName);
            bw.write("distance: " + distance);
            bw.write("navigation angle: " + navigationAngle);
            bw.write("points direction: " + pointsDirection);
            bw.write("orientation offset: " + orientationOffset);
            bw.flush();
            bw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static boolean isTargetReached() {

        return false;
    }
}
