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

import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultProducer;
import org.apache.gora.persistency.Persistent;
import org.apache.gora.store.DataStore;

import static org.apache.camel.component.gora.utils.GoraUtils.constractQueryFromPropertiesMap;
import static org.apache.camel.component.gora.utils.GoraUtils.getKeyFromExchange;
import static org.apache.camel.component.gora.utils.GoraUtils.getValueFromExchange;

/**
 * Camel-Gora {@link DefaultProducer}.
 */
public class GoraProducer extends DefaultProducer {

    /**
     * Camel-Gora endpoint configuration
     */
    private final GoraConfiguration configuration;

    /**
     * GORA datastore
     */
    private final DataStore<Object, Persistent> dataStore;

    /**
     * Constructor
     *
     * @param endpoint      Reference to the Camel-Gora endpoint
     * @param configuration Reference to Camel-Gora endpoint configuration
     * @param dataStore     Reference to the datastore
     */
    public GoraProducer(final GoraEndpoint endpoint,
                        final GoraConfiguration configuration,
                        final DataStore<Object, Persistent> dataStore) {

        super(endpoint);
        this.dataStore = dataStore;
        this.configuration = configuration;
    }

    @Override
    public void process(final Exchange exchange) throws Exception {
        final String operation = (String) exchange.getIn().getHeader(GoraAttribute.GORA_OPERATION.value);

        if (operation == null || operation.isEmpty()) {
            throw new RuntimeException("Gora operation is null or empty!");
        }

        Object result = 0; // 0 used as default response in order to avoid null body exception

        if (GoraOperation.PUT.value.equalsIgnoreCase(operation)) {
            dataStore.put(getKeyFromExchange(exchange), getValueFromExchange(exchange));
        } else if (GoraOperation.GET.value.equalsIgnoreCase(operation)) {
            result = dataStore.get(getKeyFromExchange(exchange));
        } else if (GoraOperation.DELETE.value.equalsIgnoreCase(operation)) {
            result = dataStore.delete(getKeyFromExchange(exchange));
        } else if (GoraOperation.QUERY.value.equalsIgnoreCase(operation)) {
            final Map<String, Object> props = exchange.getIn().getHeaders();
            result = constractQueryFromPropertiesMap(props, dataStore, this.configuration).execute();
        } else if (GoraOperation.DELETE_BY_QUERY.value.equalsIgnoreCase(operation)) {
            final Map<String, Object> props = exchange.getIn().getHeaders();
            result = dataStore.deleteByQuery(constractQueryFromPropertiesMap(props, dataStore, this.configuration));
        } else if (GoraOperation.GET_SCHEMA_NAME.value.equalsIgnoreCase(operation)) {
            result = dataStore.getSchemaName();
        } else if (GoraOperation.DELETE_SCHEMA.value.equalsIgnoreCase(operation)) {
            dataStore.deleteSchema();
        } else if (GoraOperation.CREATE_SCHEMA.value.equalsIgnoreCase(operation)) {
            dataStore.createSchema();
        } else if (GoraOperation.SCHEMA_EXIST.value.equalsIgnoreCase(operation)) {
            result = dataStore.schemaExists();
        } else {
            throw new RuntimeException("Unknown operation: " + operation);
        }

        /*
           from the tests auto-flush seems not to work always
           therefore a temporary solution is calling flush
           on every action
        */
        if (configuration.isFlushOnEveryOperation()) {
            dataStore.flush();
        }

        exchange.getOut().setBody(result);
        // preserve headers
        exchange.getOut().setHeaders(exchange.getIn().getHeaders());
    }

}
