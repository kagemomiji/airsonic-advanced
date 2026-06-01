/*
  This file is part of Airsonic.

  Airsonic is free software: you can redistribute it and/or modify
  it under the terms of the GNU General Public License as published by
  the Free Software Foundation, either version 3 of the License, or
  (at your option) any later version.

  Airsonic is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.

  You should have received a copy of the GNU General Public License
  along with Airsonic.  If not, see <http://www.gnu.org/licenses/>.

  Forked from https://github.com/4thline/cling version 2.0.1, then
  modified to run with jupnp.

   Copyright (C) 2013 4th Line GmbH, Switzerland
 */
package org.airsonic.player.service.upnp.apache;

import org.apache.http.Header;
import org.apache.http.HttpMessage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts from/to Apache HTTP Components header format.
 *
 * @author Christian Bauer
 */
public class HeaderUtil {

    public static void add(HttpMessage httpMessage, Map<String, List<String>> headers) {
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            for (String value : entry.getValue()) {
                httpMessage.addHeader(entry.getKey(), value);
            }
        }
    }

    public static Map<String, List<String>> get(HttpMessage httpMessage) {
        Map<String, List<String>> headers = new HashMap<>();
        for (Header header : httpMessage.getAllHeaders()) {
            if (! headers.containsKey(header.getName())) {
                List<String> headerList = new ArrayList<>();
                headers.put(header.getName(), headerList);
            }
            headers.get(header.getName()).add(header.getValue());
        }
        return headers;
    }

}
