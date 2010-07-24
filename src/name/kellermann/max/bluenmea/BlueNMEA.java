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
import android.util.Log;

public class BlueNMEA extends Activity
    implements RadioGroup.OnCheckedChangeListener, LocationListener,
               Source.Listener {
    private static final String TAG = "BlueNMEA";

    Bridge bridge;

    /** is the Bluetooth socket connected */
    boolean connected = false;

    /** the name of the currently selected location provider */
    String locationProvider;

    LocationManager locationManager;

    Source source;

    RadioGroup locationProviderGroup;
    TextView providerStatus, bluetoothStatus;

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

        bridge = new Bridge();
        if (!bridge.loaded) {
            setContentView(R.layout.bridge_failed);
            return;
        }

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
        source = new Source(locationManager);
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
            bridge.open(address);
            bluetoothStatus.setText("connected with " + address);
        } catch (IOException e) {
            bluetoothStatus.setText("failed: " + e.getMessage());
            return;
        }

        connected = true;
        providerStatus.setText("waiting");
        locationManager.requestLocationUpdates(locationProvider,
                                               1000, 0, this);
        source.addListener(this);
    }

    /**
     * Disconnect the Bluetooth socket, disables the LocationManager
     * and clears the saved location.
     */
    private void disconnect() {
        bridge.close();
        bluetoothStatus.setText("not connected");
        connected = false;

        locationManager.removeUpdates(this);

        providerStatus.setText("unknown");

        source.removeListener(this);
    }

    private void onConnectButtonClicked() {
        String[] devices;

        try {
            devices = bridge.scan();
        } catch (NoBluetoothException e) {
            new AlertDialog.Builder(this)
                .setTitle(R.string.app_name)
                .setMessage(R.string.no_bluetooth)
                .setPositiveButton("OK", null)
                .show();
            return;
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

        if (connected)
            locationManager.removeUpdates(this);

        locationProvider = newLocationProvider;
        source.setLocationProvider(newLocationProvider);

        if (connected) {
            providerStatus.setText("waiting");
            locationManager.requestLocationUpdates(locationProvider,
                                                   1000, 0, this);
        }
    }

    /** from Source.Listener */
    @Override public void onLine(String line) {
        try {
            bridge.send(line + "\n");
        } catch (IOException e) {
            disconnect();

            bluetoothStatus.setText("disconnected: " + e.getMessage());
        }
    }

    /** from LocationManager */
    @Override public void onLocationChanged(Location newLocation) {
        Log.d(TAG, "onLocationChanged " + newLocation);

        providerStatus.setText("ok");
    }

    /** from LocationManager */
    @Override public void onProviderDisabled(String provider) {
        providerStatus.setText("disabled");
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
            break;

        case LocationProvider.AVAILABLE:
            providerStatus.setText("ok");
            break;
        }
    }
}
