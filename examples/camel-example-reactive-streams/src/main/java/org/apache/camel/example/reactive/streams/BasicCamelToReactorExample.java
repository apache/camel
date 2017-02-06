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
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.reactive.streams.api.CamelReactiveStreamsService;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Flux;

/**
 * This example shows how a reactive stream framework can subscribe to events published
 * by Camel routes.
 *
 * The exchange pattern is in-only from Camel to Reactor.
 *
 * Note: the Camel and reactor components are placed in the same configuration class for the sake of clarity,
 * but they can be moved in their own files.
 */
@Configuration
@ConditionalOnProperty("examples.basic.camel-to-reactor")
public class BasicCamelToReactorExample {

    /**
     * The reactor streams.
     */
    @Component
    public static class BasicCamelToReactorExampleStreams {
        private static final Logger LOG = LoggerFactory.getLogger(BasicCamelToReactorExample.class);

        @Autowired
        private CamelReactiveStreamsService camel;


        @PostConstruct
        public void setupStreams() {

            // Use two streams from Camel
            Publisher<Integer> numbers = camel.fromStream("numbers", Integer.class);
            Publisher<String> strings = camel.fromStream("strings", String.class);

            Flux.from(numbers)
                    .zipWith(strings) // emit items in pairs
                    .map(tuple -> "BasicCamelToReactor - " + tuple.getT1() + " -> " + tuple.getT2())
                    .doOnNext(LOG::info)
                    .subscribe();
        }

    }


    /**
     * The Camel Configuration.
     */
    @Component
    public static class BasicCamelToReactorExampleRoutes extends RouteBuilder {

        @Override
        public void configure() throws Exception {

            // Generating numbers every 5 seconds and forwarding to the stream "numbers"
            from("timer:clock?period=5000")
                    .setBody().header(Exchange.TIMER_COUNTER)
                    .to("reactive-streams:numbers");

            // Generating strings every 4.9 seconds and forwarding to the stream "strings"
            from("timer:clock2?period=4900&delay=2000")
                    .setBody().simple("Hello World ${header.CamelTimerCounter}!")
                    .to("reactive-streams:strings");

        }

    }

}
