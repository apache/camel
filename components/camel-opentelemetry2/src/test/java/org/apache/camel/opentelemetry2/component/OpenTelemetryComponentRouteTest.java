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
package org.apache.camel.opentelemetry2.component;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

public class OpenTelemetryComponentRouteTest extends CamelTestSupport {

    @Produce("direct:start1")
    protected ProducerTemplate template1;

    @Test
    public void testMetrics() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);

        template1.sendBody(new Object());
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testMessageContentDelivery() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        String body = "Message Body";
        String header1 = "Header 1";
        String header2 = "Header 2";
        Object value1 = new Date();
        Object value2 = System.currentTimeMillis();
        mock.expectedBodiesReceived(body);
        mock.expectedHeaderReceived(header1, value1);
        mock.expectedHeaderReceived(header2, value2);
        Map<String, Object> headers = new HashMap<>();
        headers.put(header1, value1);
        headers.put(header2, value2);

        template1.sendBodyAndHeaders(body, headers);
        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start1")
                        .to("opentelemetry2:timer:T?action=start")
                        .to("opentelemetry2:counter://A")
                        .to("opentelemetry2:counter://B")
                        .to("opentelemetry2:counter:C?increment=19291")
                        .to("opentelemetry2:counter:C?decrement=19292")
                        .to("opentelemetry2:counter:C")
                        .to("opentelemetry2:summary:E")
                        .to("opentelemetry2:timer:T")
                        .to("opentelemetry2:summary:E?value=12000000031")
                        .to("opentelemetry2:timer:T?action=stop")
                        .to("mock:result");
            }
        };
    }
}
