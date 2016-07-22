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
package org.apache.camel.component.chronicle.engine;

import net.openhft.chronicle.engine.api.tree.AssetTree;
import net.openhft.chronicle.engine.tree.VanillaAssetTree;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriPath;
import org.apache.camel.util.ObjectHelper;


@UriEndpoint(scheme = "chronicle-engine", title = "Chronicle Engine", syntax = "chronicle-engine:addresses/path", consumerClass = ChronicleEngineConsumer.class, label = "Chronicle")
public class ChronicleEngineEndpoint extends DefaultEndpoint {
    private final ChronicleEngineConfiguration configuration;

    @UriPath(description = "engine path")
    @Metadata(required = "true")
    private final String path;

    public ChronicleEngineEndpoint(String uri, ChronicleEngineComponent component, ChronicleEngineConfiguration configuration) throws Exception {
        super(uri, component);

        ObjectHelper.notNull(configuration.getCamelContext(), "camelContext");
        ObjectHelper.notNull(configuration.getAddresses(), "addresses");
        ObjectHelper.notNull(configuration.getPath(), "path");
        ObjectHelper.notNull(configuration.getWireType(), "wireType");

        this.configuration = configuration;
        this.path = configuration.getPath();
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new ChronicleEngineProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        return new ChronicleEngineConsumer(this, processor);
    }

    @Override
    protected void doStart() throws Exception {
    }

    @Override
    protected void doStop() throws Exception {
    }

    // ****************************
    // Helpers
    // ****************************

    protected ChronicleEngineConfiguration getConfiguration() {
        return configuration;
    }

    protected String getPath() {
        return this.path;
    }

    protected String getUri() {
        return configuration.isPersistent()
            ? configuration.getPath()
            : configuration.getPath() + "?dontPersist=true";
    }

    protected AssetTree createRemoteAssetTree() {
        return new VanillaAssetTree()
            .forRemoteAccess(configuration.getAddresses(), configuration.getWireType());
    }
}
