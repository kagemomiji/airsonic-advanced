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

  Copyright 2024 (C) Y.Tory
  Copyright 2017 (C) Airsonic Authors
  Based upon Subsonic, Copyright 2009 (C) Sindre Mehus
*/
package org.airsonic.player.service.upnp;

import org.airsonic.player.service.upnp.apache.StreamClientConfigurationImpl;
import org.airsonic.player.service.upnp.apache.StreamClientImpl;
import org.airsonic.player.service.upnp.apache.StreamServerConfigurationImpl;
import org.airsonic.player.service.upnp.apache.StreamServerImpl;
import org.jupnp.DefaultUpnpServiceConfiguration;
import org.jupnp.transport.spi.NetworkAddressFactory;
import org.jupnp.transport.spi.StreamClient;
import org.jupnp.transport.spi.StreamServer;

/**
 * Note the different packages on similarly named classes from the parent
 *
 */
public class ApacheUpnpServiceConfiguration extends DefaultUpnpServiceConfiguration {
    public ApacheUpnpServiceConfiguration(int streamListenPort) {
        super(streamListenPort);
    }

    @Override
    public StreamClient<?> createStreamClient() {
        return new StreamClientImpl(new StreamClientConfigurationImpl(getSyncProtocolExecutorService()));
    }

    @Override
    public StreamServer<?> createStreamServer(NetworkAddressFactory networkAddressFactory) {
        return new StreamServerImpl(new StreamServerConfigurationImpl(networkAddressFactory.getStreamListenPort()));
    }
}