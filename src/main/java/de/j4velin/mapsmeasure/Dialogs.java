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

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Environment;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.Window;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.google.android.gms.maps.model.LatLng;

import java.io.File;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.List;

abstract class Dialogs {

    /**
     * @param c the Context
     * @return the about dialog
     */
    public static Dialog getAbout(final Context c) {
        AlertDialog.Builder builder = new AlertDialog.Builder(c);
        builder.setTitle(R.string.about);

        TextView tv = new TextView(c);
        int pad = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10,
                c.getResources().getDisplayMetrics());
        tv.setPadding(pad, pad, pad, pad);

        try {
            tv.setText(R.string.about_text);
            tv.append(c.getString(R.string.app_version,
                    c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName));
            tv.setMovementMethod(LinkMovementMethod.getInstance());
        } catch (NameNotFoundException e1) {
            // should not happen as the app is definitely installed when
            // seeing the dialog
        }
        builder.setView(tv);
        builder.setPositiveButton(android.R.string.ok, (dialog, which) -> dialog.dismiss());
        return builder.create();
    }

    /**
     * @param c     the Context
     * @param trace the current trace of points
     * @return the "save & share" dialog
     */
    public static Dialog getSaveNShare(final Activity c, final List<LatLng> trace) {
        final Dialog d = new Dialog(c);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        d.setContentView(R.layout.dialog_save);
        d.findViewById(R.id.save).setOnClickListener(v -> {
            final File destination;
            if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState()) &&
                    c.getExternalFilesDir(null) != null) {
                destination = c.getExternalFilesDir(null);
            } else {
                destination = c.getDir("traces", Context.MODE_PRIVATE);
            }

            d.dismiss();
            if (destination == null) {
                Toast.makeText(c,
                        c.getString(R.string.error, "Can not access external files directory"),
                        Toast.LENGTH_LONG).show();
                return;
            }
            AlertDialog.Builder b = new AlertDialog.Builder(c);
            b.setTitle(R.string.save);
            final View layout =
                    c.getLayoutInflater().inflate(R.layout.dialog_enter_filename, null);
            ((TextView) layout.findViewById(R.id.location)).setText(
                    c.getString(R.string.file_path, destination.getAbsolutePath() + "/"));
            b.setView(layout);
            b.setPositiveButton(R.string.save, (dialog, which) -> {
                try {
                    String fname = ((EditText) layout.findViewById(R.id.filename)).getText()
                            .toString();
                    if (fname.length() < 1) {
                        fname = "MapsMeasure_" + System.currentTimeMillis();
                    }
                    final File f = new File(destination, fname + ".csv");
                    TraceFile.save(f, trace);
                    d.dismiss();
                    Toast.makeText(c, R.string.file_saved, Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    if (BuildConfig.DEBUG) Log.d(Map.LOG_TAG, "save & share failed", e);
                    Toast.makeText(c, c.getString(R.string.error,
                                    e.getClass().getSimpleName() + "\n" + e.getMessage()),
                            Toast.LENGTH_LONG).show();
                }
            });
            b.create().show();
        });
        d.findViewById(R.id.load).setOnClickListener(v -> {

            File[] files = c.getDir("traces", Context.MODE_PRIVATE).listFiles();

            if (files == null) {
                Toast.makeText(c, c.getString(R.string.dir_read_error,
                                c.getDir("traces", Context.MODE_PRIVATE).getAbsolutePath()),
                        Toast.LENGTH_SHORT).show();
                return;
            }

            if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
                File ext = c.getExternalFilesDir(null);
                // even though we checked the external storage state, ext is still sometimes null, accoring to Play Store crash reports
                if (ext != null && ext.listFiles() != null) {
                    File[] filesExtern = ext.listFiles();
                    File[] allFiles = new File[files.length + filesExtern.length];
                    System.arraycopy(files, 0, allFiles, 0, files.length);
                    System.arraycopy(filesExtern, 0, allFiles, files.length,
                            filesExtern.length);
                    files = allFiles;
                }
            }

            if (files.length == 0) {
                Toast.makeText(c, c.getString(R.string.no_files_found,
                                c.getDir("traces", Context.MODE_PRIVATE).getAbsolutePath()),
                        Toast.LENGTH_SHORT).show();
            } else if (files.length == 1) {
                ((Map) c).loadTrace(Uri.fromFile(files[0]));
                d.dismiss();
            } else {
                d.dismiss();
                AlertDialog.Builder b = new AlertDialog.Builder(c);
                b.setTitle(R.string.select_file);
                final DeleteAdapter da = new DeleteAdapter(files, (Map) c);
                b.setAdapter(da, (dialog, which) -> {
                    ((Map) c).loadTrace(Uri.fromFile(da.getFile(which)));
                    dialog.dismiss();
                });
                b.create().show();
            }
        });
        d.findViewById(R.id.share).setOnClickListener(v -> {
            try {
                final File f = new File(c.getCacheDir(), "MapsMeasure.csv");
                TraceFile.save(f, trace);
                Intent shareIntent = new Intent();
                shareIntent.setAction(Intent.ACTION_SEND);
                shareIntent.putExtra(Intent.EXTRA_STREAM, FileProvider
                        .getUriForFile(c, "de.j4velin.mapsmeasure.fileprovider", f));
                shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                shareIntent.setType("text/comma-separated-values");
                d.dismiss();
                c.startActivity(Intent.createChooser(shareIntent, null));
            } catch (IOException e) {
                if (BuildConfig.DEBUG) Log.d(Map.LOG_TAG, "save & share failed", e);
                Toast.makeText(c, c.getString(R.string.error,
                                e.getClass().getSimpleName() + "\n" + e.getMessage()),
                        Toast.LENGTH_LONG).show();
            }
        });
        return d;
    }

    /**
     * @param m        the Map
     * @param metric   true, if metric units are selected
     * @param distance the current distance
     * @param area     the current area
     * @return the units dialog
     */
    public static Dialog getUnits(final Map m, boolean metric, double distance, double area) {
        final NumberFormat format = Units.twoDecimals();
        final Dialog d = new Dialog(m);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        d.setContentView(R.layout.dialog_unit);
        CheckBox metricCb = d.findViewById(R.id.metric);
        metricCb.setChecked(metric);
        metricCb.setOnCheckedChangeListener((buttonView, isChecked) -> m.setMetric(isChecked));
        ((TextView) d.findViewById(R.id.distance)).setText(m.getString(R.string.units_distance,
                format.format(Math.max(0, distance)),
                format.format(distance / 1000),
                format.format(Math.max(0, distance / 0.3048f)),
                format.format(Math.max(0, distance / 0.9144)),
                format.format(distance / 1609.344f),
                format.format(distance / 1852f)));

        ((TextView) d.findViewById(R.id.area)).setText(m.getString(R.string.units_area,
                format.format(Math.max(0, area)),
                format.format(area / 10000),
                format.format(area / 1000000),
                format.format(Math.max(0, area / 0.09290304d)),
                format.format(area / 4046.8726099d),
                format.format(area / 2589988.110336d)));
        d.findViewById(R.id.close).setOnClickListener(v -> d.dismiss());
        return d;
    }
}
