package chenyue.arfyp.common.informationUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Consumer;

public class InformationManager {
    private String facilityName;
    private ArrayList<String> eventArray = new ArrayList<>();  // events being held inside the facility
    private Map<String, String> facilityDetails = new HashMap<>();  // other detailed information about the facility, like height
    private boolean expended = true;

    public InformationManager(String facilityName, String target) {
        this.eventArray.add(0, "connecting network...");
        this.facilityName = facilityName;
        new NetworkEnquiry(facilityName, target, this).start();
    }

    public void setEventArray(JSONArray eventArray) throws JSONException {
        this.eventArray.clear();
        for (int idx = 0; idx < eventArray.length(); idx++) {
            JSONObject eventItem = eventArray.getJSONObject(idx);
            String eventString = String.format("%s  time: %s", eventItem.getString("event_name"), eventItem.getString("time"));
            this.eventArray.add(idx, eventString);
        }
    }

    public void setFailedMessage(String message) {
        eventArray.clear();
        eventArray.add(0, message);
    }

    public void setFacilityDetails(JSONArray informationArray) throws JSONException {
        synchronized (facilityDetails) {
            for (int idx = 0; idx < informationArray.length(); idx++) {
                JSONObject details = informationArray.getJSONObject(idx);
                Iterator<String> keys = details.keys();
                facilityDetails.clear();
                keys.forEachRemaining(new Consumer<String>() {
                    @Override
                    public void accept(String key) {
                        try {
                            if (key.equals("coordinates")) {
                                double coordinates[] = (double[]) details.get(key);
                                facilityDetails.put("coordsE", String.valueOf(coordinates[0]));
                                facilityDetails.put("coordsN", String.valueOf(coordinates[1]));
                            } else {
                                String property = String.valueOf(details.get(key));
                                facilityDetails.put(key, property);
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                });
            }
            facilityDetails.notifyAll();
        }
    }

    public ArrayList<String> getEventArray() {
        return eventArray;
    }

    public String getFacilityName() {
        return facilityName;
    }

    public void setFacilityName(String facilityName) {
        this.facilityName = facilityName;
    }

    public Map<String, String> getFacilityDetails() {
        return facilityDetails;
    }

    public boolean isExpended() {
        return expended;
    }

    public void setExpended(boolean expended) {
        this.expended = expended;
    }
}
