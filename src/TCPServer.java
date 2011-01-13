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

import java.io.IOException;
import java.net.Socket;
import java.net.ServerSocket;
import android.util.Log;

class TCPClient extends ThreadedStreamClient {
    Socket socket;
    String address;

    public TCPClient(Listener _listener, Socket _socket) throws IOException {
        super(_listener, _socket.getOutputStream());

        socket = _socket;
        address = socket.getInetAddress().getHostAddress() +
            ":" + socket.getPort();
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

public class TCPServer extends Server
    implements Runnable {
    private static final String TAG = "BlueNMEA";

    Listener listener;
    ServerSocket socket;
    Thread thread;

    public TCPServer(Listener _listener, int port) throws IOException {
        listener = _listener;
        socket = new ServerSocket(port);
        thread = new Thread(this);
        thread.start();
    }

    @Override public void close() throws IOException, InterruptedException {
        ServerSocket s = socket;
        socket = null;
        s.close();
        thread.join();
    }

    @Override public void run() {
        try {
            while (true) {
                Socket s = socket.accept();
                s.shutdownInput();
                listener.onNewClient(new TCPClient(listener, s));
            }
        } catch (IOException e) {
            if (socket != null)
                Log.e(TAG, e.getMessage());
        }
    }
}
