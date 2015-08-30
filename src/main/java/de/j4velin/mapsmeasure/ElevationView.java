/*
 * Copyright 2015 Thomas Hoffmann
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
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

public class ElevationView extends View {

    private float[] elevations;
    private final Paint paint = new Paint();
    private final Paint fillPaint = new Paint();
    private final Paint strokePaint = new Paint();

    public ElevationView(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        paint.setTextSize(28f);
        paint.setStrokeWidth(2f);
        paint.setColor(Color.BLACK);
        fillPaint.setColor(Color.argb(32, 128, 128, 128));
        fillPaint.setStyle(Paint.Style.FILL);
        strokePaint.setColor(Color.BLACK);
        strokePaint.setStrokeWidth(2f);
        strokePaint.setStyle(Paint.Style.STROKE);
    }

    /**
     * Sets the elevation data.
     * Call invalidate to update the view
     *
     * @param elevations the new data
     */
    public void setElevationData(final float[] elevations) {
        this.elevations = elevations;
    }

    @Override
    public void draw(final Canvas canvas) {
        super.draw(canvas);
        if (elevations == null) return;
        float max = elevations[0], min = elevations[0];
        for (float e : elevations) {
            if (e < min) min = e;
            else if (e > max) max = e;
        }
        String minStr = String.valueOf((int) (Map.metric ? min : min / 0.3048f));
        String maxStr = String.valueOf((int) (Map.metric ? max : max / 0.3048f));

        Rect textBound = new Rect();
        paint.getTextBounds(minStr, 0, minStr.length(), textBound);
        int textWidth = textBound.width();
        paint.getTextBounds(maxStr, 0, maxStr.length(), textBound);
        textWidth = Math.max(textWidth, textBound.width()) + getPaddingLeft() + 5;
        int width = getWidth();
        int height = getHeight();
        // y axis
        canvas.drawLine(textWidth, getPaddingTop(), textWidth, height - getPaddingBottom(), paint);

        // y axis label
        canvas.drawText(minStr, getPaddingLeft(), height - getPaddingBottom(), paint);
        canvas.drawText(maxStr, getPaddingLeft(), getPaddingTop() + textBound.height(), paint);

        int availableWidth = width - textWidth - getPaddingRight();
        int availableHeight = height - getPaddingBottom() - getPaddingTop();

        Path path = new Path();
        path.moveTo(textWidth,
                availableHeight - ((elevations[0] - min) / (max - min)) * availableHeight +
                        getPaddingTop());

        for (int i = 1; i < elevations.length; i++) {
            path.lineTo(((float) i / (elevations.length - 1)) * availableWidth + textWidth,
                    availableHeight - ((elevations[i] - min) / (max - min)) * availableHeight +
                            getPaddingTop());
        }
        path.lineTo(availableWidth + textWidth, height - getPaddingBottom());
        path.lineTo(textWidth, height - getPaddingBottom());
        path.lineTo(textWidth,
                availableHeight - ((elevations[0] - min) / (max - min)) * availableHeight +
                        getPaddingTop());

        canvas.drawPath(path, fillPaint);

        canvas.drawPath(path, strokePaint);
    }
}
