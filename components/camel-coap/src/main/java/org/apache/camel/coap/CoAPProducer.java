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

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.support.DefaultProducer;
import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.CoapResponse;
import org.eclipse.californium.core.coap.MediaTypeRegistry;

/**
 * The CoAP producer.
 */
public class CoAPProducer extends DefaultProducer {
    private final CoAPEndpoint endpoint;
    private volatile CoapClient client;

    public CoAPProducer(CoAPEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        initClient();

        String ct = exchange.getIn().getHeader(CoAPConstants.CONTENT_TYPE, String.class);
        if (ct == null) {
            // ?default?
            ct = "application/octet-stream";
        }
        String method = CoAPHelper.getDefaultMethod(exchange, client);
        int mediaType = MediaTypeRegistry.parse(ct);
        CoapResponse response = null;
        boolean pingResponse = false;
        switch (method) {
            case CoAPConstants.METHOD_GET:
                response = client.get();
                break;
            case CoAPConstants.METHOD_DELETE:
                response = client.delete();
                break;
            case CoAPConstants.METHOD_POST:
                byte[] bodyPost = exchange.getIn().getBody(byte[].class);
                response = client.post(bodyPost, mediaType);
                break;
            case CoAPConstants.METHOD_PUT:
                byte[] bodyPut = exchange.getIn().getBody(byte[].class);
                response = client.put(bodyPut, mediaType);
                break;
            case CoAPConstants.METHOD_PING:
                pingResponse = client.ping();
                break;
            default:
                break;
        }

        if (response != null) {
            CoAPHelper.convertCoapResponseToMessage(response, exchange.getOut());
        }

        if (method.equalsIgnoreCase(CoAPConstants.METHOD_PING)) {
            Message resp = exchange.getOut();
            resp.setBody(pingResponse);
        }
    }

    protected synchronized void initClient() throws Exception {
        if (client == null) {
            client = endpoint.createCoapClient(endpoint.getUri());
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (client != null) {
            client.shutdown();
            client = null;
        }
    }
}
