package org.ecloga.dynamico;

import android.content.Context;
import android.view.ViewGroup;

import org.ecloga.dynamico.network.ApiResponse;
import org.ecloga.dynamico.network.FileDownload;
import org.ecloga.dynamico.style.ViewFactory;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;

final class DynamicoLayoutLoader {

    private static final String TAG = "Dynamico.DynamicoLayoutLoader";
    private StringBuilder json = null;

    private String url, name;
    private ViewGroup layout;
    private Context context;
    private Boolean mUseCache = false;
    private DynamicoListener listener;

    DynamicoLayoutLoader(String url, String name, ViewGroup layout) {
        this.url = url;
        this.name = name;
        this.layout = layout;
        this.context = layout.getContext();

        if(!this.name.endsWith(".json")) {
            this.name += ".json";
        }
    }

    DynamicoLayoutLoader(StringBuilder json, String name, ViewGroup layout, Boolean useCache) {
        this.url = null;
        this.json = json;
        this.name = name;
        this.layout = layout;
        this.context = layout.getContext();
        this.mUseCache = useCache;

        if(!this.name.endsWith(".json")) {
            this.name += ".json";
        }
    }

    public void setListener(DynamicoListener listener) {
        this.listener = listener;
    }

    public void loadLayoutFromServer() {
        Util.log(TAG, "Loading from server");

        new FileDownload(getDirectoryUrl(name), context, getStoragePath(name, context))
                .addHandler(new ApiResponse() {
                    @Override
                    public void onSuccess(String response) {
                        addViews(response);
                    }

                    @Override
                    public void onError(String message) {
                        Util.log("Server error", message);

                        loadLayoutFromCache();

                        if(listener != null) {
                            listener.onError(message);
                        }
                    }
                })
                .start();
    }

    public void loadLayoutFromCache() {
        Util.log(TAG, "Loading from cache");

        File file = new File(getStoragePath(name, context));

        try {
            BufferedReader reader = new BufferedReader(new FileReader(file));

            StringBuilder content = new StringBuilder();
            String line;

            while((line = reader.readLine()) != null) {
                content.append(line);
            }

            addViews(content.toString());

            reader.close();
        }catch(IOException e) {
            Util.log("File error", "Loading layout from cache produced the following error: " + e.getMessage());

            loadLayoutFromServer();

            if(listener != null) {
                listener.onError(e.getMessage());
            }
        }
    }

    public void loadLayoutFromString() {
        Util.log(TAG, "Loading from String");

        File file = new File(getStoragePath(name, context));

        try {
            //if the string is not a valid json obj it will throw a JSONException
            JSONObject obj = new JSONObject(json.toString());

            addViews(obj);

            if(mUseCache) {
                //if adding the view was successfull, write the content to a file so we can use the Cached content as fallback
                //OutputStreamWriter outputStreamWriter = new OutputStreamWriter(context.openFileOutput("config.txt", Context.MODE_PRIVATE));
                OutputStreamWriter outputStreamWriter = new OutputStreamWriter(context.openFileOutput(name, Context.MODE_PRIVATE));
                outputStreamWriter.write(json.toString(), 0, json.toString().length());
                outputStreamWriter.flush(); //make sure all the data was written to the file
                outputStreamWriter.close();
            }

        }catch(JSONException e) {
            Util.log("File error", "Loading layout from String produced the following error: " + e.getMessage());
            if(mUseCache)
                loadLayoutFromCache();
            if(listener != null) {
                listener.onError(e.getMessage());
            }
        } catch (IOException e) {
            Util.log("File error", "Loading layout from String produced the following error: " + e.getMessage());
            if(listener != null) {
                listener.onError(e.getMessage());
            }
        }
    }

    private void addViews(JSONObject content){
        addViews(content.toString());
    }

    private void addViews(String content) {
        ViewFactory factory = new ViewFactory(context);

        layout.removeAllViews();

        try {
            JSONObject obj = new JSONObject(content);

            if(obj.has("targets")) {
                JSONArray targets = obj.getJSONArray("targets");

                boolean foundLayout = false;

                for(int i = 0; i < targets.length(); i++) {
                    JSONObject target = targets.getJSONObject(i);

                    String targetKey = target.getString("key");
                    String targetValue = target.getString("value");

                    String matcher = null;

                    if(target.has("matcher")) {
                        matcher = target.getString("matcher");
                    }

                    if(Device.matches(targetKey, targetValue, matcher)) {
                        factory.addViews(layout, target);

                        foundLayout = true;

                        break;
                    }
                }

                if(!foundLayout && obj.has("default")) {
                    factory.addViews(layout, obj.getJSONObject("default"));
                }
            }else {
                factory.addViews(layout, obj);
            }

            if(listener != null) {
                listener.onSuccess(content);
            }
        }catch(Exception e) {
            Util.log("Layout error", e.getMessage());

            if(listener != null) {
                listener.onError(content);
            }
        }
    }

    private String getDirectoryUrl(String name) {
        return this.url + "/" + name;
    }

    private String getStoragePath(String name, Context context) {
        return context.getFilesDir() + File.separator + name;
    }
}