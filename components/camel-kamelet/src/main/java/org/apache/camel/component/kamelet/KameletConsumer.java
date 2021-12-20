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
package org.apache.camel.component.kamelet;

import org.apache.camel.Processor;
import org.apache.camel.ShutdownRunningTask;
import org.apache.camel.Suspendable;
import org.apache.camel.spi.InflightRepository;
import org.apache.camel.spi.ShutdownAware;
import org.apache.camel.support.DefaultConsumer;

final class KameletConsumer extends DefaultConsumer implements ShutdownAware, Suspendable {

    private final InflightRepository inflight;
    private final KameletComponent component;
    private final String key;

    public KameletConsumer(KameletEndpoint endpoint, Processor processor, String key) {
        super(endpoint, processor);
        this.component = endpoint.getComponent();
        this.key = key;
        this.inflight = endpoint.getCamelContext().getInflightRepository();
    }

    @Override
    public KameletEndpoint getEndpoint() {
        return (KameletEndpoint) super.getEndpoint();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        component.addConsumer(key, this);
    }

    @Override
    protected void doStop() throws Exception {
        component.removeConsumer(key, this);
        super.doStop();
    }

    @Override
    protected void doSuspend() throws Exception {
        component.removeConsumer(key, this);
    }

    @Override
    protected void doResume() throws Exception {
        // resume by using the start logic
        component.addConsumer(key, this);
    }

    @Override
    public boolean deferShutdown(ShutdownRunningTask shutdownRunningTask) {
        // deny stopping on shutdown as we want kamelet consumers to run in
        // case some other queues depend on this consumer to run, so it can
        // complete its exchanges
        return true;
    }

    @Override
    public int getPendingExchangesSize() {
        // capture the inflight counter from the route
        return inflight.size(getRouteId());
    }

    @Override
    public void prepareShutdown(boolean suspendOnly, boolean forced) {
        // noop
    }
}
