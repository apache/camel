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
package org.apache.camel.component.undertow.rest;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.undertow.BaseUndertowTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RestUndertowProducerEncodingTest extends BaseUndertowTest {

    @Test
    public void testSelect() {
        template.sendBody("rest:get:bw-web-api/v1/objects/timesheets?companyId=RD&select=personId,personName", "Hello World");
    }

    @Test
    public void testFilter() {
        template.sendBody("rest:get:bw-web-api/v1/objects/timesheets?companyId=RD&select=personId,personName"
                          + "&filter=date(time/date) ge 2020-06-01 and personId eq 'R10019'",
                "Bye World");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // configure to use undertow on localhost with the given port
                restConfiguration().component("undertow").host("localhost").port(getPort());

                // use the rest DSL to define the rest services
                rest("/bw-web-api/v1/objects")
                        .get("{action}")
                        .to("direct:action");

                from("direct:action")
                        .process(exchange -> {
                            String action = exchange.getIn().getHeader("action", String.class);
                            assertEquals("timesheets", action);
                            String select = exchange.getIn().getHeader("select", String.class);
                            assertEquals("personId,personName", select);
                            String cid = exchange.getIn().getHeader("companyId", String.class);
                            assertEquals("RD", cid);
                            String filter = exchange.getIn().getHeader("filter", String.class);
                            if (filter != null) {
                                assertEquals("date(time/date) ge 2020-06-01 and personId eq 'R10019'", filter);
                            }
                        });
            }
        };
    }

}
