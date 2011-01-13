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
import android.app.ListActivity;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.AdapterView;
import android.view.View;
import android.content.Intent;

/**
 * This #Activity lets the user select a Bluetooth device to connect
 * to.
 */
public class SelectDevice extends ListActivity {
    public static final String KEY_DEVICES = "devices";
    public static final String KEY_ADDRESS = "address";

    ListView list;
    ArrayList<String> devices;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.select);

        /* obtain the device list from the */

        Bundle extras = getIntent().getExtras();
        devices = extras.getStringArrayList(KEY_DEVICES);
        list = (ListView)findViewById(android.R.id.list);

        ArrayAdapter listAdapter = new ArrayAdapter(this,
                                                    android.R.layout.simple_list_item_1,
                                                    devices);
        list.setAdapter(listAdapter);

        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override public void onItemClick(AdapterView<?> parent,
                                                  View view, int position,
                                                  long id) {
                    Bundle bundle = new Bundle();
                    bundle.putString(KEY_ADDRESS, devices.get(position));

                    Intent intent = new Intent();
                    intent.putExtras(bundle);
                    setResult(RESULT_OK, intent);
                    finish();
                }
            });
    }
}
