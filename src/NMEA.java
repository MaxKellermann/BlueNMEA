/*
 * Copyright (C) 2003-2011 Max Kellermann <max@duempel.org>
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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import android.os.Bundle;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationManager;
import android.text.format.Time;
import android.util.Log;

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
        char suffix = latitude < 0 ? 'S' : 'N';
        latitude = Math.abs(latitude);

        return String.format("%02d%02d.%04d,%c",
                             (int)latitude,
                             (int)(latitude * 60) % 60,
                             (int)(latitude * 60 * 10000) % 10000,
                             suffix);
    }

    /**
     * Formats the longitude from the #Location into a string.
     */
    public static String formatLongitude(Location location) {
        double longitude = location.getLongitude();
        char suffix = longitude < 0 ? 'W' : 'E';
        longitude = Math.abs(longitude);

        return String.format("%03d%02d.%04d,%c",
                             (int)longitude,
                             (int)(longitude * 60) % 60,
                             (int)(longitude * 60 * 10000) % 10000,
                             suffix);
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
            s += location.getBearing();
        return s;
    }

    public static String formatGpsGsa(GpsStatus gps) {
        String fix = "1";
        String prn = "";
        int nbr_sat = 0;
        Iterator<GpsSatellite> satellites = gps.getSatellites().iterator();
        for (int i = 0; i < 12; i++){
            if (satellites.hasNext()){
                GpsSatellite sat = satellites.next();
                if (sat.usedInFix()){
                    prn = prn + sat.getPrn();
                    nbr_sat++;
                }
            }

            prn = prn + ",";
        }

        if (nbr_sat > 3)
            fix = "3";
        else if(nbr_sat > 0)
            fix = "2";

        //TODO: calculate DOP values
        return fix + "," + prn + ",,,";
    }

    public static List<String> formatGpsGsv(GpsStatus gps) {
        List<String> gsv = new ArrayList<String>();
        int nbr_sat = 0;
        for (GpsSatellite sat : gps.getSatellites())
            nbr_sat++;

        Iterator<GpsSatellite> satellites = gps.getSatellites().iterator();
        for (int i = 0; i < 3; i++) {
            if (satellites.hasNext()) {
                String g = Integer.toString(nbr_sat);
                for (int n = 0; n < 4; n++) {
                    if(satellites.hasNext()) {
                        GpsSatellite sat = satellites.next();
                        g = g + "," + sat.getPrn() + "," + sat.getElevation() +
                            "," + sat.getAzimuth() + "," + sat.getSnr();
                    }
                }
                gsv.add(g);
            }
        }
        return gsv;
    }
}
