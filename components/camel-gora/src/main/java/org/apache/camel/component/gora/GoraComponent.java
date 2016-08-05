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
package org.apache.camel.component.gora;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;

import org.apache.camel.Endpoint;
import org.apache.camel.impl.UriEndpointComponent;
import org.apache.gora.persistency.Persistent;
import org.apache.gora.store.DataStore;
import org.apache.gora.store.DataStoreFactory;
import org.apache.hadoop.conf.Configuration;

import static org.apache.camel.component.gora.GoraConstants.GORA_DEFAULT_DATASTORE_KEY;

public class GoraComponent extends UriEndpointComponent {

    /**
     * GORA datastore
     */
    private DataStore<Object, Persistent> dataStore;

    /**
     * GORA properties
     */
    private Properties goraProperties;

    /**
     * Hadoop configuration
     */
    private Configuration configuration;

    public GoraComponent() {
        super(GoraEndpoint.class);
    }

    /**
     *
     * Initialize class and create DataStore instance
     *
     * @param config component configuration
     * @throws IOException
     */
    private void init(final GoraConfiguration config) throws IOException {
        this.goraProperties = DataStoreFactory.createProps();
        this.dataStore = DataStoreFactory.getDataStore(goraProperties.getProperty(GORA_DEFAULT_DATASTORE_KEY,
                                                                                  config.getDataStoreClass()),
                                                        config.getKeyClass(),
                                                        config.getValueClass(),
                                                        this.configuration);
    }

    @Override
    protected Endpoint createEndpoint(final String uri,
                                      final String remaining,
                                      final Map<String, Object> parameters) throws Exception {

        final GoraConfiguration config = new GoraConfiguration();
        setProperties(config, parameters);
        config.setName(remaining);
        init(config);
        return new GoraEndpoint(uri, this, config, dataStore);
    }

    /**
     * Get DataStore
     */
    public DataStore<Object, Persistent> getDataStore() {
        return dataStore;
    }
    
    @Override
    protected void doStart() throws Exception {
        if (configuration == null) {
            configuration = new Configuration();
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (dataStore != null) {
            dataStore.close();
        }
    }

}