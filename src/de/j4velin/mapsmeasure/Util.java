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

public class Util {

	// from http://mrtn.me/blog/2012/03/17/get-the-height-of-the-status-bar-in-android/
	public static int getStatusBarHeight(final Context c) {
		int result = 0;
		int resourceId = c.getResources().getIdentifier("status_bar_height", "dimen", "android");
		if (resourceId > 0) {
			result = c.getResources().getDimensionPixelSize(resourceId);
		}
		return result;
	}

	// from http://mrtn.me/blog/2012/03/17/get-the-height-of-the-status-bar-in-android/
	public static int getNavigationBarHeight(final Context c) {
		int result = 0;
		int resourceId = c.getResources().getIdentifier("navigation_bar_height", "dimen", "android");
		if (resourceId > 0) {
			result = c.getResources().getDimensionPixelSize(resourceId);
		}
		return result;
	}

}
