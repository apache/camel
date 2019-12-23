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
package org.apache.camel.component.gora;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.gora.persistency.Persistent;
import org.apache.gora.store.DataStore;

/**
 * The gora component allows you to work with NoSQL databases using the Apache Gora framework.
 */
@UriEndpoint(firstVersion = "2.14.0", scheme = "gora", title = "Gora", syntax = "gora:name", label = "database,hadoop,nosql")
public class GoraEndpoint extends DefaultEndpoint {

    /**
     * Gora DataStore
     */
    private final DataStore<Object, Persistent> dataStore;

    /**
     * Camel-Gora Endpoint Configuratopn
     */
    @UriParam
    private GoraConfiguration configuration;

    /**
     * GORA endpoint default constructor
     *
     * @param uri           Endpoint URI
     * @param goraComponent Reference to the Camel-Gora component
     * @param config        Reference to Camel-Gora endpoint configuration
     * @param dataStore     Reference to Gora DataStore
     */
    public GoraEndpoint(final String uri,
                        final GoraComponent goraComponent,
                        final GoraConfiguration config,
                        final DataStore<Object, Persistent> dataStore) {

        super(uri, goraComponent);
        this.configuration = config;
        this.dataStore = dataStore;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new GoraProducer(this, this.configuration, this.dataStore);
    }

    @Override
    public Consumer createConsumer(final Processor processor) throws Exception {
        return new GoraConsumer(this, processor, this.configuration, this.dataStore);
    }

    public GoraConfiguration getConfiguration() {
        return configuration;
    }

}
