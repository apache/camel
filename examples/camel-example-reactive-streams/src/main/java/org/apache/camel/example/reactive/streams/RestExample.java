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

import org.apache.camel.Header;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.reactive.streams.util.UnwrapStreamProcessor;
import org.reactivestreams.Publisher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Flux;

/**
 * This example shows how a Camel route defined with rest DSL can call a can asynchronously request data to a reactive stream framework
 * and continue processing.
 *
 * The exchange pattern is in-out from Camel to Reactor.
 *
 * Note: the Camel and reactor components are placed in the same configuration class for the sake of clarity,
 * but they can be moved in their own files.
 */
@Configuration
@ConditionalOnProperty("examples.others.rest")
public class RestExample {

    /**
     * The reactor streams.
     */
    @Component("calculator")
    public static class RestExampleStreams {

        /**
         * This method will be called by a Camel route.
         */
        public Publisher<Long> sum(@Header("num1") Publisher<Long> num1, @Header("num2") Publisher<Long> num2) {
            return Flux.from(num1).zipWith(num2)
                    .map(t -> t.getT1() + t.getT2());
        }

    }


    /**
     * The Camel Configuration.
     */
    @Component
    public static class RestExampleRoutes extends RouteBuilder {

        @Override
        public void configure() throws Exception {

            // The full path should be eg.: http://localhost:8080/camel/sum/23/31
            rest().get("/sum/{num1}/{num2}")
                    .produces("text/plain")
                    .route()
                    .setHeader("num1").simple("headerAs(num1,Long)")
                    .setHeader("num2").simple("headerAs(num2,Long)")
                    .bean("calculator", "sum")
                    .process(new UnwrapStreamProcessor())
                    .setBody().simple("The result is: ${body}");

        }

    }

}
