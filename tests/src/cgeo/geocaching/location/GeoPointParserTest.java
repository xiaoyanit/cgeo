package cgeo.geocaching.location;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

import cgeo.geocaching.utils.Formatter;

import android.test.AndroidTestCase;

public class GeoPointParserTest extends AndroidTestCase {

    private static final double refLongitude = 8.0 + 38.564 / 60.0;
    private static final double refLatitude = 49.0 + 56.031 / 60.0;

    public static void testParseLatitude() {
        assertEquals(refLatitude, GeopointParser.parseLatitude("N 49° 56.031"), 1e-8);
    }

    public static void testParseLongitude() {
        assertEquals(refLongitude, GeopointParser.parseLongitude("E 8° 38.564"), 1e-8);
    }

    public static void testFullCoordinates() {
        final Geopoint goal = new Geopoint(refLatitude, refLongitude);
        assertEquals(goal, GeopointParser.parse("N 49° 56.031 | E 8° 38.564"), (float) 1e-6);
    }

    private static void assertEquals(final Geopoint expected, final Geopoint actual, final float tolerance) {
        assertThat(expected).isNotNull();
        assertThat(actual).isNotNull();
        assertThat(expected.distanceTo(actual)).isLessThanOrEqualTo(tolerance);
    }

    public static void testCoordinateMissingPart() {
        // we are trying to parse a _point_, but have only a latitude. Don't accept the numerical part as longitude!
        Geopoint point = null;
        try {
            point = GeopointParser.parse("N 49° 56.031");
        } catch (final Geopoint.ParseException e) {
            // expected
        }
        assertThat(point).isNull();
    }

    public static void testCoordinateMissingDegree() {
        // Some home coordinates on geocaching.com lack the degree part.
        final Geopoint point = GeopointParser.parse("N 51° 23.123' W ° 17.123");
        assertThat(point).isEqualTo(new Geopoint("N", "51", "23", "123", "W", "0", "17", "123"));
    }

    public static void testSouth() {
        assertEquals(-refLatitude, GeopointParser.parseLatitude("S 49° 56.031"), 1e-8);
    }

    public static void testWest() {
        assertEquals(-refLongitude, GeopointParser.parseLongitude("W 8° 38.564"), 1e-8);
    }

    public static void testLowerCase() {
        assertEquals(refLongitude, GeopointParser.parseLongitude("e 8° 38.564"), 1e-8);
    }

    public static void testVariousFormats() {
        final Geopoint goal1 = GeopointParser.parse("N 49° 43' 57\" | E 2 12' 35");
        final Geopoint goal2 = GeopointParser.parse("N 49 43.95 E2°12.5833333333");
        assertEquals(goal1, goal2, (float) 1e-6);
    }

    public static void testParseOurOwnSeparator() {
        final Geopoint separator = GeopointParser.parse("N 49° 43' 57\"" + Formatter.SEPARATOR + "E 2 12' 35");
        final Geopoint noSeparator = GeopointParser.parse("N 49 43.95 E2°12.5833333333");
        assertEquals(separator, noSeparator, (float) 1e-6);
    }

    public static void testInSentence() {
        final Geopoint p1 = GeopointParser.parse("Station3: N51 21.523 / E07 02.680");
        final Geopoint p2 = GeopointParser.parse("N51 21.523 E07 02.680");
        assertThat(p1).isNotNull();
        assertThat(p2).isNotNull();
        assertThat(p2).isEqualTo(p1);
    }

    public static void testUnrelatedParts() {
        Geopoint point = null;
        try {
            point = GeopointParser.parse("N51 21.523 and some words in between, so there is no relation E07 02.680");
        } catch (final Geopoint.ParseException e) {
            // expected
        }
        assertThat(point).isNull();
    }

    public static void testComma() {
        final Geopoint pointComma = GeopointParser.parse("N 46° 27' 55,65''\n" +
                "E 15° 53' 41,68''");
        final Geopoint pointDot = GeopointParser.parse("N 46° 27' 55.65''\n" +
                "E 15° 53' 41.68''");
        assertThat(pointComma).isNotNull();
        assertThat(pointDot).isNotNull();
        assertThat(pointDot).isEqualTo(pointComma);
    }

    public static void testBlankAddedByAutocorrectionDot() {
        assertEquals(refLatitude, GeopointParser.parseLatitude("N 49° 56. 031"), 1e-8);
    }

    public static void testBlankAddedByAutocorrectionComma() {
        assertEquals(refLatitude, GeopointParser.parseLatitude("N 49° 56, 031"), 1e-8);
    }

    public static void testNonTrimmed() {
        assertEquals(refLatitude, GeopointParser.parseLatitude("    N 49° 56, 031   "), 1e-8);
    }

    public static void testEquatorGC53() {
        assertEquals(new Geopoint(0, 36), GeopointParser.parse("00° 00.000 E 036° 00.000"));
    }

    public static void testMeridian() {
        assertEquals(new Geopoint(23, 0), GeopointParser.parse("N 23° 00.000 00° 00.000"));
    }

    public static void testEquatorMeridian() {
        assertThat(GeopointParser.parse("00° 00.000 00° 00.000")).isEqualTo(Geopoint.ZERO);
    }

    public static void testFloatingPointLatitude() {
        assertThat(GeopointParser.parseLatitude("47.648883")).isEqualTo(GeopointParser.parseLatitude("N 47° 38.933"), offset(1e-6));
    }

    public static void testFloatingPointNegativeLatitudeMeansSouth() {
        assertThat(GeopointParser.parseLatitude("-47.648883")).isEqualTo(GeopointParser.parseLatitude("S 47° 38.933"), offset(1e-6));
    }

    public static void testFloatingPointBoth() {
        assertEquals(GeopointParser.parse("47.648883  122.348067"), GeopointParser.parse("N 47° 38.933 E 122° 20.884"), 1e-4f);
        assertEquals(GeopointParser.parse("47.648883  -122.348067"), GeopointParser.parse("N 47° 38.933 W 122° 20.884"), 1e-4f);
    }

    public static void testFloatingPointNbsp() {
        assertEquals(GeopointParser.parse("47.648883  122.348067\u00a0"), GeopointParser.parse("N 47° 38.933 E 122° 20.884"), 1e-4f);
    }

}
