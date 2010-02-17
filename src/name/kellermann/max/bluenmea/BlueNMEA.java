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

import java.util.ArrayList;
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

public class BlueNMEA extends Activity
    implements RadioGroup.OnCheckedChangeListener, LocationListener, Runnable, Listener {
    private static final String TAG = "BlueNMEA";

    static {
        System.loadLibrary("bluebridge");
    }

    /** this timer is used for sending regular updates over the
        socket, even when onLocationChanged() is not called */
    private Handler timer = new Handler();

    /** is the Bluetooth socket connected */
    boolean connected = false;

    /** the name of the currently selected location provider */
    String locationProvider;

    LocationManager locationManager;

    RadioGroup locationProviderGroup;
    TextView providerStatus, bluetoothStatus;

    /** the most recent known location, or null */
    Location location;

    private void ExceptionAlert(Throwable exception, String title) {
        AlertDialog dialog = new AlertDialog.Builder(this).create();
        dialog.setTitle(title);
        dialog.setMessage(exception.getMessage());
        dialog.setButton("OK", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    dialog.cancel();
                }
            });
        dialog.show();
    }

    /** from Activity */
    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.main);

        providerStatus = (TextView)findViewById(R.id.providerStatus);
        providerStatus.setText("unknown");
        bluetoothStatus = (TextView)findViewById(R.id.bluetoothStatus);
        bluetoothStatus.setText("not connected");

        locationProviderGroup = (RadioGroup)findViewById(R.id.provider);
        locationProviderGroup.setOnCheckedChangeListener(this);

        Button button;

        button = (Button) findViewById(R.id.connectButton);
        button.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    onConnectButtonClicked();
                }
            });

        locationProvider = LocationManager.GPS_PROVIDER;
        locationManager = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
    }

    /** from Activity */
    @Override protected void onActivityResult(int requestCode, int resultCode,
                                              Intent intent) {
        if (resultCode != RESULT_OK)
            return;

        String address = intent.getStringExtra(SelectDevice.KEY_ADDRESS);
        if (address == null)
            return;

        disconnect();

        try {
            open(address);
            bluetoothStatus.setText("connected with " + address);
        } catch (IOException e) {
            bluetoothStatus.setText("failed: " + e.getMessage());
            return;
        }

        connected = true;
        providerStatus.setText("waiting");
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

    /**
     * Disconnect the Bluetooth socket, disables the LocationManager
     * and clears the saved location.
     */
    private void disconnect() {
        close();
        bluetoothStatus.setText("not connected");
        connected = false;

        locationManager.removeUpdates(this);

        if (locationProvider.equals(LocationManager.GPS_PROVIDER))
            locationManager.removeGpsStatusListener(this);

        providerStatus.setText("unknown");
        clearLocation();
    }

    private void onConnectButtonClicked() {
        String[] devices;

        try {
            devices = scan();
        } catch (IOException e) {
            Log.e(TAG, e.getMessage());
            ExceptionAlert(e, "Scan failed");
            return;
        }

        ArrayList<String> deviceArray =
            new ArrayList<String>(devices.length);
        for (String device: devices)
            deviceArray.add(device);

        Bundle bundle = new Bundle();
        bundle.putStringArrayList(SelectDevice.KEY_DEVICES,
                                  deviceArray);

        Intent intent = new Intent(BlueNMEA.this,
                                   SelectDevice.class);
        intent.putExtras(bundle);
        startActivityForResult(intent, 0);
    }

    /** from RadioGroup.OnCheckedChangeListener */
    @Override public void onCheckedChanged(RadioGroup group, int checkedId) {
        String newLocationProvider;

        if (checkedId == R.id.gpsProvider)
            newLocationProvider = LocationManager.GPS_PROVIDER;
        else if (checkedId == R.id.networkProvider)
            newLocationProvider = LocationManager.NETWORK_PROVIDER;
        else
            return;

        if (newLocationProvider.equals(locationProvider))
            return;

        if (connected) {
            locationManager.removeUpdates(this);

            if (locationProvider.equals(LocationManager.GPS_PROVIDER))
                locationManager.removeGpsStatusListener(this);
        }

        locationProvider = newLocationProvider;
        clearLocation();

        if (connected) {
            providerStatus.setText("waiting");
            locationManager.requestLocationUpdates(locationProvider,
                                                   1000, 0, this);

            if (locationProvider.equals(LocationManager.GPS_PROVIDER))
                locationManager.addGpsStatusListener(this);
        }
    }

    private void sendWithChecksum(String line) throws IOException {
        line = NMEA.decorate(line);
        send(line + "\n");
        Log.d(TAG, "SEND '" + line + "'");
    }

    private void sendLocation() throws IOException {
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

    private void sendSatellite(GpsStatus gps) throws IOException {
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

        providerStatus.setText("ok");

        if (location != null)
            /* reset the timer */
            timer.removeCallbacks(this);

        location = newLocation;

        try {
            sendLocation();

            /* requeue the timer with a fresh duration */
            timer.postDelayed(this, 5000);
        } catch (IOException e) {
            disconnect();

            bluetoothStatus.setText("disconnected: " + e.getMessage());
        }
    }

    public void onGpsStatusChanged(int event) {
        if (event == GpsStatus.GPS_EVENT_SATELLITE_STATUS) {
            GpsStatus gps = locationManager.getGpsStatus(null);
            try {
                sendSatellite(gps);
            } catch (IOException e) {
                Log.e(TAG, "onGpsStatusChanged " + e.getMessage());
            }
        }
    }

    /** from LocationManager */
    @Override public void onProviderDisabled(String provider) {
        providerStatus.setText("disabled");

        clearLocation();
    }

    /** from LocationManager */
    @Override public void onProviderEnabled(String provider) {
        providerStatus.setText("enabled");
    }

    /** from LocationManager */
    @Override public void onStatusChanged(String provider, int status,
                                          Bundle extras) {
        switch (status) {
        case LocationProvider.OUT_OF_SERVICE:
        case LocationProvider.TEMPORARILY_UNAVAILABLE:
            providerStatus.setText("unavailable");
            clearLocation();
            break;

        case LocationProvider.AVAILABLE:
            providerStatus.setText("ok");
            break;
        }
    }

    /** from Runnable */
    @Override public void run() {
        try {
            sendLocation();

            /* requeue the timer with a fresh duration */
            timer.postDelayed(this, 5000);
        } catch (IOException e) {
            disconnect();

            bluetoothStatus.setText("disconnected: " + e.getMessage());
        }
    }

    public native String[] scan() throws IOException;
    public native void open(String address) throws IOException;
    public native void listen() throws IOException;
    public native String accept() throws IOException;
    public native void close();
    public native void send(String line) throws IOException;
}
