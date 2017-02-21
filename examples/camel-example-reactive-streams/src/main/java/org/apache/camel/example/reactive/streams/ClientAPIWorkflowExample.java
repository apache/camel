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

import java.io.InputStream;
import javax.annotation.PostConstruct;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.reactive.streams.api.CamelReactiveStreamsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Flux;

/**
 * This example shows how to define a complex workflow using Camel direct client API.
 *
 * Note: the code is not spring-boot related and could have been placed in a standalone main().
 */
@Configuration
@ConditionalOnProperty("examples.client-api.workflow")
public class ClientAPIWorkflowExample {

    /**
     * The reactor streams.
     */
    @Component
    public static class ClientAPIWorkflowExampleStreams {

        @Autowired
        private CamelReactiveStreamsService camel;

        @PostConstruct
        public void setup() {

            /**
             * This workflow reads all files from the directory named "input",
             * marshals them using the Camel marshalling features (simulation)
             * and sends them to an external system (simulation)
             * only if they contain the word "camel".
             */
            Flux.from(camel.from("file:input", InputStream.class))
                    .flatMap(camel.to("direct:unmarshal", String.class))
                    .filter(text -> text.contains("camel"))
                    .flatMap(camel.to("direct:send", String.class))
                    .subscribe();

        }

    }

    /**
     * The Camel Configuration.
     */
    @Component
    public static class BasicReactorToCamelExampleRoutes extends RouteBuilder {

        @Override
        public void configure() throws Exception {

            from("direct:unmarshal")
                    // This can be far more complex, using marshal()
                    .convertBodyTo(String.class)
                    .log("Content marshalled to string: ${body}");

            from("direct:send")
                    .log("Sending the file to an external system (simulation)");

        }

    }

}
