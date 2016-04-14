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
import org.apache.camel.ExchangePattern;
import org.apache.camel.Producer;
import org.apache.camel.ProducerCallback;
import org.apache.camel.impl.ProducerCache;

public class CamelHystrixCommand extends HystrixCommand<Exchange> {
    private final Exchange exchange;
    private final String cacheKey;
    private final Endpoint runEndpoint;
    private final Endpoint fallbackEndpoint;
    private final ProducerCache producerCache;

    protected CamelHystrixCommand(Setter setter, Exchange exchange, String cacheKey, ProducerCache producerCache, Endpoint runEndpoint, Endpoint fallbackEndpoint) {
        super(setter);
        this.exchange = exchange;
        this.cacheKey = cacheKey;
        this.producerCache = producerCache;
        this.runEndpoint = runEndpoint;
        this.fallbackEndpoint = fallbackEndpoint;
    }

    @Override
    protected String getCacheKey() {
        return cacheKey;
    }

    @Override
    protected Exchange getFallback() {
        if (fallbackEndpoint == null) {
            return exchange;
        }
        try {
            if (exchange.getException() != null) {
                Exception exception = exchange.getException();
                exchange.setException(null);
                if (exception instanceof InterruptedException) {
                    exchange.removeProperty(Exchange.ROUTE_STOP);
                }
            }
            producerCache.doInProducer(fallbackEndpoint, exchange, exchange.getPattern(), new ProducerCallback<Exchange>() {
                @Override
                public Exchange doInProducer(Producer producer, Exchange exchange, ExchangePattern exchangePattern) throws Exception {
                    try {
                        producer.process(exchange);
                    } catch (Exception e) {
                        exchange.setException(e);
                    }
                    return exchange;
                }
            });
        } catch (Exception e) {
            exchange.setException(e);
        }
        return exchange;
    }

    @Override
    protected Exchange run() {
        try {
            producerCache.doInProducer(runEndpoint, exchange, exchange.getPattern(), new ProducerCallback<Exchange>() {
                @Override
                public Exchange doInProducer(Producer producer, Exchange exchange, ExchangePattern exchangePattern) throws Exception {
                    try {
                        producer.process(exchange);
                    } catch (Exception e) {
                        exchange.setException(e);
                    }
                    return exchange;
                }
            });
        } catch (Exception e) {
            exchange.setException(e);
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

}
