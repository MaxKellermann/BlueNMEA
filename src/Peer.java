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

/**
 * A Bluetooth peer device.
 */
class Peer extends Client {
    /** is the Bluetooth socket connected */
    boolean connected = false;

    Bridge bridge;
    String address;

    public Peer(Listener _listener, Bridge _bridge, String _address) {
        super(_listener);
        bridge = _bridge;
        address = _address;
    }

    public String toString() {
        return address;
    }

    /** from Client */
    @Override public void close() {
        Bridge b = bridge;
        bridge = null;

        if (b != null)
            b.close();
    }

    /** from Source.NMEAListener */
    @Override public void onLine(String line) {
        try {
            bridge.send(line + "\n");
        } catch (Exception e) {
            if (bridge != null)
                failed(e);
        }
    }
}
