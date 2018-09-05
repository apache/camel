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
package org.apache.camel.component.milo.server;

import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.milo.server.internal.CamelNamespace;
import org.apache.camel.component.milo.server.internal.CamelServerItem;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriPath;

/**
 * Make telemetry data available as an OPC UA server
 */
@UriEndpoint(firstVersion = "2.19.0", scheme = "milo-server", syntax = "milo-server:itemId", title = "OPC UA Server", consumerClass = MiloServerConsumer.class, label = "iot")
public class MiloServerEndpoint extends DefaultEndpoint {

    @UriPath
    @Metadata(required = "true")
    private String itemId;

    private final CamelNamespace namespace;

    private CamelServerItem item;

    public MiloServerEndpoint(final String uri, final String itemId, final CamelNamespace namespace, final Component component) {
        super(uri, component);
        this.itemId = itemId;
        this.namespace = namespace;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        this.item = this.namespace.getOrAddItem(this.itemId);
    }

    @Override
    protected void doStop() throws Exception {
        if (this.item != null) {
            this.item.dispose();
            this.item = null;
        }
        super.doStop();
    }

    @Override
    public Producer createProducer() throws Exception {
        return new MiloServerProducer(this, this.item);
    }

    @Override
    public Consumer createConsumer(final Processor processor) throws Exception {
        return new MiloServerConsumer(this, processor, this.item);
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    /**
     * ID of the item
     *
     * @param itemId the new ID of the item
     */
    public void setItemId(final String itemId) {
        this.itemId = itemId;
    }

    /**
     * Get the ID of the item
     *
     * @return the ID of the item
     */
    public String getItemId() {
        return this.itemId;
    }
}
