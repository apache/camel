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
package org.apache.camel.component.springrabbit;

import java.net.URISyntaxException;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.spi.SendDynamicAware;
import org.apache.camel.spi.annotations.SendDynamic;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.URISupport;

/**
 * RabbitMQ based {@link org.apache.camel.spi.SendDynamicAware} which allows to optimise RabbitMQ components with the
 * toD (dynamic to) DSL in Camel. This implementation optimises by allowing to provide dynamic parameters via
 * {@link SpringRabbitMQConstants#EXCHANGE_OVERRIDE_NAME} header instead of the endpoint uri. That allows to use a
 * static endpoint and its producer to service dynamic requests.
 */
@SendDynamic("spring-rabbitmq")
public class SpringRabbitMQSendDynamicAware extends ServiceSupport implements SendDynamicAware {

    private CamelContext camelContext;
    private String scheme;

    @Override
    public String getScheme() {
        return scheme;
    }

    @Override
    public void setScheme(String scheme) {
        this.scheme = scheme;
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public boolean isLenientProperties() {
        return false;
    }

    @Override
    public DynamicAwareEntry prepare(Exchange exchange, String uri, String originalUri) throws Exception {
        return new DynamicAwareEntry(uri, originalUri, null, null);
    }

    @Override
    public String resolveStaticUri(Exchange exchange, DynamicAwareEntry entry) throws Exception {
        String answer = null;

        String exchangeName = parseExchangeName(entry.getUri());
        if (exchangeName != null) {
            String originalExchangeName = parseExchangeName(entry.getOriginalUri());
            if (!exchangeName.equals(originalExchangeName)) {
                // okay the exchange name was dynamic, so use the original as endpoint name
                answer = entry.getUri();
                answer = StringHelper.replaceFirst(answer, exchangeName, originalExchangeName);
            }
        }
        String routingKey = parseRoutingKey(entry.getUri());
        if (routingKey != null) {
            String originalRoutingKey = parseRoutingKey(entry.getOriginalUri());
            if (!routingKey.equals(originalRoutingKey)) {
                // okay the routing key was dynamic, so use the original as static uri
                if (answer == null) {
                    answer = entry.getUri();
                }
                answer = StringHelper.replaceFirst(answer, "routingKey=" + routingKey, "routingKey=" + originalRoutingKey);
            }
        }

        return answer;
    }

    @Override
    public Processor createPreProcessor(Exchange exchange, DynamicAwareEntry entry) throws Exception {
        // preserve existing headers if they are already there
        String s = exchange.getMessage().getHeader(SpringRabbitMQConstants.EXCHANGE_OVERRIDE_NAME, String.class);
        final String destinationName = s != null ? s : parseExchangeName(entry.getUri());

        s = exchange.getMessage().getHeader(SpringRabbitMQConstants.ROUTING_OVERRIDE_KEY, String.class);
        final String routingKey = s != null ? s : parseRoutingKey(entry.getUri());

        return new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                if (destinationName != null) {
                    exchange.getMessage().setHeader(SpringRabbitMQConstants.EXCHANGE_OVERRIDE_NAME, destinationName);
                }
                if (routingKey != null) {
                    exchange.getMessage().setHeader(SpringRabbitMQConstants.ROUTING_OVERRIDE_KEY, routingKey);
                }
            }
        };
    }

    @Override
    public Processor createPostProcessor(Exchange exchange, DynamicAwareEntry entry) throws Exception {
        // no post processor is needed
        return null;
    }

    private String parseExchangeName(String uri) {
        // strip query
        uri = uri.replaceFirst(scheme + "://", ":");
        uri = StringHelper.before(uri, "?", uri);

        // exchange name is after first colon
        int pos = uri.indexOf(':');
        if (pos != -1) {
            return uri.substring(pos + 1);
        } else {
            return null;
        }
    }

    private String parseRoutingKey(String uri) throws URISyntaxException {
        String query = URISupport.extractQuery(uri);
        if (query != null) {
            Object key = URISupport.parseQuery(query).get("routingKey");
            return key != null ? key.toString() : null;
        }
        return null;
    }

}
