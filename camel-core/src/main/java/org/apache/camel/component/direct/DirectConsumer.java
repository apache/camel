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
package org.apache.camel.component.direct;

import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.ShutdownRunningTask;
import org.apache.camel.Suspendable;
import org.apache.camel.impl.DefaultConsumer;
import org.apache.camel.spi.ShutdownAware;

/**
 * The direct consumer.
 *
 * @version 
 */
public class DirectConsumer extends DefaultConsumer implements ShutdownAware, Suspendable {

    private DirectEndpoint endpoint;

    public DirectConsumer(Endpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = (DirectEndpoint) endpoint;
    }

    @Override
    public DirectEndpoint getEndpoint() {
        return (DirectEndpoint) super.getEndpoint();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        // add consumer to endpoint
        boolean existing = this == endpoint.getConsumer();
        if (!existing && endpoint.hasConsumer(this)) {
            throw new IllegalArgumentException("Cannot add a 2nd consumer to the same endpoint. Endpoint " + endpoint + " only allows one consumer.");
        }
        if (!existing) {
            endpoint.addConsumer(this);
        }
    }

    @Override
    protected void doStop() throws Exception {
        endpoint.removeConsumer(this);
        super.doStop();
    }

    @Override
    protected void doSuspend() throws Exception {
        endpoint.removeConsumer(this);
    }

    @Override
    protected void doResume() throws Exception {
        // resume by using the start logic
        doStart();
    }

    public boolean deferShutdown(ShutdownRunningTask shutdownRunningTask) {
        // deny stopping on shutdown as we want direct consumers to run in case some other queues
        // depend on this consumer to run, so it can complete its exchanges
        return true;
    }

    public int getPendingExchangesSize() {
        // return 0 as we do not have an internal memory queue with a variable size
        // of inflight messages. 
        return 0;
    }

    public void prepareShutdown(boolean suspendOnly, boolean forced) {
        // noop
    }
}
