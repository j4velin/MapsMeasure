package de.j4velin.mapsmeasure;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager.NameNotFoundException;
import android.text.method.LinkMovementMethod;
import android.widget.TextView;

public class Dialogs {
	
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
}
