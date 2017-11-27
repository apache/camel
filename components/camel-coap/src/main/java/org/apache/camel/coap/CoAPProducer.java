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
package org.apache.camel.coap;

import java.net.URI;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.impl.DefaultProducer;
import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.CoapResponse;
import org.eclipse.californium.core.coap.MediaTypeRegistry;

/**
 * The CoAP producer.
 */
public class CoAPProducer extends DefaultProducer {
    private final CoAPEndpoint endpoint;
    private CoapClient client;

    public CoAPProducer(CoAPEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    public void process(Exchange exchange) throws Exception {
        CoapClient client = getClient(exchange);
        String ct = exchange.getIn().getHeader(Exchange.CONTENT_TYPE, String.class);
        if (ct == null) {
            //?default?
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
            Message resp = exchange.getOut();
            String mt = MediaTypeRegistry.toString(response.getOptions().getContentFormat());
            resp.setHeader(org.apache.camel.Exchange.CONTENT_TYPE, mt);
            resp.setHeader(CoAPConstants.COAP_RESPONSE_CODE, response.getCode().toString());
            resp.setBody(response.getPayload());
        }

        if (method.equalsIgnoreCase(CoAPConstants.METHOD_PING)) {
            Message resp = exchange.getOut();
            resp.setBody(pingResponse);
        }
    }

    private synchronized CoapClient getClient(Exchange exchange) {
        if (client == null) {
            URI uri = exchange.getIn().getHeader(CoAPConstants.COAP_URI, URI.class);
            if (uri == null) {
                uri = endpoint.getUri();
            }
            client = new CoapClient(uri);
        }
        return client;
    }
}
