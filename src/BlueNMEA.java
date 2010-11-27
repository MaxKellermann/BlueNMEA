/*
 * Copyright (C) 2003-2010 Max Kellermann <max@duempel.org>
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
import android.widget.ListView;
import android.widget.ArrayAdapter;
import android.view.View;
import android.os.Handler;
import android.content.Context;
import android.content.Intent;
import android.content.DialogInterface;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.util.Log;

public class BlueNMEA extends Activity
    implements RadioGroup.OnCheckedChangeListener,
               Source.StatusListener, Client.Listener {
    private static final String TAG = "BlueNMEA";

    Bridge bridge;

    /** the Bluetooth peer; null if none is connected */
    Client bluetoothClient;

    /** the name of the currently selected location provider */
    String locationProvider;

    LocationManager locationManager;

    Source source;

    RadioGroup locationProviderGroup;
    TextView providerStatus, bluetoothStatus;

    ArrayList<Client> clients = new ArrayList<Client>();
    ArrayAdapter clientListAdapter;

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

    private void addClient(Client client) {
        clients.add(client);
        clientListAdapter.add(client.toString());
        source.addListener(client);
    }

    private void removeClient(Client client) {
        source.removeListener(client);
        clients.remove(client);
        clientListAdapter.remove(client.toString());
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
        providerStatus.setText(R.string.status_unknown);
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
        source = new Source(locationManager, this);

        clientListAdapter =
            new ArrayAdapter(this, android.R.layout.simple_list_item_1);
        ListView clientList = (ListView)findViewById(R.id.clients);
        clientList.setAdapter(clientListAdapter);
    }

    /** from Activity */
    @Override protected void onActivityResult(int requestCode, int resultCode,
                                              Intent intent) {
        if (resultCode != RESULT_OK)
            return;

        String address = intent.getStringExtra(SelectDevice.KEY_ADDRESS);
        if (address == null)
            return;

        if (bluetoothClient != null) {
            removeClient(bluetoothClient);

            bluetoothClient.close();
            bluetoothClient = null;
        }

        try {
            bridge.open(address);
            bluetoothStatus.setText("connected with " + address);
        } catch (IOException e) {
            bluetoothStatus.setText("failed: " + e.getMessage());
            return;
        }

        bluetoothClient = new Peer(this, bridge, address);
        addClient(bluetoothClient);
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

        locationProvider = newLocationProvider;
        source.setLocationProvider(newLocationProvider);
    }

    /** from Source.StatusListener */
    @Override public void onStatusChanged(int status) {
        providerStatus.setText(status);
    }

    /** from Client.Listener */
    @Override public void onClientFailure(Client client, Throwable t) {
        removeClient(client);

        if (client == bluetoothClient) {
            bluetoothClient = null;
            bluetoothStatus.setText("disconnected: " + t.getMessage());
        }

        client.close();
    }
}
