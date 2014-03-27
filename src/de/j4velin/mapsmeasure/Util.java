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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Stack;

import com.google.android.gms.maps.model.LatLng;

import android.content.Context;
import android.util.TypedValue;

public class Util {

	// from
	// http://mrtn.me/blog/2012/03/17/get-the-height-of-the-status-bar-in-android/
	public static int getStatusBarHeight(final Context c) {
		int result = 0;
		int resourceId = c.getResources().getIdentifier("status_bar_height", "dimen", "android");
		if (resourceId > 0) {
			result = c.getResources().getDimensionPixelSize(resourceId);
		}
		return result;
	}

	public static int dpToPx(final Context c, int dp) {
		return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, c.getResources().getDisplayMetrics());
	}

	/**
	 * Writes the given trace of points to the given file in CSV format,
	 * separated by ";"
	 * 
	 * @param f
	 *            the file to write to
	 * @param trace
	 *            the trace to write
	 * @throws IOException
	 */
	static void saveToFile(final File f, final Stack<LatLng> trace) throws IOException {
		if (!f.exists())
			f.createNewFile();
		BufferedWriter out = new BufferedWriter(new FileWriter(f));
		LatLng current;
		for (int i = 0; i < trace.size(); i++) {
			current = trace.get(i);
			out.append(current.latitude + ";" + current.longitude + "\n");
		}
		out.close();
	}

	/**
	 * Replaces the current points on the map with the one from the provided file
	 * 
	 * @param f the file to read from
	 * @param m the Map activity to add the new points to
	 * @throws IOException
	 */
	static void loadFromFile(final File f, final Map m) throws IOException {
		List<LatLng> list = new LinkedList<LatLng>();
		BufferedReader in = new BufferedReader(new FileReader(f));
		String line;
		String[] data;
		while ((line = in.readLine()) != null) {
			data = line.split(";");
			list.add(new LatLng(Double.parseDouble(data[0]), Double.parseDouble(data[1])));
		}
		in.close();
		m.clear();
		for (int i = 0; i < list.size(); i++) {
			m.addPoint(list.get(i));
		}
	}

}
