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
package org.apache.camel.impl;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.util.EventHelper;
import org.apache.camel.util.StopWatch;

/**
 * Helper class to notify on exchange sending events in async engine
 */
public class EventNotifierCallback implements AsyncCallback {
    private final AsyncCallback originalCallback;
    private final StopWatch watch;
    private final Exchange exchange;
    private final Endpoint endpoint;
    private final boolean sending;

    public EventNotifierCallback(AsyncCallback originalCallback, Exchange exchange,
            Endpoint endpoint) {
        this.originalCallback = originalCallback;
        this.exchange = exchange;
        this.endpoint = endpoint;
        this.sending = EventHelper.notifyExchangeSending(exchange.getContext(), exchange, endpoint);
        if (this.sending) {
            this.watch = new StopWatch();
        } else {
            this.watch = null;
        }
    }

    @Override
    public void done(boolean doneSync) {
        if (watch != null) {
            long timeTaken = watch.taken();
            EventHelper.notifyExchangeSent(exchange.getContext(), exchange, endpoint, timeTaken);
        }
        originalCallback.done(doneSync);
    }
}
