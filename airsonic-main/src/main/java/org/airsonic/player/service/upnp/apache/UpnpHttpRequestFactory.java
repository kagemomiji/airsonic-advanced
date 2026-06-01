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

import org.apache.http.HttpRequest;
import org.apache.http.MethodNotSupportedException;
import org.apache.http.RequestLine;
import org.apache.http.impl.DefaultHttpRequestFactory;
import org.apache.http.message.BasicHttpEntityEnclosingRequest;
import org.apache.http.message.BasicHttpRequest;

/**
 * This is how you add new HTTP methods to Apache HTTP Components.
 * <p>
 * Yes, Oleg dude, make stuff private! Good idea!
 *</p>
 * @author Christian Bauer
 */
public class UpnpHttpRequestFactory extends DefaultHttpRequestFactory {

    private static final String[] BASIC = {
        "SUBSCRIBE",
        "UNSUBSCRIBE"
    };

    private static final String[] WITH_ENTITY = {
        "NOTIFY"
    };

    public UpnpHttpRequestFactory() {
        super();
    }

    private static boolean isOneOf(final String[] methods, final String method) {
        for (int i = 0; i < methods.length; i++) {
            if (methods[i].equalsIgnoreCase(method)) {
                return true;
            }
        }
        return false;
    }

    public HttpRequest newHttpRequest(final RequestLine requestline)
            throws MethodNotSupportedException {
        if (requestline == null) {
            throw new IllegalArgumentException("Request line may not be null");
        }
        String method = requestline.getMethod();
        String uri = requestline.getUri();
        return newHttpRequest(method, uri);
    }

    public HttpRequest newHttpRequest(final String method, final String uri) throws MethodNotSupportedException {
        if (isOneOf(BASIC, method)) {
            return new BasicHttpRequest(method, uri);
        } else if (isOneOf(WITH_ENTITY, method)) {
            return new BasicHttpEntityEnclosingRequest(method, uri);
        } else {
            return super.newHttpRequest(method, uri);
        }
    }

}
