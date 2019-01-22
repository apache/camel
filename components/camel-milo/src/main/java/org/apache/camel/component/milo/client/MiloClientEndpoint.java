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
package org.apache.camel.component.milo.client;

import java.util.Objects;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.eclipse.milo.opcua.stack.core.types.builtin.ExpandedNodeId;

/**
 * Connect to OPC UA servers using the binary protocol for acquiring telemetry
 * data
 */
@UriEndpoint(firstVersion = "2.19.0", scheme = "milo-client", syntax = "milo-client:endpointUri", title = "OPC UA Client", label = "iot")
public class MiloClientEndpoint extends DefaultEndpoint {

    /**
     * The OPC UA server endpoint
     */
    @UriPath
    @Metadata(required = true)
    private final String endpointUri;

    /**
     * The node definition (see Node ID)
     */
    @UriParam
    private ExpandedNodeId node;

    /**
     * The method definition (see Method ID)
     */
    @UriParam
    private ExpandedNodeId method;

    /**
     * The sampling interval in milliseconds
     */
    @UriParam
    private Double samplingInterval;

    /**
     * The client configuration
     */
    @UriParam
    private MiloClientConfiguration client;

    /**
     * Default "await" setting for writes
     */
    @UriParam
    private boolean defaultAwaitWrites;

    private final MiloClientConnection connection;
    private final MiloClientComponent component;

    public MiloClientEndpoint(final String uri, final MiloClientComponent component, final MiloClientConnection connection, final String endpointUri) {
        super(uri, component);

        Objects.requireNonNull(component);
        Objects.requireNonNull(connection);
        Objects.requireNonNull(endpointUri);

        this.endpointUri = endpointUri;

        this.component = component;
        this.connection = connection;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        this.component.disposed(this);
        super.doStop();
    }

    @Override
    public Producer createProducer() throws Exception {
        return new MiloClientProducer(this, this.connection, this.defaultAwaitWrites);
    }

    @Override
    public Consumer createConsumer(final Processor processor) throws Exception {
        return new MiloClientConsumer(this, processor, this.connection);
    }

    @Override
    public boolean isSingleton() {
        return false;
    }

    public MiloClientConnection getConnection() {
        return this.connection;
    }

    // item configuration

    public void setMethod(final String method) {
        if (method == null) {
            this.method = null;
        } else {
            this.method = ExpandedNodeId.parse(method);
        }
    }

    public String getMethod() {
        if (this.method != null) {
            return this.method.toParseableString();
        } else {
            return null;
        }
    }

    public void setNode(final String node) {
        if (node == null) {
            this.node = null;
        } else {
            this.node = ExpandedNodeId.parse(node);
        }
    }

    public String getNode() {
        if (this.node != null) {
            return this.node.toParseableString();
        } else {
            return null;
        }
    }

    ExpandedNodeId getNodeId() {
        return this.node;
    }

    ExpandedNodeId getMethodId() {
        return this.method;
    }

    public Double getSamplingInterval() {
        return this.samplingInterval;
    }

    public void setSamplingInterval(final Double samplingInterval) {
        this.samplingInterval = samplingInterval;
    }

    public boolean isDefaultAwaitWrites() {
        return this.defaultAwaitWrites;
    }

    public void setDefaultAwaitWrites(final boolean defaultAwaitWrites) {
        this.defaultAwaitWrites = defaultAwaitWrites;
    }
}
