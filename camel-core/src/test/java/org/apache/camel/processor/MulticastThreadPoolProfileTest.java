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
package org.apache.camel.processor;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.builder.ThreadPoolProfileBuilder;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.apache.camel.spi.ThreadPoolProfile;

/**
 * @version 
 */
public class MulticastThreadPoolProfileTest extends MulticastParallelTest {

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // register thread pool profile
                ThreadPoolProfile profile = new ThreadPoolProfileBuilder("myProfile").poolSize(5).maxPoolSize(10).maxQueueSize(20).build();
                context.getExecutorServiceManager().registerThreadPoolProfile(profile);

                from("direct:start")
                    .multicast(new AggregationStrategy() {
                            public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
                                if (oldExchange == null) {
                                    return newExchange;
                                }

                                String body = oldExchange.getIn().getBody(String.class);
                                oldExchange.getIn().setBody(body + newExchange.getIn().getBody(String.class));
                                return oldExchange;
                            }
                        })
                        // and refer to the profile here
                        .parallelProcessing().executorServiceRef("myProfile").to("direct:a", "direct:b")
                    // use end to indicate end of multicast route
                    .end()
                    .to("mock:result");

                from("direct:a").delay(100).setBody(constant("A"));

                from("direct:b").setBody(constant("B"));
            }
        };
    }
}
