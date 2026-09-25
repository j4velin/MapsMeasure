package de.j4velin.mapsmeasure

import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.StringReader
import java.io.StringWriter

class TraceFileTest {

    private fun read(csv: String) = TraceFile.read(StringReader(csv))

    @Test
    fun writesOnePointPerLine() {
        val out = StringWriter()
        TraceFile.write(out, listOf(LatLng(52.5, 13.25), LatLng(-33.75, 151.0)))
        assertEquals("52.5,13.25\n-33.75,151.0\n", out.toString())
    }

    @Test
    fun roundTripKeepsFullPrecision() {
        val trace = listOf(LatLng(52.520008, 13.404954), LatLng(48.85661400000001, 2.3522219), LatLng(0.0, -0.1))
        val out = StringWriter()
        TraceFile.write(out, trace)
        assertEquals(trace, read(out.toString()))
    }

    @Test
    fun readsCommaAndSemicolon() {
        assertEquals(listOf(LatLng(1.0, 2.0), LatLng(3.5, 4.5)), read("1.0,2.0\n3.5;4.5\n"))
    }

    @Test
    fun acceptsWindowsLineEndingsWhitespaceAndMissingLastNewline() {
        assertEquals(listOf(LatLng(1.0, 2.0), LatLng(3.0, 4.0)), read("1.0,2.0\r\n 3 , 4 "))
    }

    @Test
    fun acceptsTrailingSeparator() {
        assertEquals(listOf(LatLng(1.0, 2.0), LatLng(3.0, 4.0)), read("1,2,\n3;4;\n"))
    }

    @Test
    fun skipsInvalidLines() {
        val csv = "latitude,longitude\n\n1,2\n1,2,3\nabc\n5,x\n1,2;3\n6,7\n"
        assertEquals(listOf(LatLng(1.0, 2.0), LatLng(6.0, 7.0)), read(csv))
    }

    @Test
    fun ignoresExtraSemicolonValues() {
        // as the Java version did: only the comma format has to have exactly two values
        assertEquals(listOf(LatLng(1.0, 2.0)), read("1;2;3\n"))
    }

    @Test
    fun emptyInputGivesEmptyTrace() {
        assertEquals(emptyList<LatLng>(), read(""))
    }
}
