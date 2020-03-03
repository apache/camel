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

import java.lang.reflect.InvocationTargetException;

import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.gora.utils.GoraUtils;
import org.apache.camel.support.ScheduledPollConsumer;
import org.apache.gora.persistency.Persistent;
import org.apache.gora.query.Query;
import org.apache.gora.query.Result;
import org.apache.gora.store.DataStore;

/**
 * Implementation of Camel-Gora {@link Consumer}.
 */
public class GoraConsumer extends ScheduledPollConsumer {

    /**
     * GORA datastore
     */
    private final DataStore<Object, Persistent> dataStore;

    /**
     * Camel-Gora endpoint configuration
     */
    private final GoraConfiguration configuration;

    /**
     * Camel Gora Query
     */
    private Query query;

    /**
     * Poll run
     */
    private boolean firstRun;


    /**
     * Consumer Constructor
     *
     * @param endpoint      Reference to the Camel-Gora endpoint
     * @param processor     Reference to Consumer Processor
     * @param configuration Reference to Camel-Gora endpoint configuration
     * @param dataStore     Reference to the datastore
     */
    public GoraConsumer(final Endpoint endpoint,
                        final Processor processor,
                        final GoraConfiguration configuration,
                        final DataStore<Object, Persistent> dataStore) throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {

        super(endpoint, processor);
        this.configuration = configuration;
        this.dataStore = dataStore;
        this.query = GoraUtils.constractQueryFromConfiguration(this.dataStore, this.configuration);
    }

    @Override
    protected int poll() throws Exception {
        final Exchange exchange = this.getEndpoint().createExchange();

        // compute time (approx) since last update
        if (firstRun) {
            this.query.setStartTime(System.currentTimeMillis());
        } else {
            this.query.setStartTime(System.currentTimeMillis() - getDelay());
        }

        //proceed with query
        final Result result = query.execute();

        try {
            getProcessor().process(exchange);
        } finally {
            if (exchange.getException() != null) {
                getExceptionHandler().handleException("Error processing exchange", exchange, exchange.getException());
            }
        }

        return Long.valueOf(result.getOffset()).intValue();
    }
}

