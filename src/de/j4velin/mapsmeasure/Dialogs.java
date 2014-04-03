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

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.Stack;

import com.google.android.gms.maps.model.LatLng;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.support.v4.content.FileProvider;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.CompoundButton.OnCheckedChangeListener;

public class Dialogs {

	/**
	 * @param c
	 *            the Context
	 * @return the about dialog
	 */
	public static Dialog getAbout(final Context c) {
		AlertDialog.Builder builder = new AlertDialog.Builder(c);
		builder.setTitle(R.string.about);

		TextView tv = new TextView(c);
		int pad = (Util.dpToPx(c, 10));
		tv.setPadding(pad, pad, pad, pad);

		try {
			tv.setText(R.string.about_text);
			tv.append(c.getString(R.string.app_version, c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName));
			tv.setMovementMethod(LinkMovementMethod.getInstance());
		} catch (NameNotFoundException e1) {
			// should not happen as the app is definitely installed when
			// seeing the dialog
			e1.printStackTrace();
		}
		builder.setView(tv);
		builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(final DialogInterface dialog, int which) {
				dialog.dismiss();
			}
		});
		return builder.create();
	}

	/**
	 * @param c
	 *            the Context
	 * @param trace
	 *            the current trace of points
	 * @return the "save & share" dialog
	 */
	public static Dialog getSaveNShare(final Context c, final Stack<LatLng> trace) {
		final Dialog d = new Dialog(c);
		d.requestWindowFeature(Window.FEATURE_NO_TITLE);
		d.setContentView(R.layout.dialog_save);
		d.findViewById(R.id.save).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(final View v) {
				try {
					final File f = new File(c.getDir("traces", Context.MODE_PRIVATE), "MapsMeasure_" + System.currentTimeMillis()
							+ ".csv");
					Util.saveToFile(f, trace);
					d.dismiss();
					Toast.makeText(c, c.getString(R.string.file_saved_to, f.getAbsolutePath()), Toast.LENGTH_LONG).show();
				} catch (Exception e) {
					Toast.makeText(c, c.getString(R.string.error, e.getClass().getSimpleName() + "\n" + e.getMessage()),
							Toast.LENGTH_LONG).show();
				}
			}
		});
		d.findViewById(R.id.load).setOnClickListener(new OnClickListener() {
			@SuppressWarnings("deprecation")
			@Override
			public void onClick(final View v) {
				final File[] files = c.getDir("traces", Context.MODE_PRIVATE).listFiles();
				if (files.length == 0) {
					Toast.makeText(c,
							c.getString(R.string.no_files_found, c.getDir("traces", Context.MODE_PRIVATE).getAbsolutePath()),
							Toast.LENGTH_SHORT).show();
				} else if (files.length == 1) {
					try {
						Util.loadFromFile(Uri.fromFile(files[0]), (Map) c);
						d.dismiss();
					} catch (IOException e) {
						e.printStackTrace();
						Toast.makeText(c, c.getString(R.string.error, e.getClass().getSimpleName() + "\n" + e.getMessage()),
								Toast.LENGTH_LONG).show();
					}
				} else {
					AlertDialog.Builder b = new AlertDialog.Builder(c);
					b.setTitle(R.string.select_file);
					CharSequence[] items = new CharSequence[files.length];
					String filename;
					Date date;
					for (int i = 0; i < files.length; i++) {
						filename = files[i].getName();
						try {
							date = new Date(Long.parseLong(filename.substring(filename.lastIndexOf("_") + 1,
									filename.lastIndexOf("."))));
							items[i] = date.toLocaleString();
						} catch (NumberFormatException nfe) {
							items[i] = filename;
						}
					}
					b.setItems(items, new DialogInterface.OnClickListener() {
						@Override
						public void onClick(final DialogInterface dialog, int which) {
							try {
								Util.loadFromFile(Uri.fromFile(files[which]), (Map) c);
								dialog.dismiss();
							} catch (IOException e) {
								e.printStackTrace();
								Toast.makeText(c,
										c.getString(R.string.error, e.getClass().getSimpleName() + "\n" + e.getMessage()),
										Toast.LENGTH_LONG).show();
							}
						}
					});
					b.create().show();
					d.dismiss();
				}
			}
		});
		d.findViewById(R.id.share).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(final View v) {
				try {
					final File f = new File(c.getCacheDir(), "MapsMeasure.csv");
					Util.saveToFile(f, trace);
					Intent shareIntent = new Intent();
					shareIntent.setAction(Intent.ACTION_SEND);
					shareIntent.putExtra(Intent.EXTRA_STREAM,
							FileProvider.getUriForFile(c, "de.j4velin.mapsmeasure.fileprovider", f));
					shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
					shareIntent.setType("text/comma-separated-values");
					d.dismiss();
					c.startActivity(Intent.createChooser(shareIntent, null));
				} catch (IOException e) {
					e.printStackTrace();
					Toast.makeText(c, c.getString(R.string.error, e.getClass().getSimpleName() + "\n" + e.getMessage()),
							Toast.LENGTH_LONG).show();
				}
			}
		});
		return d;
	}

	/**
	 * @param m
	 *            the Map
	 * @param distance
	 *            the current distance
	 * @param area
	 *            the current area
	 * @return the units dialog
	 */
	public static Dialog getUnits(final Map m, float distance, double area) {
		final Dialog d = new Dialog(m);
		d.requestWindowFeature(Window.FEATURE_NO_TITLE);
		d.setContentView(R.layout.dialog_unit);
		CheckBox metricCb = (CheckBox) d.findViewById(R.id.metric);
		metricCb.setChecked(m.metric);
		metricCb.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				m.metric = !m.metric;
				m.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putBoolean("metric", isChecked).commit();
				m.updateValueText();
			}
		});
		((TextView) d.findViewById(R.id.distance)).setText(Map.formatter_two_dec.format(Math.max(0, distance)) + " m\n"
				+ Map.formatter_two_dec.format(distance / 1000) + " km\n\n"
				+ Map.formatter_two_dec.format(Math.max(0, distance / 0.3048f)) + " ft\n"
				+ Map.formatter_two_dec.format(Math.max(0, distance / 0.9144)) + " yd\n"
				+ Map.formatter_two_dec.format(distance / 1609.344f) + " mi\n"
				+ Map.formatter_two_dec.format(distance / 1852f) + " nautical miles");

		((TextView) d.findViewById(R.id.area)).setText(Map.formatter_two_dec.format(Math.max(0, area)) + " m²\n"
				+ Map.formatter_two_dec.format(area / 10000) + " ha\n" + Map.formatter_two_dec.format(area / 1000000)
				+ " km²\n\n" + Map.formatter_two_dec.format(Math.max(0, area / 0.09290304d)) + " ft²\n"
				+ Map.formatter_two_dec.format(area / 4046.8726099d) + " ac (U.S. Survey)\n"
				+ Map.formatter_two_dec.format(area / 2589988.110336d) + " mi²");
		d.findViewById(R.id.close).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(final View v) {
				d.dismiss();
			}
		});
		return d;
	}
}
