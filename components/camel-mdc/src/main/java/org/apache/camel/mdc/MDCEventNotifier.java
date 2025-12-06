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
package org.apache.camel.mdc;

import org.apache.camel.spi.CamelEvent;
import org.apache.camel.support.SimpleEventNotifierSupport;

/**
 * Use {@link org.apache.camel.spi.EventNotifier} to ensure MDC is unset when exchange is either completed/failed or
 * handed over to another thread via asynchronous processing.
 */
class MDCEventNotifier extends SimpleEventNotifierSupport {

    private final MDCService mdc;

    public MDCEventNotifier(MDCService mdc) {
        this.mdc = mdc;
    }

    @Override
    protected void setupIgnore(boolean ignore) {
        setIgnoreCamelContextEvents(true);
        setIgnoreCamelContextInitEvents(true);
        setIgnoreRouteEvents(true);
        setIgnoreServiceEvents(true);
        setIgnoreStepEvents(true);
        // we need also async processing started events
        setIgnoreExchangeAsyncProcessingStartedEvents(false);
    }

    @Override
    public void notify(CamelEvent event) throws Exception {
        if (event instanceof CamelEvent.ExchangeAsyncProcessingStartedEvent eap) {
            // exchange is continued processed on another thread so unset MDC
            mdc.unsetMDC(eap.getExchange());
        } else if (event instanceof CamelEvent.ExchangeCompletedEvent ec) {
            // exchange is completed so unset MDC
            mdc.unsetMDC(ec.getExchange());
        } else if (event instanceof CamelEvent.ExchangeFailedEvent ef) {
            // exchange is failed so unset MDC
            mdc.unsetMDC(ef.getExchange());
        }
    }
}
