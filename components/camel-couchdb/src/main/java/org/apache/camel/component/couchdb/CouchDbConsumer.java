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
package org.apache.camel.component.couchdb;

import java.util.concurrent.ExecutorService;

import org.apache.camel.Processor;
import org.apache.camel.support.DefaultConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CouchDbConsumer extends DefaultConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(CouchDbConsumer.class);

    private final CouchDbClientWrapper couchClient;
    private final CouchDbEndpoint endpoint;
    private ExecutorService executor;
    private CouchDbChangesetTracker task;

    public CouchDbConsumer(CouchDbEndpoint endpoint, CouchDbClientWrapper couchClient, Processor processor) {
        super(endpoint, processor);
        this.couchClient = couchClient;
        this.endpoint = endpoint;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        LOG.info("Starting CouchDB consumer");

        executor = endpoint.getCamelContext().getExecutorServiceManager().newFixedThreadPool(this, endpoint.getEndpointUri(), 1);
        task = new CouchDbChangesetTracker(endpoint, this, couchClient);
        executor.submit(task);
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        LOG.info("Stopping CouchDB consumer");
        if (task != null) {
            task.stop();
        }
        if (executor != null) {
            endpoint.getCamelContext().getExecutorServiceManager().shutdownNow(executor);
            executor = null;
        }
    }

}
