/*
 * Copyright 2014 Thomas Hoffmann
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.j4velin.mapsmeasure;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.util.Pair;
import android.util.TypedValue;

import com.google.android.gms.maps.model.LatLng;
import com.google.maps.android.PolyUtil;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;
import java.util.Stack;
import java.util.WeakHashMap;

abstract class Util {

    public static float lastElevation;
    private static final String TAG_OPEN = "<elevation>";
    private static final String TAG_CLOSE = "</elevation>";
    private static final String ERROR_OPEN = "<error_message>";
    private static final String ERROR_CLOSE = "</error_message>";
    private static final String STATUS_OVER_LIMIT = "OVER_QUERY_LIMIT";
    private static final int ELEVATION_TRACE_SAMPLES = 20;

    private final static WeakHashMap<LatLng, Float> CACHE = new WeakHashMap<>();

    /**
     * Returns the height of the status bar
     * from http://mrtn.me/blog/2012/03/17/get-the-height-of-the-status-bar-in-android/
     *
     * @param c the Context
     * @return the height of the status bar
     */
    static int getStatusBarHeight(final Context c) {
        int result = 0;
        int resourceId = c.getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            result = c.getResources().getDimensionPixelSize(resourceId);
        }
        return result;
    }

    /**
     * Returns the height of the navigation bar
     *
     * @param c the Context
     * @return the height of the navigation bar
     */
    static int getNavigationBarHeight(final Context c) {
        int result = 0;
        int resourceId =
                c.getResources().getIdentifier("navigation_bar_height", "dimen", "android");
        if (resourceId > 0) {
            result = c.getResources().getDimensionPixelSize(resourceId);
        }
        return result;
    }

    /**
     * Converts the given lenght in dp into pixels
     *
     * @param c  the Context
     * @param dp the size in dp
     * @return the size in px
     */
    static int dpToPx(final Context c, int dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
                c.getResources().getDisplayMetrics());
    }

    /**
     * Writes the given trace of points to the given file in CSV format,
     * separated by ";"
     *
     * @param f     the file to write to
     * @param trace the trace to write
     * @throws IOException
     */
    static void saveToFile(final File f, final Stack<LatLng> trace) throws IOException {
        if (!f.exists()) f.createNewFile();
        BufferedWriter out = new BufferedWriter(new FileWriter(f));
        LatLng current;
        for (int i = 0; i < trace.size(); i++) {
            current = trace.get(i);
            out.append(String.valueOf(current.latitude)).append(",")
                    .append(String.valueOf(current.longitude)).append("\n");
        }
        out.close();
    }

    /**
     * Replaces the current points on the map with the one from the provided
     * file
     *
     * @param f the file to read from
     * @param m the Map activity to add the new points to
     * @throws IOException
     */
    static void loadFromFile(final Uri f, final Map m) throws IOException {
        List<LatLng> list = new LinkedList<>();
        BufferedReader in = new BufferedReader(
                new InputStreamReader(m.getContentResolver().openInputStream(f)));
        String line;
        String[] data;
        while ((line = in.readLine()) != null) {
            data = line.split(",");
            if (data.length != 2) data = line.split(";"); // try with semicolon instead
            try {
                list.add(new LatLng(Double.parseDouble(data[0]), Double.parseDouble(data[1])));
            } catch (Exception nfe) {
                nfe.printStackTrace();
            }
        }
        in.close();
        m.clear();
        for (int i = 0; i < list.size(); i++) {
            m.addPoint(list.get(i));
        }
        if (!list.isEmpty()) m.moveCamera(list.get(0));
    }

    /**
     * Queries for a single elevation information
     *
     * @param loc the location
     * @return a pair of 0's
     */
    private static Pair<Float, Float> getSingleElevation(final LatLng loc) throws IOException {
        if (BuildConfig.DEBUG) Logger.log("get elevation for " + loc);
        if (CACHE.containsKey(loc)) {
            lastElevation = CACHE.get(loc);
            if (BuildConfig.DEBUG) Logger.log("cache -> " + lastElevation);
        } else {
            HttpURLConnection urlConnection = null;
            BufferedReader in = null;
            try {
                URL url = new URL("https://maps.googleapis.com/maps/api/elevation/xml?locations=" +
                        loc.latitude + "," + loc.longitude + "&key=" + Map.ELEVATION_API_KEY);
                urlConnection = (HttpURLConnection) url.openConnection();
                in = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
                String line;
                int subStringStart = TAG_OPEN.length();
                while ((line = in.readLine()) != null) {
                    if (line.trim().startsWith(ERROR_OPEN)) {
                        String error =
                                line.substring(ERROR_OPEN.length() + 1, line.indexOf(ERROR_CLOSE));
                        if (BuildConfig.DEBUG) Logger.log("error: " + error);
                        throw new IOException(error);
                    } else if (line.trim().startsWith(TAG_OPEN)) {
                        lastElevation = Float.parseFloat(
                                line.substring(subStringStart + 2, line.indexOf(TAG_CLOSE)));
                        CACHE.put(loc, lastElevation);
                        if (BuildConfig.DEBUG)
                            Logger.log("elevation read: " + line + " " + lastElevation);
                        break;
                    }
                }
            } finally {
                if (in != null) {
                    try {
                        in.close();
                    } catch (IOException e) {
                        if (BuildConfig.DEBUG) Logger.log(e);
                    }
                }
                if (urlConnection != null) urlConnection.disconnect();
            }
        }
        return new Pair<>(0f, 0f);
    }

    /**
     * Updates the elevations graph
     *
     * @param view  the graph
     * @param trace the points
     * @return the aggregated up and down distances along the trace
     */
    static Pair<Float, Float> updateElevationView(final ElevationView view,
                                                  final List<LatLng> trace) throws IOException {
        if (BuildConfig.DEBUG) Logger.log("get elevation for trace " + trace);
        if (trace.isEmpty()) return new Pair<>(0f, 0f);
        if (trace.size() == 1) return getSingleElevation(trace.get(0));
        String encodedPath = PolyUtil.encode(trace);
        float[] result = new float[ELEVATION_TRACE_SAMPLES];
        HttpURLConnection urlConnection = null;
        BufferedReader in = null;
        try {
            URL url = new URL("https://maps.googleapis.com/maps/api/elevation/xml?path=enc:" +
                    encodedPath + "&samples=" + ELEVATION_TRACE_SAMPLES + "&key=" +
                    Map.ELEVATION_API_KEY);
            if (BuildConfig.DEBUG) Logger.log("url " + url);
            urlConnection = (HttpURLConnection) url.openConnection();
            in = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
            String line;
            int pos = 0;
            int subStringStart = TAG_OPEN.length();
            while ((line = in.readLine()) != null) {
                if (line.trim().startsWith(ERROR_OPEN)) {
                    String error =
                            line.substring(ERROR_OPEN.length() + 1, line.indexOf(ERROR_CLOSE));
                    if (BuildConfig.DEBUG) Logger.log("error: " + error);
                    throw new IOException(error);
                } else if (line.trim().startsWith(TAG_OPEN)) {
                    result[pos] = Float.parseFloat(
                            line.substring(subStringStart + 2, line.indexOf(TAG_CLOSE)));
                    if (BuildConfig.DEBUG) Logger.log("result[" + pos + "]=" + result[pos]);
                    pos++;
                }
            }
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    if (BuildConfig.DEBUG) Logger.log(e);
                }
            }
            if (urlConnection != null) urlConnection.disconnect();
        }
        view.setElevationData(result);
        float up = 0, down = 0, difference;
        for (int i = 1; i < result.length; i++) {
            difference = result[i - 1] - result[i];
            if (difference < 0) up += difference;
            else down += difference;
        }
        lastElevation = result[result.length - 1];
        return new Pair<>(-1 * up, down);
    }

    /**
     * Tests for an internet connection.
     *
     * @param context the Context
     * @return true, if connected to the internet
     */
    static boolean checkInternetConnection(final Context context) {
        final ConnectivityManager cm =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        // test for connection
        return cm.getActiveNetworkInfo() != null && cm.getActiveNetworkInfo().isAvailable() &&
                cm.getActiveNetworkInfo().isConnected();
    }

}
