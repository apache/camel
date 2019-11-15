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
package org.apache.camel.component.atomix.client;

import io.atomix.AtomixClient;
import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.util.ObjectHelper;

public abstract class AbstractAtomixClientEndpoint<T extends AbstractAtomixClientComponent, C extends AtomixClientConfiguration> extends DefaultEndpoint {
    @UriPath(description = "The distributed resource name")
    @Metadata(required = true)
    private final String resourceName;

    private AtomixClient atomix;

    protected AbstractAtomixClientEndpoint(String uri, T component, String resourceName) {
        super(uri, component);

        this.resourceName = resourceName;
    }

    @Override
    public Producer createProducer() throws Exception {
        throw new UnsupportedOperationException("Producer not supported");
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Consumer not supported");
    }

    @Override
    protected void doStart() throws Exception {
        if (atomix == null) {
            final C configuration = getConfiguration();
            final CamelContext context = getCamelContext();

            ObjectHelper.notNull(configuration, "Configuration");
            ObjectHelper.notNull(context, "CamelContext");

            atomix = AtomixClientHelper.createClient(context, configuration);
            atomix.connect(configuration.getNodes()).join();
        }

        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        if (atomix != null) {
            atomix.close();
        }
    }

    // **********************************
    // Helpers for implementations
    // **********************************

    @SuppressWarnings("unchecked")
    public T getAtomixComponent() {
        return (T)super.getComponent();
    }

    public AtomixClient getAtomix() {
        return atomix;
    }

    public String getResourceName() {
        return resourceName;
    }

    // **********************************
    // Abstract
    // **********************************

    public abstract C getConfiguration();

    public abstract void setConfiguration(C configuration);
}
