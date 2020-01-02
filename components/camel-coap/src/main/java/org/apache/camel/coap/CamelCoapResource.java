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

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.camel.Message;
import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.coap.CoAP.ResponseCode;
import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.eclipse.californium.core.coap.OptionSet;
import org.eclipse.californium.core.network.Exchange;
import org.eclipse.californium.core.server.resources.CoapExchange;
import org.eclipse.californium.core.server.resources.Resource;

final class CamelCoapResource extends CoapResource {
    private final Map<String, CoAPConsumer> consumers = new ConcurrentHashMap<>();
    private final List<CamelCoapResource> possibles;

    CamelCoapResource(String name, CoAPConsumer consumer) {
        super(name);
        addConsumer(consumer);
        possibles = null;
    }

    private CamelCoapResource(String name, List<CamelCoapResource> possibles) {
        super(name);
        this.possibles = possibles;
    }

    void addConsumer(CoAPConsumer consumer) {
        CoAPEndpoint coapEndpoint = consumer.getCoapEndpoint();
        String coapMethodRestrict = CoAPHelper.getDefaultMethodRestrict(coapEndpoint.getCoapMethodRestrict());
        for (String method : coapMethodRestrict.split(",")) {
            consumers.put(method.trim(), consumer);
        }
    }

    @Override
    public Resource getChild(String name) {
        if (possibles != null) {
            // FIXME - find which might work...
        }
        Resource child = super.getChild(name);
        if (child == null) {
            final List<CamelCoapResource> possibles = new LinkedList<>();
            for (Resource r : getChildren()) {
                if (r.getName().startsWith("{") && r.getName().endsWith("}")) {
                    possibles.add((CamelCoapResource)r);
                }
            }
            if (possibles.size() == 1) {
                return possibles.get(0);
            }
            if (!possibles.isEmpty()) {
                return new CamelCoapResource(name, possibles);
            }
        }
        return child;
    }

    @Override
    public void handleRequest(Exchange exchange) {
        org.apache.camel.Exchange camelExchange = null;
        CoAPConsumer consumer = null;
        if (possibles != null) {
            consumers.putAll(possibles.get(0).consumers);
        }
        CoapExchange cexchange = new CoapExchange(exchange, this);
        try {
            consumer = consumers.get(exchange.getRequest().getCode().name());
            if (consumer == null) {
                cexchange.respond(ResponseCode.METHOD_NOT_ALLOWED);
                return;
            }

            camelExchange = consumer.getEndpoint().createExchange();
            consumer.createUoW(camelExchange);

            OptionSet options = exchange.getRequest().getOptions();
            for (String s : options.getUriQuery()) {
                int i = s.indexOf('=');
                if (i == -1) {
                    camelExchange.getIn().setHeader(s, "");
                } else {
                    camelExchange.getIn().setHeader(s.substring(0, i), s.substring(i + 1));
                }
            }

            if (options.hasContentFormat()) {
                String mt = MediaTypeRegistry.toString(options.getContentFormat());
                camelExchange.getIn().setHeader(org.apache.camel.Exchange.CONTENT_TYPE, mt);
            }

            List<String> path = exchange.getRequest().getOptions().getUriPath();
            LinkedList<Resource> resources = new LinkedList<>();
            Resource r = this;
            while (r != null) {
                resources.push(r);
                r = r.getParent();
            }
            if (resources.getFirst().getName().isEmpty()) {
                resources.removeFirst();
            }
            int res = 0;
            while (!resources.isEmpty() && res < path.size()) {
                r = resources.removeFirst();
                if (r.getName().charAt(0) == '{' && r.getName().charAt(r.getName().length() - 1) == '}') {
                    String n = r.getName().substring(1, r.getName().length() - 1);
                    camelExchange.getIn().setHeader(n, path.get(res));
                }
                res++;
            }

            byte bytes[] = exchange.getCurrentRequest().getPayload();
            camelExchange.getIn().setBody(bytes);

            consumer.getProcessor().process(camelExchange);
            Message target = camelExchange.getMessage();

            int format = MediaTypeRegistry.parse(target.getHeader(org.apache.camel.Exchange.CONTENT_TYPE, String.class));
            cexchange.respond(ResponseCode.CONTENT, target.getBody(byte[].class), format);

        } catch (Exception e) {
            cexchange.respond(ResponseCode.INTERNAL_SERVER_ERROR, e.getMessage());
        } finally {
            if (camelExchange != null) {
                consumer.doneUoW(camelExchange);
            }
        }
    }
}
