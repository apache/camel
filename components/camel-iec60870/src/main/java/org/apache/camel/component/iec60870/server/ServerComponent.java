/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.iec60870.server;

import java.net.UnknownHostException;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.component.iec60870.AbstractIecComponent;
import org.apache.camel.component.iec60870.ConnectionId;
import org.apache.camel.component.iec60870.Constants;
import org.apache.camel.component.iec60870.ObjectAddress;
import org.eclipse.neoscada.protocol.iec60870.server.data.DataModuleOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServerComponent extends AbstractIecComponent<ServerConnectionMultiplexor, ServerOptions> {

    private static final Logger LOG = LoggerFactory.getLogger(ServerComponent.class);

    public ServerComponent(final CamelContext context) {
        super(ServerOptions.class, new ServerOptions(), context, ServerEndpoint.class);
    }

    public ServerComponent() {
        super(ServerOptions.class, new ServerOptions(), ServerEndpoint.class);
    }

    @Override
    protected void applyDataModuleOptions(final ServerOptions options, final Map<String, Object> parameters) {
        if (parameters.get(Constants.PARAM_DATA_MODULE_OPTIONS) instanceof DataModuleOptions) {
            options.setDataModuleOptions((DataModuleOptions)parameters.get(Constants.PARAM_DATA_MODULE_OPTIONS));
        }
    }

    @Override
    protected ServerConnectionMultiplexor createConnection(final ConnectionId id, final ServerOptions options) {
        LOG.debug("Create new server - id: {}", id);

        try {
            return new ServerConnectionMultiplexor(new ServerInstance(id.getHost(), id.getPort(), options));
        } catch (final UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected Endpoint createEndpoint(final String uri, final ServerConnectionMultiplexor connection, final ObjectAddress address) {
        return new ServerEndpoint(uri, this, connection, address);
    }

    /**
     * Default connection options
     *
     * @param defaultConnectionOptions the new default connection options, must
     *            not be {@code null}
     */
    @Override
    public void setDefaultConnectionOptions(final ServerOptions defaultConnectionOptions) {
        super.setDefaultConnectionOptions(defaultConnectionOptions);
    }

    @Override
    public ServerOptions getDefaultConnectionOptions() {
        return super.getDefaultConnectionOptions();
    }

}
