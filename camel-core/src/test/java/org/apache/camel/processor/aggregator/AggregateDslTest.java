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
package org.apache.camel.processor.aggregator;

import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

public class AggregateDslTest extends ContextTestSupport {

    public void testAggregate() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:aggregated");
        mock.expectedBodiesReceived("0,3,6", "1,4,7", "2,5,8");

        for (int i = 0; i < 9; i++) {
            template.sendBodyAndHeader("direct:start", i, "type", i % 3);
        }

        mock.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .aggregate()
                        .message(m -> m.getHeader("type"))
                        .strategy()
                            .body(String.class, (o, n) ->  Stream.of(o, n).filter(Objects::nonNull).collect(Collectors.joining(",")))
                        .completion()
                            .body(String.class, s -> s.length() == 5)
                                    .to("mock:aggregated");
            }
        };
    }
}

