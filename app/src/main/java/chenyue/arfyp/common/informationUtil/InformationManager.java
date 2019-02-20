package chenyue.arfyp.common.informationUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

public class InformationManager {
    private String facilityName;
    private ArrayList<String> informationArray = new ArrayList<>();
    private boolean expended = true;

    public InformationManager(String facilityName) {
        this.informationArray.add(0, "connecting network...");
        this.facilityName = facilityName;
        new NetworkEnquiry(facilityName, this).start();
    }

    public void setInformationArray(JSONArray informationArray) throws JSONException {
        this.informationArray.clear();
        for (int idx = 0; idx < informationArray.length(); idx++) {
            JSONObject eventItem = informationArray.getJSONObject(idx);
            String eventString = String.format("%s  time: %s", eventItem.getString("event_name"), eventItem.getString("time"));
            this.informationArray.add(idx, eventString);
        }
    }

    public void setInformationArray(String message) {
        informationArray.clear();
        informationArray.add(0, message);
    }

    public ArrayList<String> getInformationArray() {
        return informationArray;
    }

    public String getFacilityName() {
        return facilityName;
    }

    public void setFacilityName(String facilityName) {
        this.facilityName = facilityName;
    }

    public boolean isExpended() {
        return expended;
    }

    public void setExpended(boolean expended) {
        this.expended = expended;
    }
}
