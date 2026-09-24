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
import android.net.Uri;
import android.util.TypedValue;

import com.google.android.gms.maps.model.LatLng;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.LinkedList;
import java.util.List;
import java.util.Stack;

abstract class Util {

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
        try (BufferedWriter out = new BufferedWriter(new FileWriter(f))) {
            for (LatLng current : trace) {
                out.append(String.valueOf(current.latitude)).append(",")
                        .append(String.valueOf(current.longitude)).append("\n");
            }
        }
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
        InputStream stream = m.getContentResolver().openInputStream(f);
        if (stream == null) throw new IOException("Can not open " + f);
        try (BufferedReader in = new BufferedReader(new InputStreamReader(stream))) {
            String line;
            String[] data;
            while ((line = in.readLine()) != null) {
                data = line.split(",");
                if (data.length != 2) data = line.split(";"); // try with semicolon instead
                try {
                    list.add(new LatLng(Double.parseDouble(data[0]), Double.parseDouble(data[1])));
                } catch (Exception nfe) {
                    if (BuildConfig.DEBUG) Logger.log(nfe);
                }
            }
        }
        m.clear();
        for (int i = 0; i < list.size(); i++) {
            m.addPoint(list.get(i));
        }
        if (!list.isEmpty()) m.moveCamera(list.get(0));
    }

}
