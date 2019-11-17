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
package org.apache.camel.component.iec60870.server;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.iec60870.AbstractIecEndpoint;
import org.apache.camel.component.iec60870.ObjectAddress;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.DefaultComponent;

import static java.util.Objects.requireNonNull;

/**
 * IEC 60870 component used for telecontrol (supervisory control and data acquisition)
 * such as controlling electric power transmission grids and other geographically widespread control systems.
 */
@UriEndpoint(firstVersion = "2.20.0", scheme = "iec60870-server", syntax = "iec60870-server:uriPath",
    title = "IEC 60870 Server", label = "iot")
public class ServerEndpoint extends AbstractIecEndpoint<ServerConnectionMultiplexor> {

    /**
     * Filter out all requests which don't have the execute bit set
     */
    @UriParam(defaultValue = "true")
    private boolean filterNonExecute = true;

    public ServerEndpoint(final String uri, final DefaultComponent component, final ServerConnectionMultiplexor connection, final ObjectAddress address) {
        super(uri, component, requireNonNull(connection), address);
    }

    @Override
    public Producer createProducer() throws Exception {
        return new ServerProducer(this, getConnection().getServer());
    }

    @Override
    public Consumer createConsumer(final Processor processor) throws Exception {
        return new ServerConsumer(this, processor, getConnection().getServer());
    }

    public void setFilterNonExecute(final boolean filterNonExecute) {
        this.filterNonExecute = filterNonExecute;
    }

    public boolean isFilterNonExecute() {
        return this.filterNonExecute;
    }
}
