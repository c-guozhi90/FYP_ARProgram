package chenyue.arfyp.userviews;

import android.app.Activity;
import android.nfc.Tag;
import android.os.Bundle;
import android.os.Handler;
import android.util.JsonReader;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.function.Consumer;

import chenyue.arfyp.navigation.CoordsCalculation;

public class SearchActivity extends Activity implements View.OnClickListener, AdapterView.OnItemClickListener {
    private final static String TAG = "Search Activity";
    private ListView searchListView;
    private EditText searchInput;
    private Button searchButton;
    private Button cancelButton;
    private Button goThereButton;
    private ArrayAdapter<String> searchResultsItems;
    private boolean EnquiryFinished = false;
    private Handler handler;
    public static int START_FOR_NAVIGATION = 1;
    private int selected = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);
        handler = new Handler();
        searchListView = findViewById(R.id.search_list_view);
        searchInput = findViewById(R.id.search_input);
        searchButton = findViewById(R.id.search_result_button);
        goThereButton = findViewById(R.id.go_there_button);
        cancelButton = findViewById(R.id.cancel_button);
        searchButton.setOnClickListener(this);
        cancelButton.setOnClickListener(this);
        goThereButton.setOnClickListener(this);
        String[] initList = {"no result"};
        searchResultsItems = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new ArrayList<>(Arrays.asList(initList)));
        searchListView.setAdapter(searchResultsItems);
        searchListView.setOnItemClickListener(this);
    }

    @Override
    protected void onPause() {
        super.onPause();

    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.search_result_button:
                searchFacilityOnline(String.valueOf(searchInput.getText()));
                showStatusText();
                break;
            case R.id.cancel_button:
                this.finish();
                break;
            case R.id.go_there_button:
                startNavigation(selected);
                setResult(START_FOR_NAVIGATION);
                finish();
                break;
        }
    }

    private void searchFacilityOnline(String input) {
        new Thread(() -> {
            String enquiryAddress = "https://fypserverentry.herokuapp.com/api/search/" + input;
            Log.d(TAG, input);
            String result = "";
            String temp;
            try {
                URL url = new URL(enquiryAddress);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.connect();
                InputStreamReader isr = new InputStreamReader(connection.getInputStream());
                BufferedReader br = new BufferedReader(isr);
                while ((temp = br.readLine()) != null) {
                    result += temp;
                }
                br.close();
                isr.close();
                connection.disconnect();

                String finalResult = result;
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        JSONArray returnedJSONArray = null;
                        try {
                            returnedJSONArray = new JSONArray(finalResult);
                            synchronized (searchResultsItems) {
                                searchResultsItems.clear();
                                for (int idx = 0; idx < returnedJSONArray.length(); idx++) {
                                    JSONObject details = returnedJSONArray.getJSONObject(idx);
                                    Iterator<String> keys = details.keys();
                                    keys.forEachRemaining(new Consumer<String>() {
                                        @Override
                                        public void accept(String key) {
                                            try {
                                                if (key.equals("facility_name")) {
                                                    Log.d(TAG, "here");
                                                    String property = details.getString(key);
                                                    Log.d(TAG, property);
                                                    searchResultsItems.add(property);
                                                }
                                            } catch (JSONException e) {
                                                e.printStackTrace();
                                            }
                                        }
                                    });
                                }
                                EnquiryFinished = true;
                            }
                            Log.d(TAG, "" + searchResultsItems.getCount());
                            searchResultsItems.notifyDataSetChanged();
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }

                    }
                });
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void showStatusText() {
        new Thread(() -> {
            String statusText = "connecting network";
            int count = 0;
            while (!EnquiryFinished) {
                String outputText = statusText;
                for (int num = 0; num < count; num++) {
                    outputText += ".";
                }
                count = (count + 1) % 4;
                String finalOutputText = outputText;
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        synchronized (searchResultsItems) {
                            if (!EnquiryFinished) {
                                searchResultsItems.clear();
                                searchResultsItems.add(finalOutputText);
                                searchResultsItems.notifyDataSetChanged();
                            }
                        }
                    }
                });
                try {
                    Thread.sleep(250);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void searchNavigationPath(double[] startPoint, int floor, String target) {
        try {
//            while (!CoordsCalculation.readyForTracking) {
//                Thread.sleep(50);
//            }
            Socket newEnquiry = new Socket("139.199.88.99", 3001);    // set up a website
            DataOutputStream dos = new DataOutputStream(newEnquiry.getOutputStream());
            DataInputStream dis = new DataInputStream(newEnquiry.getInputStream());
            dos.writeInt(0); // operation code
            dos.writeDouble(startPoint[0]);
            dos.writeDouble(startPoint[1]);
            dos.writeInt(floor);
            dos.writeUTF(target);
            String results = dis.readUTF();
            dis.close();
            dos.close();
            JSONArray jsonArray = new JSONArray(results);
            JSONObject jsonObject = jsonArray.getJSONObject(0);
            double[] coordinates = (double[]) jsonObject.get("coordinates");  // may cause a bug

        } catch (IOException | JSONException e) {
            e.printStackTrace();
        }
    }

    private void startNavigation(int selectedTarget) {
        MainActivity.NAVIGATION_MODE = true;
        new Thread(() -> {
            String target = searchResultsItems.getItem(selectedTarget);
            while (true) {
                if (CoordsCalculation.readyForTracking) {
                    //searchNavigationPath(CoordsCalculation.initCoordinates, CoordsCalculation.floor, target);
                    break;
                } else {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }

        }).start();
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        goThereButton.setVisibility(View.VISIBLE);
        selected = position;
    }
}
