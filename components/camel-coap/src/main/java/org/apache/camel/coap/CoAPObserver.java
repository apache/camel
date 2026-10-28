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
package org.apache.camel.coap;

import java.io.IOException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.support.DefaultConsumer;
import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.CoapHandler;
import org.eclipse.californium.core.CoapObserveRelation;
import org.eclipse.californium.core.CoapResponse;

/**
 * The CoAP observer.
 */
public class CoAPObserver extends DefaultConsumer implements CoapHandler {
    private final CoAPEndpoint endpoint;
    // guards client and observation: onError() runs on a Californium thread and observes again, which must not race
    // doStop(), or the new observation is not cancelled (the server keeps notifying it) or the client is gone
    private final Lock lock = new ReentrantLock();
    private CoapClient client;
    private volatile Observation observation;

    public CoAPObserver(final CoAPEndpoint endpoint, final Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        lock.lock();
        try {
            if (this.client == null) {
                client = endpoint.createCoapClient(endpoint.getUri());
            }
            startObserve();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void onLoad(CoapResponse response) {
        Exchange camelExchange = createExchange(false);
        try {
            CoAPHelper.convertCoapResponseToMessage(response, camelExchange.getMessage());
            getProcessor().process(camelExchange);
        } catch (Exception e) {
            camelExchange.setException(e);
        } finally {
            Exception exception = camelExchange.getException();
            if (exception != null) {
                getExceptionHandler().handleException("Error processing observed update", camelExchange, exception);
            }
            releaseExchange(camelExchange, false);
        }
    }

    @Override
    protected void doStop() throws Exception {
        // cancel the observation, or the server keeps sending notifications to this consumer after it was stopped, and
        // a route that is started again then gets every notification twice (once from the stopped consumer)
        lock.lock();
        try {
            Observation current = observation;
            observation = null;
            if (current != null) {
                current.cancel();
            }
            if (client != null) {
                client.shutdown();
                client = null;
            }
        } finally {
            lock.unlock();
        }
        super.doStop();
    }

    @Override
    public void onError() {
        getExceptionHandler().handleException(new IOException("CoAP request timed out or has been rejected by the server"));
        lock.lock();
        try {
            // doStop() runs after the status became STOPPING, so either it cancels the observation that is started
            // here (with its relation set), or this sees that the consumer is stopping and does not observe again
            if (isRunAllowed() && client != null) {
                startObserve();
            }
        } finally {
            lock.unlock();
        }
    }

    // called with the lock held
    private void startObserve() {
        Observation next = new Observation();
        this.observation = next;
        next.relation = this.client.observe(next);
    }

    /**
     * The handler of one observe relation. The response to the cancel request that
     * {@link CoapObserveRelation#proactiveCancel()} sends (the current state of the resource) is delivered to the
     * handler of the relation too, so a cancelled observation drops everything it still gets. Otherwise that stale
     * response is routed by the stopped route (where it fails with a RouteStoppingException) or, after a quick restart,
     * by the started route in addition to the current state that its new observation gets.
     */
    private final class Observation implements CoapHandler {
        private volatile boolean cancelled;
        private volatile CoapObserveRelation relation;

        void cancel() {
            cancelled = true;
            CoapObserveRelation current = relation;
            if (current != null) {
                current.proactiveCancel();
            }
        }

        @Override
        public void onLoad(CoapResponse response) {
            if (!cancelled) {
                CoAPObserver.this.onLoad(response);
            }
        }

        @Override
        public void onError() {
            if (!cancelled) {
                CoAPObserver.this.onError();
            }
        }
    }
}
