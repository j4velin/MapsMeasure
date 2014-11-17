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
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.TextView;

import com.google.android.gms.maps.GoogleMap;

public class DrawerListAdapter extends BaseAdapter {

    private final static int ID_EDITTEXT = 0;
    private final static int ID_DIVIDER = 1;
    private final static int ID_ITEM = 2;
    private final static int ID_SMALL_ITEM = 3;

    private final static int[] TYPE_AT_POSITION =
            {ID_EDITTEXT, ID_DIVIDER, ID_ITEM, ID_ITEM, ID_ITEM, ID_ITEM, ID_DIVIDER, ID_ITEM,
                    ID_ITEM, ID_ITEM, ID_DIVIDER, ID_SMALL_ITEM, ID_SMALL_ITEM, ID_SMALL_ITEM};

    private final static int[] STRING_AT_POSITION =
            {0, R.string.section_measure, R.string.units, R.string.measure_distance,
                    R.string.measure_area, R.string.measure_elevation, R.string.section_mapview,
                    R.string.mapview_map, R.string.mapview_satellite, R.string.mapview_terrain,
                    R.string.about, R.string.savenshare, R.string.moreapps, R.string.about};

    private final static int[] ICON_AT_POSITION =
            {0, 0, R.drawable.ic_metric, R.drawable.ic_distance, R.drawable.ic_area,
                    R.drawable.ic_elevation, 0, R.drawable.ic_mapview_map,
                    R.drawable.ic_mapview_satellite, R.drawable.ic_mapview_terrain, 0,
                    R.drawable.ic_action_save, R.drawable.ic_store, R.drawable.ic_about};

    private final LayoutInflater mInflater;

    private final Map map;

    private int selected_type, selected_view;

    public DrawerListAdapter(final Map m) {
        map = m;
        mInflater = (LayoutInflater) m.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    @Override
    public int getViewTypeCount() {
        // Search, Divider, Measure/View items, small items = 4
        return 4;
    }

    @Override
    public int getCount() {
        return TYPE_AT_POSITION.length;
    }

    @Override
    public Object getItem(int i) {
        return null;
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        int type = getItemViewType(position);
        if (type == ID_EDITTEXT) {
            convertView = mInflater.inflate(R.layout.listitem_edittext, null);
            ((EditText) convertView.findViewById(R.id.search))
                    .setOnEditorActionListener(new TextView.OnEditorActionListener() {
                        @Override
                        public boolean onEditorAction(final TextView v, int actionId, final KeyEvent event) {
                            if (event == null || event.getAction() == KeyEvent.ACTION_DOWN) {
                                new GeocoderTask(map).execute(v.getText().toString());
                                InputMethodManager inputManager = (InputMethodManager) map
                                        .getSystemService(Context.INPUT_METHOD_SERVICE);
                                inputManager.hideSoftInputFromWindow(
                                        map.getCurrentFocus().getWindowToken(),
                                        InputMethodManager.HIDE_NOT_ALWAYS);
                                map.closeDrawer();
                            }
                            return true;
                        }
                    });
        } else {
            ViewHolder holder;
            if (convertView == null) {
                holder = new ViewHolder();
                switch (type) {
                    case ID_DIVIDER:
                        convertView = mInflater.inflate(R.layout.listitem_separator, null);
                        holder.view = (TextView) convertView.findViewById(R.id.separator);
                        break;
                    case ID_ITEM:
                        convertView = mInflater.inflate(R.layout.listitem_item, null);
                        holder.view = (TextView) convertView.findViewById(R.id.item);
                        break;
                    case ID_SMALL_ITEM:
                        convertView = mInflater.inflate(R.layout.listitem_small_item, null);
                        holder.view = (TextView) convertView.findViewById(R.id.smallitem);
                        break;
                }
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }
            holder.view.setText(STRING_AT_POSITION[position]);
            holder.view
                    .setCompoundDrawablesWithIntrinsicBounds(ICON_AT_POSITION[position], 0, 0, 0);
            if (type == ID_ITEM) {
                holder.view.setBackgroundResource(
                        selected_type == position || selected_view == position ?
                                R.drawable.background_selected : R.drawable.background_normal);
            }
        }
        return convertView;
    }

    @Override
    public int getItemViewType(int position) {
        return TYPE_AT_POSITION[position];
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    public static class ViewHolder {
        public TextView view;
    }

    public void changeType(final Map.MeasureType newType) {
        switch (newType) {
            case DISTANCE:
                selected_type = 3;
                break;
            case AREA:
                selected_type = 4;
                break;
            case ELEVATION:
                selected_type = 5;
                break;
        }
        notifyDataSetInvalidated();
    }

    public void changeView(final int newView) {
        switch (newView) {
            case GoogleMap.MAP_TYPE_NORMAL:
                selected_view = 7;
                break;
            case GoogleMap.MAP_TYPE_HYBRID:
                selected_view = 8;
                break;
            case GoogleMap.MAP_TYPE_TERRAIN:
                selected_view = 9;
                break;
        }
        notifyDataSetInvalidated();
    }
}
