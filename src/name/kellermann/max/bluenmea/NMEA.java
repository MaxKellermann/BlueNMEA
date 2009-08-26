/*
 * Copyright (C) 2003-2009 Max Kellermann <max@duempel.org>
 * http://max.kellermann.name/projects/blue-nmea/
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; version 2 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package name.kellermann.max.bluenmea;

import android.os.Bundle;
import android.location.Location;
import android.location.LocationManager;
import android.text.format.Time;

/**
 * This class is a container for several static methods which help
 * with generating NMEA data.
 *
 * A nice reference for NMEA is at http://www.gpsinformation.org/dale/nmea.htm
 */
final class NMEA {
    /**
     * Calculates the NMEA checksum of the specified string.  Pass the
     * portion of the line between '$' and '*' here.
     */
    public static int checksum(String s) {
        byte[] bytes = s.getBytes();
        int checksum = 0;

        for (int i = bytes.length - 1; i >= 0; --i)
            checksum = checksum ^ bytes[i];

        return checksum;
    }

    /**
     * Prepends the '$' and appends '*' followed by the hexadecimal
     * checksum.  This does not add the newline character.
     */
    public static String decorate(String s) {
        return "$" + s + "*" + String.format("%02x", checksum(s));
    }

    /**
     * Formats the time from the #Location into a string.
     */
    public static String formatTime(Location location) {
        Time time = new Time("UTC");
        time.set(location.getTime());

        return String.format("%02d%02d%02d",
                             time.hour, time.minute, time.second);

    }

    /**
     * Formats the date from the #Location into a string.
     */
    public static String formatDate(Location location) {
        Time time = new Time("UTC");
        time.set(location.getTime());

        return String.format("%02d%02d%02d",
                             time.monthDay, time.month + 1, time.year % 100);

    }

    /**
     * Formats the latitude from the #Location into a string.
     */
    public static String formatLatitude(Location location) {
        double latitude = location.getLatitude();

        return String.format("%02d%02d.%04d,N",
                             (int)latitude, (int)(latitude * 60) % 60,
                             ((int)latitude * 60 * 10000) % 10000);
    }

    /**
     * Formats the longitude from the #Location into a string.
     */
    public static String formatLongitude(Location location) {
        double longitude = location.getLongitude();

        return String.format("%03d%02d.%04d,E",
                             (int)longitude, (int)(longitude * 60) % 60,
                             ((int)longitude * 60 * 10000) % 10000);
    }

    /**
     * Formats the surface position (latitude and longitude) from the
     * #Location into a string.
     */
    public static String formatPosition(Location location) {
        return formatLatitude(location) + "," + formatLongitude(location);
    }

    /**
     * Formats the number of satellites from the #Location into a
     * string.  In case #LocationManager.NETWORK_PROVIDER is used, it
     * returns the faked value "1", because some software refuses to
     * work with a "0" or an empty value.
     */
    public static String formatSatellites(Location location) {
        if (location.getProvider().equals(LocationManager.GPS_PROVIDER)) {
            Bundle bundle = location.getExtras();
            return bundle != null
                ? "" + bundle.getInt("satellites")
                : "";
        } else if (location.getProvider().equals(LocationManager.NETWORK_PROVIDER))
            // fake this variable
            return "1";
        else
            return "";
    }

    /**
     * Formats the altitude from the #Location into a string, with a
     * second unit field ("M" for meters).  If the altitude is
     * unknown, it returns two empty fields.
     */
    public static String formatAltitude(Location location) {
        String s = "";
        if (location.hasAltitude())
            s += location.getAltitude() + ",M";
        else
            s += ",";
        return s;
    }

    /**
     * Formats the speed in knots from the #Location into a string.
     * If the speed is unknown, it returns an empty string.
     */
    public static String formatSpeedKt(Location location) {
        String s = "";
        if (location.hasSpeed())
            // http://www.google.com/search?q=m%2Fs+to+kt
            s += (location.getSpeed() * 1.94384449);
        return s;
    }

    /**
     * Formats the bearing from the #Location into a string.  If the
     * bearing is unknown, it returns an empty string.
     */
    public static String formatBearing(Location location) {
        String s = "";
        if (location.hasBearing())
            s += location.hasBearing();
        return s;
    }
}
