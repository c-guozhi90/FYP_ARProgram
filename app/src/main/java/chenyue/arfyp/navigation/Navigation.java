package chenyue.arfyp.navigation;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.LinkedList;

public class Navigation implements Runnable {
    private static LinkedList<Node> path = new LinkedList<>();
    private static boolean START_NAVIGATION = false;
    public static double navigationAngle = 0;   // from -PI to PI

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
        }
    }

    public static void setStart(boolean b) {
        START_NAVIGATION = b;
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
            double distance = DistanceEstimation.calculateDistance(CoordsCalculation.curPosition[0], CoordsCalculation.curPosition[1], aheadNode.coordinates[0], aheadNode.coordinates[1]);
            synchronized (path) {
                if (distance < 1) {
                    path.removeFirst();
                }
            }
            calculateNavigationAngle();
        }
    }
}
