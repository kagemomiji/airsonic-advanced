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

import org.jupnp.transport.spi.AbstractStreamClientConfiguration;

import java.util.concurrent.ExecutorService;

/**
 * Settings for the Apache HTTP Components implementation.
 *
 * @author Christian Bauer
 */
public class StreamClientConfigurationImpl extends AbstractStreamClientConfiguration {

    protected int maxTotalConnections = 1024;
    protected int maxTotalPerRoute = 100;
    protected String contentCharset = "UTF-8"; // UDA spec says it's always UTF-8 entity content

    public StreamClientConfigurationImpl(ExecutorService timeoutExecutorService) {
        super(timeoutExecutorService);
    }

    public StreamClientConfigurationImpl(ExecutorService timeoutExecutorService, int timeoutSeconds) {
        super(timeoutExecutorService, timeoutSeconds);
    }

    /**
     * Defaults to 1024.
     */
    public int getMaxTotalConnections() {
        return maxTotalConnections;
    }

    public void setMaxTotalConnections(int maxTotalConnections) {
        this.maxTotalConnections = maxTotalConnections;
    }

    /**
     * Defaults to 100.
     */
    public int getMaxTotalPerRoute() {
        return maxTotalPerRoute;
    }

    public void setMaxTotalPerRoute(int maxTotalPerRoute) {
        this.maxTotalPerRoute = maxTotalPerRoute;
    }

    /**
     * @return Character set of textual content, defaults to "UTF-8".
     */
    public String getContentCharset() {
        return contentCharset;
    }

    public void setContentCharset(String contentCharset) {
        this.contentCharset = contentCharset;
    }

    /**
     * <p>
     * Returning <code>-1</code> will also avoid OOM on the HTC Thunderbolt where default size is 2MB (!):
     * http://stackoverflow.com/questions/5358014/android-httpclient-oom-on-4g-lte-htc-thunderbolt
     * </p>
     * @return By default <code>-1</code>, enabling HttpClient's default (8192 bytes in version 4.1)
     */
    public int getSocketBufferSize() {
        return -1;
    }

    /**
     * @return Whether we should (expensively) check for stale connections, defaults to <code>false</code>.
     */
    public boolean getStaleCheckingEnabled() {
        return false;
    }

    /**
     * @return By default <code>0</code>, use <code>-1</code> to enable HttpClient's default (3 retries in version 4.1)
     */
    public int getRequestRetryCount() {
        return 0;
    }

}
