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

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Stack;

abstract class Util {

    private static HashMap<LatLng, Float> elevationCache;

    /**
     * Returns the height of the status bar
     * <p/>
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
            out.append(String.valueOf(current.latitude)).append(";")
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
            data = line.split(";");
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
    }

    /**
     * Get the altitude data for a specific point
     *
     * @param p the point to get the altitude for
     * @return the altitude at point p or -Float.MAX_VALUE if no valid data
     * could be fetched
     * @throws IOException
     */
    static float getAltitude(final LatLng p) throws IOException {
        if (elevationCache == null) {
            elevationCache = new HashMap<>(30);
        }
        if (elevationCache.containsKey(p)) {
            return elevationCache.get(p);
        } else {
            float altitude = -Float.MAX_VALUE;
            URL url = new URL("http://maps.googleapis.com/maps/api/elevation/xml?locations=" +
                    String.valueOf(p.latitude) + "," + String.valueOf(p.longitude) +
                    "&sensor=true");
            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
            try {
                InputStream in = new BufferedInputStream(urlConnection.getInputStream());
                int r;
                StringBuilder respStr = new StringBuilder();
                while ((r = in.read()) != -1) respStr.append((char) r);
                String tagOpen = "<elevation>";
                String tagClose = "</elevation>";
                if (respStr.indexOf(tagOpen) != -1) {
                    int start = respStr.indexOf(tagOpen) + tagOpen.length();
                    int end = respStr.indexOf(tagClose);
                    altitude = Float.parseFloat(respStr.substring(start, end));
                    elevationCache.put(p, altitude);
                }
                in.close();
            } finally {
                urlConnection.disconnect();
            }
            return altitude;
        }
    }

    /**
     * Calculates the up- & downwards elevation along the passed trace.
     * <p/>
     * This method might need to connect to the Google Elevation API and
     * therefore must not be called from the main UI thread.
     *
     * @param trace the list of LatLng objects
     * @return null if an error occurs, a pair of two float values otherwise:
     * first one is the upwards elevation, second one downwards
     * <p/>
     * based on
     * http://stackoverflow.com/questions/1995998/android-get-altitude
     * -by-longitude-and-latitude
     */
    static Pair<Float, Float> getElevation(final List<LatLng> trace) {
        float up = 0, down = 0;
        float lastElevation = -Float.MAX_VALUE, currentElevation;
        float difference;
        try {
            for (LatLng p : trace) {
                currentElevation = getAltitude(p);

                // current and last point have a valid elevation data ->
                // calculate difference
                if (currentElevation > -Float.MAX_VALUE && lastElevation > -Float.MAX_VALUE) {
                    difference = currentElevation - lastElevation;
                    if (difference > 0) up += difference;
                    else down += difference;
                }
                lastElevation = currentElevation;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return new Pair<>(up, down);
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
