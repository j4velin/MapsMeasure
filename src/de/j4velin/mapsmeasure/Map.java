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

import java.io.IOException;
import java.io.Serializable;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Stack;

import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMap.OnMapClickListener;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Polygon;
import com.google.android.gms.maps.model.PolygonOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.maps.android.SphericalUtil;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.BadParcelableException;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v4.widget.DrawerLayout.DrawerListener;
import android.text.method.LinkMovementMethod;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.TextView.OnEditorActionListener;

public class Map extends FragmentActivity {

	// the map to draw to
	private GoogleMap mMap;
	private DrawerLayout mDrawerLayout;

	// private ActionBarDrawerToggle mDrawerToggle;

	// the stacks - everytime the user touches the map, an entry is pushed
	private Stack<LatLng> trace = new Stack<LatLng>();
	private Stack<Polyline> lines = new Stack<Polyline>();
	private Stack<Polygon> points = new Stack<Polygon>();

	private Polygon areaOverlay;

	private float distance; // in meters
	private boolean showArea; // in square meters
	private TextView valueTv; // the view displaying the distance/area & unit

	private final static int COLOR_LINE = Color.argb(128, 0, 0, 0), COLOR_POINT = Color.argb(128, 255, 0, 0);

	private final static NumberFormat formatter = NumberFormat.getInstance(Locale.getDefault());

	public boolean metric; // display in metric units

	/**
	 * Get the formatted string for the valueTextView.
	 * 
	 * Depending on whether 'showArea' is set, the returned string shows the
	 * distance of the trace or the area between them. If 'showArea' is set,
	 * this call might be expensive as the area is computed here and not cached.
	 * 
	 * @return the formatted text for the valueTextView
	 */
	private String getFormattedString() {
		if (!showArea) {
			if (metric) {
				if (distance > 1000)
					return formatter.format(distance / 1000) + " km";
				else
					return formatter.format(Math.max(0, distance)) + " m";
			} else {
				if (distance > 1609)
					return formatter.format(distance / 1609.344f) + " mi";
				else
					return formatter.format(Math.max(0, distance / 0.9144f)) + " yd";
			}
		} else {
			double area;
			if (areaOverlay != null)
				areaOverlay.remove();
			if (trace.size() >= 3) {
				area = SphericalUtil.computeArea(trace);
				areaOverlay = mMap.addPolygon(new PolygonOptions().addAll(trace).strokeWidth(0).fillColor(COLOR_POINT));
			} else {
				area = 0;
			}
			if (metric) {
				return formatter.format(Math.max(0, area)) + " m²";
			} else {
				return formatter.format(Math.max(0, area / 4046.8564f)) + " ac";
			}
		}
	}

	@Override
	protected void onRestoreInstanceState(final Bundle savedInstanceState) {
		super.onRestoreInstanceState(savedInstanceState);
		metric = savedInstanceState.getBoolean("metric");
		@SuppressWarnings("unchecked")
		Stack<LatLng> tmp = (Stack<LatLng>) savedInstanceState.getSerializable("trace");
		Iterator<LatLng> it = tmp.iterator();
		while (it.hasNext()) {
			addPoint(it.next());
		}
		mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(savedInstanceState.getDouble("position-lat"),
				savedInstanceState.getDouble("position-lon")), savedInstanceState.getFloat("position-zoom")));
	}

	@Override
	protected void onSaveInstanceState(final Bundle outState) {
		outState.putSerializable("trace", trace);
		outState.putBoolean("metric", metric);
		outState.putDouble("position-lon", mMap.getCameraPosition().target.longitude);
		outState.putDouble("position-lat", mMap.getCameraPosition().target.latitude);
		outState.putFloat("position-zoom", mMap.getCameraPosition().zoom);
		super.onSaveInstanceState(outState);
	}

	/**
	 * Adds a new point, calculates the new distance and draws the point and a
	 * line to it
	 * 
	 * @param p
	 *            the new point
	 */
	private void addPoint(final LatLng p) {
		if (!trace.isEmpty()) {
			lines.push(mMap.addPolyline(new PolylineOptions().color(COLOR_LINE).add(trace.peek()).add(p)));
			distance += SphericalUtil.computeDistanceBetween(p, trace.peek());
		}
		points.push(drawCircle(p));
		trace.push(p);
		updateValueText();
	}

	/**
	 * Removes the last added point, the line to it and updates the distance
	 */
	private void removeLast() {
		if (trace.isEmpty())
			return;
		points.pop().remove();
		LatLng remove = trace.pop();
		if (!trace.isEmpty())
			distance -= SphericalUtil.computeDistanceBetween(remove, trace.peek());
		if (!lines.isEmpty())
			lines.pop().remove();
		updateValueText();
	}

	@SuppressLint("NewApi")
	@Override
	public void onCreate(final Bundle savedInstanceState) {
		try {
			super.onCreate(savedInstanceState);
		} catch (final BadParcelableException bpe) {
			Serializable t = savedInstanceState.getSerializable("trace");
			savedInstanceState.remove("trace");
			super.onCreate(savedInstanceState);
			savedInstanceState.putSerializable("trace", t);
		}
		setContentView(R.layout.activity_map);

		final SharedPreferences prefs = getSharedPreferences("settings", Context.MODE_PRIVATE);

		metric = prefs.getBoolean("metric", true);

		final View topCenterOverlay = findViewById(R.id.topCenterOverlay);
		mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
		if (mDrawerLayout != null) {
			mDrawerLayout.setDrawerShadow(R.drawable.drawer_shadow, GravityCompat.START);

			mDrawerLayout.setDrawerListener(new DrawerListener() {

				@Override
				public void onDrawerStateChanged(int newState) {

				}

				@TargetApi(Build.VERSION_CODES.HONEYCOMB)
				@Override
				public void onDrawerSlide(final View drawerView, final float slideOffset) {
					if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.HONEYCOMB)
						topCenterOverlay.setAlpha(1 - slideOffset);
				}

				@Override
				public void onDrawerOpened(View drawerView) {
					if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.HONEYCOMB)
						topCenterOverlay.setVisibility(View.INVISIBLE);
				}

				@Override
				public void onDrawerClosed(View drawerView) {
					if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.HONEYCOMB)
						topCenterOverlay.setVisibility(View.VISIBLE);
				}
			});
		}

		mMap = ((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map)).getMap();

		if (mMap == null) {
			Dialog d = GooglePlayServicesUtil.getErrorDialog(GooglePlayServicesUtil.isGooglePlayServicesAvailable(this), this, 0);
			d.setOnDismissListener(new OnDismissListener() {
				@Override
				public void onDismiss(DialogInterface dialog) {
					finish();
				}
			});
			d.show();
			return;
		}

		valueTv = (TextView) findViewById(R.id.distance);
		updateValueText();
		valueTv.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(final View v) {
				if (mDrawerLayout != null)
					mDrawerLayout.openDrawer(GravityCompat.START);
			}
		});

		View delete = findViewById(R.id.delete);
		delete.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(final View v) {
				removeLast();
			}
		});
		delete.setOnLongClickListener(new OnLongClickListener() {
			@Override
			public boolean onLongClick(final View v) {
				AlertDialog.Builder builder = new AlertDialog.Builder(Map.this);
				builder.setMessage(getString(R.string.delete_all, trace.size()));
				builder.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						mMap.clear();
						trace.clear();
						lines.clear();
						points.clear();
						distance = 0;
						updateValueText();
						dialog.dismiss();
					}
				});
				builder.setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						dialog.dismiss();
					}
				});
				builder.create().show();
				return true;
			}
		});

		mMap.setOnMapClickListener(new OnMapClickListener() {
			@Override
			public void onMapClick(final LatLng center) {
				addPoint(center);
			}
		});

		// KitKat translucent decor enabled? -> Add some margin/padding to the
		// drawer and the map
		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {

			int statusbar = Util.getStatusBarHeight(this);

			FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) topCenterOverlay.getLayoutParams();
			lp.setMargins(10, statusbar + 10, 0, 0);
			topCenterOverlay.setLayoutParams(lp);

			DisplayMetrics total, available;
			total = new DisplayMetrics();
			available = new DisplayMetrics();
			getWindowManager().getDefaultDisplay().getMetrics(available);
			getWindowManager().getDefaultDisplay().getRealMetrics(total);

			// on most devices and in most orientations, the navigation bar
			// should be at the bottom and therefore reduces the available
			// display height
			int navBarHeight = total.heightPixels - available.heightPixels - Util.getStatusBarHeight(this);

			if (getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
				// in landscape on phones, the navigation bar might be at the
				// right side, reducing the available display width
				int navBarWidth = total.widthPixels - available.widthPixels;

				mMap.setPadding(mDrawerLayout == null ? Util.dpToPx(this, 200) : 0, statusbar, navBarWidth, navBarHeight);
				findViewById(R.id.left_drawer).setPadding(0, statusbar + 10, 0, navBarHeight);
			} else {
				mMap.setPadding(0, statusbar, 0, navBarHeight);
				findViewById(R.id.left_drawer).setPadding(0, statusbar + 10, 0, navBarHeight);
			}

		}

		mMap.setMyLocationEnabled(true);

		LatLng userLocation = null; // location to move to

		if (getIntent().getDoubleExtra("lon", 1000) < 999) {
			double lon = getIntent().getDoubleExtra("lon", 0);
			double lat = getIntent().getDoubleExtra("lat", 0);
			userLocation = new LatLng(lat, lon);
		} else {
			Location location = mMap.getMyLocation();
			if (location == null) {
				LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
				List<String> matchingProviders = locationManager.getAllProviders();
				float accuracy, bestAccuracy = Float.MAX_VALUE;
				long time, minTime = 0;
				Location tmp;
				for (String provider : matchingProviders) {
					tmp = locationManager.getLastKnownLocation(provider);
					if (tmp != null) {
						accuracy = tmp.getAccuracy();
						time = tmp.getTime();

						if ((time > minTime && accuracy < bestAccuracy)) {
							location = tmp;
							bestAccuracy = accuracy;
							minTime = time;
						} else if (time < minTime && bestAccuracy == Float.MAX_VALUE && time > minTime) {
							location = tmp;
							minTime = time;
						}
					}
				}
			}
			if (location != null) {
				userLocation = new LatLng(location.getLatitude(), location.getLongitude());
			}
		}

		if (userLocation != null) {
			mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(userLocation, 16));
		}

		// Drawer stuff
		((EditText) findViewById(R.id.search)).setOnEditorActionListener(new OnEditorActionListener() {
			@Override
			public boolean onEditorAction(final TextView v, int actionId, final KeyEvent event) {
				if (event.getAction() == KeyEvent.ACTION_DOWN) {
					new GeocoderTask().execute(v.getText().toString());
					InputMethodManager inputManager = (InputMethodManager) Map.this
							.getSystemService(Context.INPUT_METHOD_SERVICE);
					inputManager.hideSoftInputFromWindow(Map.this.getCurrentFocus().getWindowToken(),
							InputMethodManager.HIDE_NOT_ALWAYS);
					if (mDrawerLayout != null)
						mDrawerLayout.closeDrawers();
				}
				return true;
			}
		});

		final View metricTV = findViewById(R.id.metric);
		metricTV.setBackgroundResource(metric ? R.drawable.background_selected : R.drawable.background_normal);
		metricTV.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(final View v) {
				metric = !metric;
				metricTV.setBackgroundResource(metric ? R.drawable.background_selected : R.drawable.background_normal);
				updateValueText();
				prefs.edit().putBoolean("metric", metric).commit();
				if (mDrawerLayout != null)
					mDrawerLayout.closeDrawers();
			}
		});

		toggleSatelliteView(prefs.getBoolean("satellite", false));
		toggleArea(false);

		findViewById(R.id.mapview_map).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				toggleSatelliteView(false);
			}
		});
		findViewById(R.id.mapview_satellite).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				toggleSatelliteView(true);
			}
		});
		findViewById(R.id.measure_area).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				toggleArea(true);
			}
		});
		findViewById(R.id.measure_distance).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				toggleArea(false);
			}
		});
		findViewById(R.id.about).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				AlertDialog.Builder builder = new AlertDialog.Builder(Map.this);
				builder.setTitle(R.string.about);

				TextView tv = new TextView(Map.this);
				tv.setPadding(10, 10, 10, 10);

				try {
					tv.setText(R.string.about_text);
					tv.append(getString(R.string.app_version,
							Map.this.getPackageManager().getPackageInfo(Map.this.getPackageName(), 0).versionName));
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
				builder.create().show();
			}
		});
		findViewById(R.id.moreapps).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				try {
					startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=pub:j4velin"))
							.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
				} catch (ActivityNotFoundException anf) {
					startActivity(new Intent(Intent.ACTION_VIEW, Uri
							.parse("https://play.google.com/store/apps/developer?id=j4velin"))
							.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
				}
			}
		});
	}

	/**
	 * Change between measuring a distance and an area
	 * 
	 * @param measureArea
	 *            true to measure area
	 */
	private void toggleArea(boolean measureArea) {
		showArea = measureArea;
		findViewById(R.id.measure_area).setBackgroundResource(
				measureArea ? R.drawable.background_selected : R.drawable.background_normal);
		findViewById(R.id.measure_distance).setBackgroundResource(
				measureArea ? R.drawable.background_normal : R.drawable.background_selected);
		formatter.setMaximumFractionDigits(showArea ? 0 : 2);
		updateValueText();
		if (mDrawerLayout != null)
			mDrawerLayout.closeDrawers();
		if (!showArea) {
			if (areaOverlay != null)
				areaOverlay.remove();
		}
	}

	/**
	 * Change between normal map and satellite hybrid view
	 * 
	 * @param enable
	 *            true to switch to hybrid view
	 */
	private void toggleSatelliteView(boolean enable) {
		mMap.setMapType(enable ? GoogleMap.MAP_TYPE_HYBRID : GoogleMap.MAP_TYPE_NORMAL);
		findViewById(R.id.mapview_satellite).setBackgroundResource(
				enable ? R.drawable.background_selected : R.drawable.background_normal);
		findViewById(R.id.mapview_map).setBackgroundResource(
				enable ? R.drawable.background_normal : R.drawable.background_selected);
		if (mDrawerLayout != null)
			mDrawerLayout.closeDrawers();
		getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putBoolean("satellite", enable).commit();
	}

	private LatLng getPoint(final LatLng center, final float radius, final double angle) {
		double east = radius * Math.cos(angle);
		double north = radius * Math.sin(angle);

		double cLat = center.latitude;
		double cLng = center.longitude;
		double latRadius = SphericalUtil.EARTH_RADIUS * Math.cos(cLat / 180 * Math.PI);

		double newLat = cLat + (north / SphericalUtil.EARTH_RADIUS / Math.PI * 180);
		double newLng = cLng + (east / latRadius / Math.PI * 180);

		return new LatLng(newLat, newLng);
	}

	/**
	 * Draws a circle at the given point.
	 * 
	 * Should be called when the users touches the map and adds an entry to the
	 * stacks
	 * 
	 * @param center
	 *            the point where the user clicked
	 * @return the drawn Polygon
	 */
	private Polygon drawCircle(final LatLng center) {
		int totalPonts = 30; // number of corners of the pseudo-circle
		List<LatLng> points = new ArrayList<LatLng>(totalPonts);
		float radius = (float) (750000 / Math.pow(2, mMap.getCameraPosition().zoom));
		for (int i = 0; i < totalPonts; i++) {
			points.add(getPoint(center, radius, i * 2 * Math.PI / totalPonts));
		}
		return mMap.addPolygon(new PolygonOptions().addAll(points).strokeWidth(0).fillColor(COLOR_POINT));
	}

	/**
	 * 
	 * Based on
	 * http://wptrafficanalyzer.in/blog/android-geocoding-showing-user-input
	 * -location-on-google-map-android-api-v2/
	 * 
	 * @author George Mathew
	 * 
	 */
	private class GeocoderTask extends AsyncTask<String, Void, Address> {

		@Override
		protected Address doInBackground(final String... locationName) {
			// Creating an instance of Geocoder class
			Geocoder geocoder = new Geocoder(getBaseContext());
			try {
				// Get only the best result that matches the input text
				List<Address> addresses = geocoder.getFromLocationName(locationName[0], 1);
				return addresses != null && !addresses.isEmpty() ? addresses.get(0) : null;
			} catch (IOException e) {
				e.printStackTrace();
				return null;
			}
		}

		@Override
		protected void onPostExecute(final Address address) {
			if (address == null) {
				Toast.makeText(getBaseContext(), R.string.no_location_found, Toast.LENGTH_SHORT).show();
			} else {
				mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(address.getLatitude(), address.getLongitude()),
						Math.max(10, mMap.getCameraPosition().zoom)));
			}
		}
	}

	/**
	 * Updates the valueTextView at the top of the screen
	 */
	private void updateValueText() {
		valueTv.setText(getFormattedString());
	}
}
