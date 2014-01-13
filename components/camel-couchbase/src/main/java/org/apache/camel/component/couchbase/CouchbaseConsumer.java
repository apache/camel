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

package org.apache.camel.component.couchbase;

import com.couchbase.client.CouchbaseClient;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultConsumer;

import java.util.concurrent.ExecutorService;

public class CouchbaseConsumer extends DefaultConsumer {

    private final CouchbaseEndpoint endpoint;
    private final CouchbaseClient client;
    private ExecutorService executor;
    private CouchbaseReceiver task;


    public CouchbaseConsumer(CouchbaseEndpoint endpoint, CouchbaseClient client, Processor processor) {
        super(endpoint, processor);
        this.client = client;
        this.endpoint = endpoint;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        log.info("Starting Couchbase consumer");

        executor = endpoint.getCamelContext().getExecutorServiceManager().newFixedThreadPool(this, endpoint.getEndpointUri(), 1);
        task = new CouchbaseReceiver(endpoint, this, client);
        executor.submit(task);
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        log.info("Stopping Couchbase consumer");
        if (task != null) {
            task.stop();
        }
        if (executor != null) {
            endpoint.getCamelContext().getExecutorServiceManager().shutdownNow(executor);
            executor = null;
        }
    }

}

