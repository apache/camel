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
package org.apache.camel.language.groovy;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

/**
 * CAMEL-27150: new JsonSlurper().parseText(body) reads a body of bytes or a stream, as well as text: the byte[] a JMS
 * consumer gets for a BytesMessage failed with "No signature of method: parseText ... ([B)".
 */
public class GroovyJsonSlurperParseTextTest extends CamelTestSupport {

    private static final String ORDER = "{\"orderId\": \"ORD-1001\", \"lines\": [{\"sku\": \"CAMEL-MUG\", \"qty\": 1}]}";

    @Test
    public void testParseTextOfTextBytesAndStream() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("ORD-1001 has 1 line(s)", "ORD-1001 has 1 line(s)", "ORD-1001 has 1 line(s)");

        template.sendBody("direct:start", ORDER);
        template.sendBody("direct:start", ORDER.getBytes(StandardCharsets.UTF_8));
        template.sendBody("direct:start", new ByteArrayInputStream(ORDER.getBytes(StandardCharsets.UTF_8)));

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .setBody().groovy("def order = new groovy.json.JsonSlurper().parseText(body); "
                                          + "\"${order.orderId} has ${order.lines.size()} line(s)\"")
                        .to("mock:result");
            }
        };
    }
}
