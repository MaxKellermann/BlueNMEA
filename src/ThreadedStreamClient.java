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

import java.util.LinkedList;
import java.io.IOException;
import java.io.OutputStream;
import android.util.Log;

class ThreadedStreamClient extends Client implements Runnable {
    private static final String TAG = "BlueNMEA";

    OutputStream os;
    LinkedList<String> queue = new LinkedList<String>();
    Thread thread = new Thread(this);

    public ThreadedStreamClient(Listener _listener, OutputStream _stream) {
        super(_listener);

        os = _stream;

        thread.start();
    }

    /** from Client */
    @Override public void close() {
        synchronized(this) {
            try {
                os.close();
            } catch (IOException e) {
            }

            os = null;

            notify();
        }

        try {
            thread.join();
        } catch (InterruptedException e) {
        }
    }

    /** from Source.NMEAListener */
    @Override public void onLine(String line) {
        synchronized(this) {
            /* ensure the queue doesn't grow too large */
            while (queue.size() > 16)
                queue.removeFirst();

            queue.add(line);

            /* wake up the thread */
            notify();
        }
    }

    /** from Runnable */
    @Override public void run() {
        try {
            while (os != null) {
                String line;
                synchronized(this) {
                    wait();

                    if (os == null)
                        break;

                    line = queue.removeFirst();
                }

                if (line != null) {
                    line += "\n";
                    os.write(line.getBytes());
                }
            }
        } catch (InterruptedException e) {
            Log.e(TAG, e.getMessage());
        } catch (IOException e) {
            if (os != null)
                failed(e);
        }
    }
}
