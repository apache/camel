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
package org.apache.camel.component.drill;

import java.util.concurrent.TimeUnit;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Ignore;
import org.junit.Test;

@Ignore("CAMEL-10327: Set host, mode and query to test drill producer (direct connection mode).")
public class ProducerTest extends CamelTestSupport {

    private final String host = "localhost";
    private final DrillConnectionMode mode = DrillConnectionMode.DRILLBIT;
    private final Integer port = 31010; // default drillbit port
    private final String query = "select * from query";

    @Test
    public void testProducer() throws Exception {
        template.sendBody("direct:in", "");

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);

        assertMockEndpointsSatisfied(5, TimeUnit.SECONDS);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("direct:in").setHeader(DrillConstants.DRILL_QUERY, constant(query)).to("drill://" + host + "?mode=" + mode.name() + "&port=" + port).log("${body}")
                    .to("mock:result");
            }
        };
    }
}
