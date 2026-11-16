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
package org.apache.camel.component.servlet.rest;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.servlet.ServletCamelRouterTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RestServletMapHttpMessageBodyFalseTest extends ServletCamelRouterTestSupport {

    @Test
    void testBodyIsNotMapped() throws Exception {
        WebRequest req = new PostMethodWebRequest(
                contextUrl + "/services/users/123/greet",
                new ByteArrayInputStream("Hello".getBytes(StandardCharsets.UTF_8)), "text/plain");
        WebResponse response = query(req);

        assertEquals(200, response.getResponseCode());
        assertEquals("123:", response.getText());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                restConfiguration().component("servlet").endpointProperty("mapHttpMessageBody", "false");

                rest("/users/").post("{id}/greet").to("direct:greet");

                from("direct:greet").transform(simple("${header.id}:${body}"));
            }
        };
    }
}
