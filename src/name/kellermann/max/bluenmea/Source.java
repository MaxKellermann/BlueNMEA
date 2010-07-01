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

import java.util.LinkedList;
import java.util.List;
import java.io.IOException;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Button;
import android.widget.RadioGroup;
import android.view.View;
import android.os.Handler;
import android.content.Context;
import android.content.Intent;
import android.content.DialogInterface;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.location.GpsStatus.Listener;
import android.util.Log;

/**
 * This class collects data, generates NMEA sentences and submits them
 * to its listeners.
 */
public class Source
    implements LocationListener, Runnable, Listener {
    private static final String TAG = "BlueNMEA";

    /** this timer is used for sending regular updates over the
        socket, even when onLocationChanged() is not called */
    private Handler timer = new Handler();

    /** the name of the currently selected location provider */
    String locationProvider = LocationManager.GPS_PROVIDER;

    LocationManager locationManager;

    /** the most recent known location, or null */
    Location location;

    public interface Listener {
        void onLine(String line);
    }

    List<Listener> listeners = new LinkedList<Listener>();

    Source(LocationManager _locationManager) {
        locationManager = _locationManager;
    }

    protected void enable() {
        locationManager.requestLocationUpdates(locationProvider,
                                               1000, 0, this);

        if (locationProvider.equals(LocationManager.GPS_PROVIDER))
            locationManager.addGpsStatusListener(this);
    }

    /**
     * Clears the saved location and disables the timer.
     */
    private void clearLocation() {
        if (location != null) {
            timer.removeCallbacks(this);
            location = null;
        }
    }

    protected void disable() {
        clearLocation();

        locationManager.removeUpdates(this);

        if (locationProvider.equals(LocationManager.GPS_PROVIDER))
            locationManager.removeGpsStatusListener(this);
    }

    public void setLocationProvider(String _locationProvider) {
        if (!listeners.isEmpty())
            disable();

        locationProvider = _locationProvider;

        if (!listeners.isEmpty())
            enable();
    }

    public void addListener(Listener l) {
        if (listeners.isEmpty())
            enable();

        listeners.add(l);
    }

    public void removeListener(Listener l) {
        listeners.remove(l);

        if (listeners.isEmpty())
            disable();
    }

    protected void broadcastLine(String line) {
        for (Listener l: listeners)
            l.onLine(line);
    }

    private void sendWithChecksum(String line) {
        line = NMEA.decorate(line);
        broadcastLine(line);
        Log.d(TAG, "SEND '" + line + "'");
    }

    private void sendLocation() {
        String time = NMEA.formatTime(location);
        String date = NMEA.formatDate(location);
        String position = NMEA.formatPosition(location);

        sendWithChecksum("GPGGA," + time + "," +
                         position + ",1," +
                         NMEA.formatSatellites(location) + "," +
                         location.getAccuracy() + "," +
                         NMEA.formatAltitude(location) + ",,,,");
        sendWithChecksum("GPGLL," + position + "," + time + ",A");
        sendWithChecksum("GPRMC," + time + ",A," +
                         position + "," +
                         NMEA.formatSpeedKt(location) + "," +
                         NMEA.formatBearing(location) + "," +
                         date + ",,");
    }

    private void sendSatellite(GpsStatus gps) {
        String gsa = NMEA.formatGpsGsa(gps);
        sendWithChecksum("GPGSA,A," + gsa);

        List<String> gsvs = NMEA.formatGpsGsv(gps);
        for(String gsv : gsvs)
            sendWithChecksum("GPGSV," + gsvs.size() + "," +
                             Integer.toString(gsvs.indexOf(gsv)+1) + "," + gsv);
    }

    /** from LocationManager */
    @Override public void onLocationChanged(Location newLocation) {
        Log.d(TAG, "onLocationChanged " + newLocation);

        if (location != null)
            /* reset the timer */
            timer.removeCallbacks(this);

        location = newLocation;

        /* requeue the timer with a fresh duration */
        timer.postDelayed(this, 5000);

        sendLocation();
    }

    /** from GpsStatus.Listener */
    @Override public void onGpsStatusChanged(int event) {
        if (event == GpsStatus.GPS_EVENT_SATELLITE_STATUS)
            sendSatellite(locationManager.getGpsStatus(null));
    }

    /** from LocationManager */
    @Override public void onProviderDisabled(String provider) {
        clearLocation();
    }

    /** from LocationManager */
    @Override public void onProviderEnabled(String provider) {
    }

    /** from LocationManager */
    @Override public void onStatusChanged(String provider, int status,
                                          Bundle extras) {
        switch (status) {
        case LocationProvider.OUT_OF_SERVICE:
        case LocationProvider.TEMPORARILY_UNAVAILABLE:
            clearLocation();
            break;

        case LocationProvider.AVAILABLE:
            break;
        }
    }

    /** from Runnable */
    @Override public void run() {
        /* requeue the timer with a fresh duration */
        timer.postDelayed(this, 5000);

        sendLocation();
    }
}
