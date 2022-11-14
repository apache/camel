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

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.support.DefaultConsumer;
import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.CoapHandler;
import org.eclipse.californium.core.CoapResponse;

/**
 * The CoAP observer.
 */
public class CoAPObserver extends DefaultConsumer implements CoapHandler {
    private final CoAPEndpoint endpoint;
    private CoapClient client;

    public CoAPObserver(final CoAPEndpoint endpoint, final Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        if (this.client == null) {
            client = endpoint.createCoapClient(endpoint.getUri());
        }
        startObserve();
    }

    @Override
    public void onLoad(CoapResponse response) {
        Exchange camelExchange = createExchange(false);
        try {
            CoAPHelper.convertCoapResponseToMessage(response, camelExchange.getMessage());
            getProcessor().process(camelExchange);
        } catch (Exception ignored) {
        } finally {
            Exception exception = camelExchange.getException();
            if (exception != null) {
                getExceptionHandler().handleException("Error processing observed update", camelExchange, exception);
            }
            releaseExchange(camelExchange, false);
        }
    }

    @Override
    public void onError() {
        getExceptionHandler().handleException(new IOException("CoAP request timed out or has been rejected by the server"));
        startObserve();
    }

    private void startObserve() {
        this.client.observe(this);
    }
}
