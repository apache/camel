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
package org.apache.camel.component.kamelet;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.AggregationStrategies;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

public class KameletEipAggregateTest extends CamelTestSupport {

    @Test
    public void testAggregate() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("A,B,C,D,E");

        template.sendBody("direct:start", "A");
        template.sendBody("direct:start", "B");
        template.sendBody("direct:start", "C");
        template.sendBody("direct:start", "D");
        template.sendBody("direct:start", "E");

        assertMockEndpointsSatisfied();
    }

    // **********************************************
    //
    // test set-up
    //
    // **********************************************

    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                routeTemplate("my-aggregate")
                        .templateParameter("count")
                        .from("kamelet:source")
                        .aggregate(constant(true))
                            .completionSize("{{count}}")
                            .aggregationStrategy(AggregationStrategies.string(","))
                            .to("log:aggregate")
                            .to("kamelet:sink")
                        .end();

                from("direct:start")
                        .kamelet("my-aggregate?count=5")
                        .to("log:info")
                        .to("mock:result");
            }
        };
    }
}
