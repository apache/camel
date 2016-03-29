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
package org.apache.camel.component.hystrix;

import com.netflix.hystrix.HystrixCommand;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.util.CamelContextHelper;

public class CamelHystrixCommand extends HystrixCommand<Exchange> {
    private final Exchange exchange;
    private final String cacheKey;
    private String runEndpointId;
    private String fallbackEndpointId;

    protected CamelHystrixCommand(Setter setter, Exchange exchange, String cacheKey, String runEndpointId,  String fallbackEndpointId) {
        super(setter);
        this.exchange = exchange;
        this.cacheKey = cacheKey;
        this.runEndpointId = runEndpointId;
        this.fallbackEndpointId = fallbackEndpointId;
    }

    @Override
    protected String getCacheKey() {
        return cacheKey;
    }

    @Override
    protected Exchange getFallback() {
        if (fallbackEndpointId == null) {
            super.getFallback();
        }
        try {
            Endpoint endpoint = findEndpoint(fallbackEndpointId);
            if (exchange.getException() != null) {
                Exception exception = exchange.getException();
                exchange.setException(null);
                if (exception instanceof InterruptedException) {
                    exchange.removeProperty(Exchange.ROUTE_STOP);
                }
            }

            endpoint.createProducer().process(exchange);
        } catch (Exception exception) {
            throw new RuntimeException(exception.getMessage());
        }
        return exchange;
    }

    @Override
    protected Exchange run() {
        try {
            Endpoint endpoint = findEndpoint(runEndpointId);
            endpoint.createProducer().process(exchange);
        } catch (Exception exception) {
            exchange.setException(null);
            if (exception instanceof InterruptedException) {
                exchange.removeProperty(Exchange.ROUTE_STOP);
            }
            throw new RuntimeException(exception.getMessage());
        }

        if (exchange.getException() != null) {
            Exception exception = exchange.getException();
            exchange.setException(null);
            if (exception instanceof InterruptedException) {
                exchange.removeProperty(Exchange.ROUTE_STOP);
            }
            throw new RuntimeException(exception.getMessage());
        }
        return exchange;
    }

    private Endpoint findEndpoint(String endpointId) {
        return CamelContextHelper.mandatoryLookup(exchange.getContext(), endpointId, Endpoint.class);
    }
}
