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

public class Bridge {
    public static boolean loaded = false;

    static {
        try {
            System.loadLibrary("bluebridge");
            loaded = true;
        } catch (UnsatisfiedLinkError e) {
        }
    }

    /**
     * Are Bluetooth sockets supported?
     */
    public native boolean available();

    public native String[] scan() throws IOException;
    public native void open(String address) throws IOException;
    public native void listen() throws IOException;
    public native String accept() throws IOException;
    public native void close();
    public native void send(String line) throws IOException;
}
