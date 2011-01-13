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

import java.util.UUID;
import java.io.IOException;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

class ToothClient extends ThreadedStreamClient {
    BluetoothSocket socket;
    String address;

    public ToothClient(Listener _listener, BluetoothSocket _socket)
        throws IOException {
        super(_listener, _socket.getOutputStream());

        socket = _socket;
        address = socket.getRemoteDevice().getName() +
            " [" + socket.getRemoteDevice().getAddress() + "]";
    }

    /** from Object */
    @Override public String toString() {
        return address;
    }

    /** from Client */
    @Override public void close() {
        super.close();

        try {
            socket.close();
        } catch (IOException e) {
        }

        socket = null;
    }
}

/**
 * A server for Bluetooth.
 */
public class ToothServer extends Server
    implements Runnable {
    private static final String TAG = "BlueNMEA";

    /**
     * This "magic" UUID value marks our socket as "serial port",
     * which is recognized by Windows PDAs.
     */
    private static final UUID MY_UUID =
        UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    /**
     * This exception is thrown the Bluetooth is not available.
     */
    public static class UnavailableException extends Exception {
        public UnavailableException(String msg) {
            super(msg);
        }
    }

    Listener listener;
    BluetoothServerSocket socket;
    Thread thread;

    public ToothServer(Listener _listener)
        throws IOException, UnavailableException {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null)
            throw new UnavailableException("No Bluetooth adapter found");

        if (!adapter.isEnabled())
            throw new UnavailableException("Bluetooth is disabled");

        listener = _listener;
        socket = adapter.listenUsingRfcommWithServiceRecord("BlueNMEA",
                                                            MY_UUID);
        thread = new Thread(this);
        thread.start();
    }

    @Override public void close() throws IOException, InterruptedException {
        BluetoothServerSocket s = socket;
        socket = null;
        s.close();
        thread.join();
    }

    @Override public void run() {
        try {
            while (true) {
                BluetoothSocket s = socket.accept();
                listener.onNewClient(new ToothClient(listener, s));
            }
        } catch (IOException e) {
            if (socket != null)
                Log.e(TAG, e.getMessage());
        }
    }
}
