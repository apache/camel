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
package org.apache.camel.component.vm;

import java.util.concurrent.BlockingQueue;

import org.apache.camel.Component;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.seda.BlockingQueueFactory;
import org.apache.camel.component.seda.SedaEndpoint;
import org.apache.camel.spi.UriEndpoint;

/**
 * The vm component provides asynchronous call to another endpoint from the same CamelContext.
 */
@UriEndpoint(firstVersion = "1.1.0", scheme = "vm", title = "VM", syntax = "vm:name", label = "core,endpoint")
public class VmEndpoint extends SedaEndpoint {

    public VmEndpoint(String endpointUri, Component component, BlockingQueue<Exchange> queue) {
        super(endpointUri, component, queue);
    }

    public VmEndpoint(String endpointUri, Component component, BlockingQueue<Exchange> queue, int concurrentConsumers) {
        super(endpointUri, component, queue, concurrentConsumers);
    }

    public VmEndpoint(String endpointUri, Component component, BlockingQueueFactory<Exchange> queueFactory, int concurrentConsumers) {
        super(endpointUri, component, queueFactory, concurrentConsumers);
    }

    @Override
    protected VmConsumer createNewConsumer(Processor processor) {
        return new VmConsumer(this, processor);
    }

}
