/*
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
package org.apache.camel.component.iec60870.client;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.component.iec60870.AbstractIecComponent;
import org.apache.camel.component.iec60870.ConnectionId;
import org.apache.camel.component.iec60870.Constants;
import org.apache.camel.component.iec60870.ObjectAddress;
import org.apache.camel.spi.annotations.Component;
import org.eclipse.neoscada.protocol.iec60870.client.data.DataModuleOptions;

@Component("iec60870-client")
public class ClientComponent extends AbstractIecComponent<ClientConnectionMultiplexor, ClientOptions> {

    public ClientComponent(final CamelContext context) {
        super(ClientOptions.class, new ClientOptions(), context);
    }

    public ClientComponent() {
        super(ClientOptions.class, new ClientOptions());
    }

    @Override
    protected void applyDataModuleOptions(final ClientOptions options, final Map<String, Object> parameters) {
        if (parameters.get(Constants.PARAM_DATA_MODULE_OPTIONS) instanceof DataModuleOptions) {
            options.setDataModuleOptions((DataModuleOptions)parameters.get(Constants.PARAM_DATA_MODULE_OPTIONS));
        }
    }

    @Override
    protected Endpoint createEndpoint(final String uri, final ClientConnectionMultiplexor connection, final ObjectAddress address) {
        return new ClientEndpoint(uri, this, connection, address);
    }

    @Override
    protected ClientConnectionMultiplexor createConnection(final ConnectionId id, final ClientOptions options) {
        return new ClientConnectionMultiplexor(new ClientConnection(id.getHost(), id.getPort(), options));
    }

    /**
     * Default connection options
     *
     * @param defaultConnectionOptions the new default connection options, must
     *            not be {@code null}
     */
    @Override
    public void setDefaultConnectionOptions(final ClientOptions defaultConnectionOptions) {
        super.setDefaultConnectionOptions(defaultConnectionOptions);
    }

    @Override
    public ClientOptions getDefaultConnectionOptions() {
        return super.getDefaultConnectionOptions();
    }

}
