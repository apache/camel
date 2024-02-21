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
package org.apache.camel.component.stub;

import java.util.concurrent.BlockingQueue;

import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.seda.BlockingQueueFactory;
import org.apache.camel.component.seda.SedaEndpoint;
import org.apache.camel.spi.UriEndpoint;

/**
 * Stub out any physical endpoints while in development or testing.
 *
 * For example to run a route without needing to actually connect to a specific SMTP or HTTP endpoint. Just add stub: in
 * front of any endpoint URI to stub out the endpoint. Internally the Stub component creates Seda endpoints. The main
 * difference between Stub and Seda is that Seda will validate the URI and parameters you give it, so putting seda: in
 * front of a typical URI with query arguments will usually fail. Stub won't though, as it basically ignores all query
 * parameters to let you quickly stub out one or more endpoints in your route temporarily.
 */
@UriEndpoint(firstVersion = "2.10.0", scheme = "stub", title = "Stub", syntax = "stub:name",
             remote = false, category = { Category.CORE, Category.TESTING }, lenientProperties = true)
public class StubEndpoint extends SedaEndpoint {

    public StubEndpoint(String endpointUri, Component component, BlockingQueue<Exchange> queue) {
        super(endpointUri, component, queue);
    }

    public StubEndpoint(String endpointUri, Component component, BlockingQueue<Exchange> queue, int concurrentConsumers) {
        super(endpointUri, component, queue, concurrentConsumers);
    }

    public StubEndpoint(String endpointUri, Component component, BlockingQueueFactory<Exchange> queueFactory,
                        int concurrentConsumers) {
        super(endpointUri, component, queueFactory, concurrentConsumers);
    }

    @Override
    protected StubConsumer createNewConsumer(Processor processor) {
        return new StubConsumer(this, processor);
    }

    @Override
    public Producer createProducer() throws Exception {
        return new StubProducer(
                this, getWaitForTaskToComplete(), getTimeout(), isBlockWhenFull(), isDiscardWhenFull(), getOfferTimeout());
    }
}
