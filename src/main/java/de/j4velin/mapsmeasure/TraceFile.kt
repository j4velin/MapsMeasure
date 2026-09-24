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

package de.j4velin.mapsmeasure

import android.content.ContentResolver
import android.net.Uri
import com.google.android.gms.maps.model.LatLng
import java.io.File
import java.io.IOException
import java.io.Reader
import java.io.Writer

/**
 * Reads and writes traces as CSV: one "latitude,longitude" line per point.
 * When reading, ";" is accepted as separator, too.
 */
object TraceFile {

    /**
     * Writes the given trace of points to the given file
     */
    @JvmStatic
    @Throws(IOException::class)
    fun save(file: File, trace: List<LatLng>) = file.bufferedWriter().use { write(it, trace) }

    /**
     * Reads the trace stored at the given uri
     */
    @Throws(IOException::class)
    fun load(resolver: ContentResolver, uri: Uri): List<LatLng> {
        val stream = resolver.openInputStream(uri) ?: throw IOException("Can not open $uri")
        return stream.reader().use { read(it) }
    }

    fun write(out: Writer, trace: List<LatLng>) {
        for (point in trace) {
            out.append(point.latitude.toString()).append(",")
                .append(point.longitude.toString()).append("\n")
        }
    }

    /**
     * Reads all points, skipping lines which are not a valid point
     */
    fun read(input: Reader): List<LatLng> =
        input.buffered().lineSequence().mapNotNull { parseLine(it) }.toList()

    private fun parseLine(line: String): LatLng? {
        var data = splitLine(line, ',')
        if (data.size != 2) data = splitLine(line, ';')
        if (data.size < 2) return null
        val lat = data[0].toDoubleOrNull() ?: return null
        val lng = data[1].toDoubleOrNull() ?: return null
        return LatLng(lat, lng)
    }

    // like Java's String.split: trailing empty values are dropped
    private fun splitLine(line: String, separator: Char) =
        line.split(separator).dropLastWhile { it.isEmpty() }
}
