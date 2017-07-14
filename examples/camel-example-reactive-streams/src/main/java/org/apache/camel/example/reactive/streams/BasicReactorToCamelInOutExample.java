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

import java.time.Duration;
import javax.annotation.PostConstruct;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.reactive.streams.api.CamelReactiveStreamsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Flux;

/**
 * This example shows how a reactive stream framework can asynchronously request data to a Camel stream
 * and continue processing.
 *
 * The exchange pattern is in-out from Reactor to Camel.
 *
 * Note: the Camel and reactor components are placed in the same configuration class for the sake of clarity,
 * but they can be moved in their own files.
 */
@Configuration
@ConditionalOnProperty("examples.basic.reactor-to-camel-in-out")
public class BasicReactorToCamelInOutExample {

    /**
     * The reactor streams.
     */
    @Component
    public static class BasicReactorToCamelInOutExampleStreams {
        private static final Logger LOG = LoggerFactory.getLogger(BasicReactorToCamelInOutExample.class);

        @Autowired
        private CamelReactiveStreamsService camel;


        @PostConstruct
        public void setupStreams() {

            Flux.interval(Duration.ofSeconds(8))
                    .map(i -> i + 1) // to start from 1
                    .flatMap(camel.toStream("sqrt", Double.class)) // call Camel and continue
                    .map(d -> "BasicReactorToCamelInOut - sqrt=" + d)
                    .doOnNext(LOG::info)
                    .subscribe();

        }
    }


    /**
     * The Camel Configuration.
     */
    @Component
    public static class BasicReactorToCamelInOutExampleRoutes extends RouteBuilder {

        @Override
        public void configure() throws Exception {

            // Transform the body of every exchange into its square root
            from("reactive-streams:sqrt")
                    .setBody().body(Double.class, Math::sqrt);

            // This route can be much more complex: it can use any Camel component to compute the output data

        }

    }

}
