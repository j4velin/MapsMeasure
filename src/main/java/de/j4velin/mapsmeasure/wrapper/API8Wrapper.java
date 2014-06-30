package de.j4velin.mapsmeasure.wrapper;

import java.io.File;
import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;

@TargetApi(Build.VERSION_CODES.FROYO)
public class API8Wrapper {

	private API8Wrapper() {
	}

	public static File getExternalFilesDir(final Context c) {
		return c.getExternalFilesDir(null);
	}

}
