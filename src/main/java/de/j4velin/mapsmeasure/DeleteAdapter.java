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

import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;

/**
 * Class for showing a list of saved traces and the ability to delete them
 * 
 */
class DeleteAdapter extends BaseAdapter {

	private final ArrayList<File> files;
	private final LayoutInflater mInflater;
	private final OnClickListener deleteListener = new OnClickListener() {
		@Override
		public void onClick(final View v) {
            int file = (Integer) v.getTag();
            files.remove(file).delete();
			notifyDataSetChanged();
		}
	};

	/**
	 * @param f
	 *            the files to show
	 * @param c
	 *            the calling activity
	 */
	public DeleteAdapter(final File[] f, final Map c) {
		files = new ArrayList<>(Arrays.asList(f));
		mInflater = c.getLayoutInflater();
	}

	/**
	 * Gets a file from the list. Used to load the file when clicking on it.
	 * 
	 * @param position
	 *            of the file in the list
	 * @return the corresponding file
	 */
	File getFile(int position) {
		return files.get(position);
	}

	@Override
	public int getCount() {
		return files.size();
	}

	@Override
	public Object getItem(int position) {
		return files.get(position);
	}

	@Override
	public long getItemId(int position) {
		return position;
	}

	@SuppressWarnings("deprecation")
	@Override
	public View getView(int position, View convertView, final ViewGroup parent) {
		ViewHolder holder;
		if (convertView == null) {
			convertView = mInflater.inflate(R.layout.deletelistitem, null);
			holder = new ViewHolder();
			holder.iv = convertView.findViewById(R.id.icon);
			holder.tv = (TextView) convertView.findViewById(R.id.text);
			holder.iv.setOnClickListener(deleteListener);
			convertView.setTag(holder);
		} else {
			holder = (ViewHolder) convertView.getTag();
		}
		String filename = files.get(position).getName();
		if (filename.startsWith("MapsMeasure_")) {
			try {
				Date date = new Date(Long.parseLong(filename.substring(filename.lastIndexOf("_") + 1, filename.lastIndexOf("."))));
				filename = date.toLocaleString();
			} catch (NumberFormatException nfe) {
			}
		} else {
			filename = filename.substring(0, filename.lastIndexOf("."));
		}
		holder.tv.setText(filename);
		holder.iv.setTag(position);
		return convertView;
	}

	private static class ViewHolder {
		private TextView tv; // the TextView showing the name of the file
		private View iv; // the ImageView showing the delete icon
	}

}
