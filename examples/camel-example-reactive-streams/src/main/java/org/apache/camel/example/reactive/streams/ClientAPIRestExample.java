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
package org.apache.camel.example.reactive.streams;

import javax.annotation.PostConstruct;

import org.apache.camel.Exchange;
import org.apache.camel.component.reactive.streams.api.CamelReactiveStreamsService;
import org.reactivestreams.Publisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Flux;

/**
 * This example shows how to define Camel rest endpoints using the direct client API.
 *
 * Note: the code is not spring-boot related and could have been placed in a standalone main().
 */
@Configuration
@ConditionalOnProperty("examples.client-api.rest")
public class ClientAPIRestExample {

    @Component
    public static class ClientAPIRestExampleStreams {

        @Autowired
        private CamelReactiveStreamsService camel;

        @PostConstruct
        public void setup() {

            // Rest endpoint to retrieve all orders: http://localhost:8080/camel/orders
            camel.process("rest:get:orders", exchange ->
                    Flux.from(exchange)
                            .flatMap(ex -> allOrders()));


            // Rest endpoint to retrieve an order.
            // Try: http://localhost:8080/camel/orders/1
            // Or: http://localhost:8080/camel/orders/xxx
            camel.process("rest:get:orders/{orderId}", exchange ->
                    Flux.from(exchange)
                            .map(ex -> ex.getIn().getHeader("orderId", String.class))
                            .flatMap(this::toOrderInfo)
                            .map(Object.class::cast)
                            .switchIfEmpty(
                                    Flux.from(exchange)
                                            .doOnNext(ex -> ex.getOut().setBody("Not found"))
                                            .doOnNext(ex -> ex.getOut().setHeader(Exchange.HTTP_RESPONSE_CODE, 404))
                            ));

        }

        private Publisher<String> toOrderInfo(String orderId) {
            // Simulate a retrieval from DB
            return allOrders()
                    .filter(o -> o.equals(orderId)) // Ensure the order exists
                    .map("Detailed Info on "::concat) // Add detailed info
                    .next();
        }

        private Flux<String> allOrders() {
            return Flux.just("1", "2");
        }

    }

}
