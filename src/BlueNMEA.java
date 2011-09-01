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
import java.util.List;
import java.io.IOException;

import android.app.Activity;
import android.app.Dialog;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.widget.TextView;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.ListView;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuInflater;
import android.content.Context;
import android.content.Intent;
import android.content.DialogInterface;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.util.Log;

class ScanThread extends Thread {
    public static final int SUCCESS = 1;
    public static final int ERROR = 2;
    public static final int EXCEPTION = 3;

    Bridge bridge;
    Handler handler;

    public ScanThread(Bridge _bridge, Handler _handler) {
        bridge = _bridge;
        handler = _handler;
    }

    @Override public void run() {
        Message msg;

        try {
            String[] devices;
            devices = bridge.scan();

            if (devices.length > 0)
                msg = handler.obtainMessage(SUCCESS, devices);
            else
                msg = handler.obtainMessage(ERROR, R.string.no_devices, 0);
        } catch (NoBluetoothException e) {
            msg = handler.obtainMessage(ERROR, R.string.no_bluetooth, 0);
        } catch (IOException e) {
            msg = handler.obtainMessage(EXCEPTION, e);
        }

        handler.sendMessage(msg);
    }
}

public class BlueNMEA extends Activity
    implements RadioGroup.OnCheckedChangeListener,
               Source.StatusListener, Client.Listener,
               Server.Listener {
    private static final String TAG = "BlueNMEA";

    static final int SCANNING_DIALOG = 0;

    Bridge bridge;

    /** the Bluetooth peer; null if none is connected */
    Client bluetoothClient;

    Server tcp, bluetoothServer;

    /** the name of the currently selected location provider */
    String locationProvider;

    LocationManager locationManager;

    Source source;

    RadioGroup locationProviderGroup;
    TextView providerStatus, bluetoothClientStatus, bluetoothServerStatus, tcpStatus;

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

        setContentView(R.layout.main);

        providerStatus = (TextView)findViewById(R.id.providerStatus);
        providerStatus.setText(R.string.status_unknown);
        bluetoothClientStatus = (TextView)findViewById(R.id.bluetoothClientStatus);
        bluetoothClientStatus.setText("not connected");
        bluetoothServerStatus = (TextView)findViewById(R.id.bluetoothServerStatus);
        bluetoothServerStatus.setText("not initialized");
        tcpStatus = (TextView)findViewById(R.id.tcpStatus);

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

        clientList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override public void onItemClick(AdapterView<?> parent,
                                                  View view, int position,
                                                  long id) {
                    final Client client = clients.get(position);

                    AlertDialog.Builder builder = new AlertDialog.Builder(BlueNMEA.this);
                    builder.setMessage("Do you want to disconnect the client " + client + "?")
                        .setCancelable(false)
                        .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog,
                                                    int id) {
                                    if (!clients.contains(client))
                                        return;

                                    removeClient(client);
                                    client.close();
                                }
                            })
                        .setNegativeButton("No", new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog,
                                                    int id) {
                                }
                            });
                    builder.create().show();
                }
            });

        bridge = new Bridge();
        if (!bridge.loaded || !bridge.available()) {
            bridge = null;
            bluetoothClientStatus.setText("not available");
            button.setVisibility(View.GONE);
        }

        try {
            int port = 4352;
            tcp = new TCPServer(this, port);
            tcpStatus.setText("listening on port " + port);
        } catch (IOException e) {
            tcpStatus.setText("failed: " + e.getMessage());
        }

        try {
            bluetoothServer = new ToothServer(this);
            bluetoothServerStatus.setText("listening");
        } catch (Exception e) {
            bluetoothServerStatus.setText("failed: " + e.getMessage());
        } catch (VerifyError e) {
            bluetoothServerStatus.setText("not available");
        }
    }

    /** from Activity */
    @Override public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main, menu);
        return true;
    }

    /** from Activity */
    @Override public boolean onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.disconnect_all).setEnabled(!clients.isEmpty());
        return true;
    }

    /** from Activity */
    @Override public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.disconnect_all:
            ArrayList<Client> copy = new ArrayList<Client>(clients);
            for (Client client : copy) {
                removeClient(client);
                client.close();
            }

            return true;

        default:
            return super.onOptionsItemSelected(item);
        }
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
            bluetoothClientStatus.setText("connected with " + address);
        } catch (IOException e) {
            bluetoothClientStatus.setText("failed: " + e.getMessage());
            return;
        }

        bluetoothClient = new Peer(this, bridge, address);
        addClient(bluetoothClient);
    }

    protected void scanFinished(String[] devices) {
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

    class ScanHandler extends Handler {
        public void handleMessage(Message msg) {
            try {
                dismissDialog(SCANNING_DIALOG);
            } catch (IllegalArgumentException e) {
                /* this exception was reported on the Android Market,
                   however I cannot imagine how this could ever
                   happen */
            }

            switch (msg.what) {
            case ScanThread.SUCCESS:
                scanFinished((String[])msg.obj);
                break;

            case ScanThread.ERROR:
                new AlertDialog.Builder(BlueNMEA.this)
                    .setTitle(R.string.app_name)
                    .setMessage(msg.arg1)
                    .setPositiveButton("OK", null)
                    .show();
                return;

            case ScanThread.EXCEPTION:
                ExceptionAlert((Throwable)msg.obj, "Scan failed");
                break;
            }
        }
    }

    final Handler scanHandler = new ScanHandler();

    class ClientHandler extends Handler {
        public static final int REMOVE = 1;
        public static final int ADD = 2;

        public void handleMessage(Message msg) {
            Client client = (Client)msg.obj;

            switch (msg.what) {
            case ADD:
                addClient(client);
                break;

            case REMOVE:
                if (!clients.contains(client))
                    return;

                removeClient(client);

                if (client == bluetoothClient) {
                    bluetoothClient = null;
                    bluetoothClientStatus.setText("disconnected: " +
                                                  msg.getData().getString("error"));
                }

                break;
            }
        }
    }

    final Handler clientHandler = new ClientHandler();

    /** from Activity */
    protected Dialog onCreateDialog(int id) {
        switch (id) {
        case SCANNING_DIALOG:
            ProgressDialog progressDialog = new ProgressDialog(this);
            progressDialog.setMessage(getString(R.string.scanning));
            return progressDialog;

        default:
            return null;
        }
    }

    private void onConnectButtonClicked() {
        showDialog(SCANNING_DIALOG);
        new ScanThread(bridge, scanHandler).start();
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
        Message msg = clientHandler.obtainMessage(ClientHandler.REMOVE,
                                                  client);
        Bundle b = new Bundle();
        b.putString("error", t.getMessage());
        msg.setData(b);

        clientHandler.sendMessage(msg);

        client.close();
    }

    /** from Server.Listener */
    @Override public void onNewClient(Client client) {
        Message msg = clientHandler.obtainMessage(ClientHandler.ADD,
                                                  client);
        clientHandler.sendMessage(msg);
    }
}
