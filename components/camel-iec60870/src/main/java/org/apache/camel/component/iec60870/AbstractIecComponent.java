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
package org.apache.camel.component.iec60870;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.component.iec60870.client.ClientOptions;
import org.apache.camel.spi.Metadata;
import org.apache.camel.support.DefaultComponent;
import org.eclipse.neoscada.protocol.iec60870.ProtocolOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Objects.requireNonNull;

public abstract class AbstractIecComponent<T1, T2 extends BaseOptions<T2>> extends DefaultComponent {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractIecComponent.class);

    private final Map<ConnectionId, T1> connections = new HashMap<>();

    private final Class<T2> connectionOptionsClazz;

    private T2 defaultConnectionOptions;

    protected AbstractIecComponent(final Class<T2> connectionOptionsClazz, final T2 defaultConnectionOptions) {
        this.connectionOptionsClazz = connectionOptionsClazz;
        this.defaultConnectionOptions = defaultConnectionOptions;
    }

    protected AbstractIecComponent(final Class<T2> connectionOptionsClazz, final T2 defaultConnectionOptions,
                                   final CamelContext context) {
        super(context);
        this.connectionOptionsClazz = connectionOptionsClazz;
        this.defaultConnectionOptions = defaultConnectionOptions;
    }

    protected abstract T1 createConnection(ConnectionId id, T2 options);

    /**
     * Default connection options
     */
    @Metadata
    protected void setDefaultConnectionOptions(final T2 defaultConnectionOptions) {
        this.defaultConnectionOptions = requireNonNull(defaultConnectionOptions);
    }

    protected T2 getDefaultConnectionOptions() {
        return this.defaultConnectionOptions;
    }

    @Override
    protected Endpoint createEndpoint(final String uri, final String remaining, final Map<String, Object> parameters)
            throws Exception {
        LOG.debug("Create endpoint - uri: {}, remaining: {}, parameters: {}", uri, remaining, parameters);

        final T1 connection = lookupConnection(uri, parameters);
        final ObjectAddress address = parseAddress(uri);

        return createEndpoint(uri, connection, address);
    }

    protected abstract Endpoint createEndpoint(String uri, T1 connection, ObjectAddress address);

    protected T2 parseOptions(final Map<String, Object> parameters) throws Exception {

        // test for provided connection options

        final Object connectionOptions = parameters.get(Constants.PARAM_CONNECTION_OPTIONS);
        if (connectionOptions != null) {
            try {
                return this.connectionOptionsClazz.cast(connectionOptions);
            } catch (final ClassCastException e) {
                throw new IllegalArgumentException(
                        String.format("'%s' must by of type %s", Constants.PARAM_CONNECTION_OPTIONS,
                                ClientOptions.class.getName()),
                        e);
            }
        }

        // construct new default set

        final T2 options = this.defaultConnectionOptions.copy();

        // apply protocolOptions

        if (parameters.get(Constants.PARAM_PROTOCOL_OPTIONS) instanceof ProtocolOptions) {
            options.setProtocolOptions((ProtocolOptions) parameters.get(Constants.PARAM_PROTOCOL_OPTIONS));
        }

        // apply dataModuleOptions

        applyDataModuleOptions(options, parameters);

        // apply parameters to connection options

        setProperties(options, parameters);

        // return result

        return options;
    }

    protected abstract void applyDataModuleOptions(T2 options, Map<String, Object> parameters);

    private T1 lookupConnection(final String fullUri, final Map<String, Object> parameters) throws Exception {

        LOG.debug("parse connection - '{}'", fullUri);

        if (fullUri == null || fullUri.isEmpty()) {
            throw new IllegalArgumentException("Invalid URI: " + fullUri);
        }

        final ConnectionId id = parseConnectionId(fullUri, parameters);

        LOG.debug("parse connection - fullUri: {} -> {}", fullUri, id);

        synchronized (this) {
            LOG.debug("Locating connection - {}", id);

            T1 connection = this.connections.get(id);

            LOG.debug("Result - {} -> {}", id, connection);

            if (connection == null) {
                final T2 options = parseOptions(parameters);
                LOG.debug("Creating new connection: {}", options);

                connection = createConnection(id, options);
                this.connections.put(id, connection);
            }
            return connection;
        }
    }

    private static ConnectionId parseConnectionId(final String fullUri, final Map<String, Object> parameters) {
        final URI uri = URI.create(fullUri);

        final Object connectionId = parameters.get("connectionId");

        return new ConnectionId(uri.getHost(), uri.getPort(), connectionId instanceof String ? (String) connectionId : null);
    }

    private static ObjectAddress parseAddress(final String fullUri) {
        final URI uri = URI.create(fullUri);

        String path = uri.getPath();
        path = path.replaceAll("^\\/+", "");

        return ObjectAddress.valueOf(path);
    }

}
