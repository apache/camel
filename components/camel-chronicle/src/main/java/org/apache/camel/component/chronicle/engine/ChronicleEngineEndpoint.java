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
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;

/**
 * The camel chronicle-engine component let you leverage the power of OpenHFT's <a href="https://github.com/OpenHFT/Chronicle-Engine">Chronicle-Engine</a>.
 */
@UriEndpoint(
    firstVersion = "2.18.0", 
    scheme = "chronicle-engine",
    title = "Chronicle Engine",
    syntax = "chronicle-engine:addresses/path", 
    consumerClass = ChronicleEngineConsumer.class, 
    label = "datagrid,cache")
public class ChronicleEngineEndpoint extends DefaultEndpoint {

    @UriPath(description = "Engine addresses. Multiple addresses can be separated by comma.")
    @Metadata(required = "true")
    private String addresses;
    @UriPath(description = "Engine path")
    @Metadata(required = "true")
    private String path;
    @UriParam
    private ChronicleEngineConfiguration configuration;

    public ChronicleEngineEndpoint(String uri, ChronicleEngineComponent component, ChronicleEngineConfiguration configuration) throws Exception {
        super(uri, component);
        this.configuration = configuration;
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
        if (!this.path.startsWith("/")) {
            this.path = "/" + this.path;
        }
    }

    @Override
    protected void doStop() throws Exception {
    }

    public void setAddresses(String addresses) {
        this.addresses = addresses;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public void setConfiguration(ChronicleEngineConfiguration configuration) {
        this.configuration = configuration;
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
            ? path
            : path + "?dontPersist=true";
    }

    protected AssetTree createRemoteAssetTree() {
        String[] urls = addresses.split(",");
        return new VanillaAssetTree()
            .forRemoteAccess(urls, configuration.getWireType());
    }
}
