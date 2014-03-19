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
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Stack;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMap.OnMapClickListener;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Polygon;
import com.google.android.gms.maps.model.PolygonOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.maps.android.SphericalUtil;

import android.content.Context;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android.widget.Toast;

public class Map extends FragmentActivity {

	// the map to draw to
	private GoogleMap mMap;

	// the stacks - everytime the user touches the map, an entry is pushed
	private Stack<LatLng> trace = new Stack<LatLng>();
	private Stack<Polyline> lines = new Stack<Polyline>();
	private Stack<Polygon> points = new Stack<Polygon>();

	private float distance; // in meters
	private TextView distanceTv; // the view displaying the distance & unit

	private final static int COLOR_LINE = Color.argb(128, 0, 0, 0), COLOR_POINT = Color.argb(128, 255, 0, 0);

	private final static NumberFormat formatter = NumberFormat.getInstance(Locale.getDefault());

	private String unit = "m";

	/**
	 * Update the distance textview.
	 * 
	 * Should be called when the distance or the unit changes
	 */
	private void updateDistance() {
		float d = distance;
		if (unit.equals("m")) {
			if (d > 1000) {
				unit = "km";
				updateDistance();
				return;
			}
		} else if (unit.equals("km")) {
			if (d < 1000) {
				unit = "m";
				updateDistance();
				return;
			}
			d /= 1000;
		} else if (unit.equals("yd")) {
			if (distance > 1609) {
				unit = "mi";
				updateDistance();
				return;
			}
			d = distance / 0.9144f;
		} else if (unit.equals("mi")) {
			if (d < 1609) {
				unit = "yd";
				updateDistance();
				return;
			}
			d = distance / 1609.344f;
		}

		distanceTv.setText(formatter.format(Math.max(0, d)) + " " + unit);
	}

	@Override
	protected void onRestoreInstanceState(final Bundle savedInstanceState) {
		super.onRestoreInstanceState(savedInstanceState);
		unit = savedInstanceState.getString("unit");
		@SuppressWarnings("unchecked")
		Stack<LatLng> tmp = (Stack<LatLng>) savedInstanceState.getSerializable("trace");
		Iterator<LatLng> it = tmp.iterator();
		while (it.hasNext()) {
			addPoint(it.next());
		}
		mMap.moveCamera(CameraUpdateFactory.newCameraPosition((CameraPosition) savedInstanceState.getParcelable("position")));
	}

	@Override
	protected void onSaveInstanceState(final Bundle outState) {
		outState.putSerializable("trace", trace);
		outState.putString("unit", unit);
		outState.putParcelable("position", mMap.getCameraPosition());
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
			updateDistance();
		}
		points.push(drawCircle(p));
		trace.push(p);
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
		updateDistance();
	}

	@Override
	public void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_map);

		mMap = ((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map)).getMap();
		distanceTv = (TextView) findViewById(R.id.distance);
		distanceTv.setText("0 " + unit);
		distanceTv.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(final View v) {
				if (unit.equals("m") || unit.equals("km")) {
					unit = "yd";
				} else {
					unit = "m";
				}
				// will change to km/mi if needed
				updateDistance();
			}
		});

		final EditText search = (EditText) findViewById(R.id.search);
		search.setOnEditorActionListener(new OnEditorActionListener() {
			@Override
			public boolean onEditorAction(final TextView v, int actionId, KeyEvent event) {
				new GeocoderTask().execute(search.getText().toString());
				InputMethodManager inputManager = (InputMethodManager) Map.this.getSystemService(Context.INPUT_METHOD_SERVICE);
				inputManager.hideSoftInputFromWindow(Map.this.getCurrentFocus().getWindowToken(),
						InputMethodManager.HIDE_NOT_ALWAYS);
				return true;
			}
		});

		findViewById(R.id.delete).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(final View v) {
				removeLast();
			}
		});

		formatter.setMaximumFractionDigits(2);

		mMap.setOnMapClickListener(new OnMapClickListener() {
			@Override
			public void onMapClick(final LatLng center) {
				addPoint(center);
			}
		});

		// KitKat translucent decor enabled? -> Add some margin/padding to the
		// textview and the map
		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
			View topleft = findViewById(R.id.topleft);
			FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) topleft.getLayoutParams();
			lp.setMargins(10, Util.getStatusBarHeight(this) + 10, 0, 0);
			topleft.setLayoutParams(lp);

			mMap.setPadding(0, Util.getStatusBarHeight(this), 0, Util.getNavigationBarHeight(this));
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
		for (int i = 0; i < totalPonts; i++) {
			points.add(getPoint(center, 10, i * 2 * Math.PI / totalPonts));
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
				Toast.makeText(Map.this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
				e.printStackTrace();
				return null;
			}
		}

		@Override
		protected void onPostExecute(final Address address) {
			if (address == null) {
				Toast.makeText(getBaseContext(), "No Location found", Toast.LENGTH_SHORT).show();
			} else {
				mMap.animateCamera(CameraUpdateFactory.newLatLng(new LatLng(address.getLatitude(), address.getLongitude())));
			}
		}
	}
}
