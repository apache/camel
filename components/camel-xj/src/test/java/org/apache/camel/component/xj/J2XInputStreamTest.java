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

package org.apache.camel.component.xj;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

public class J2XInputStreamTest extends CamelTestSupport {

    @Test
    public void testOutput() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("<?xml version=\"1.0\" encoding=\"UTF-8\"?><hello>world!</hello>");
        mock.message(0).body().isInstanceOf(String.class);

        template.sendBody("direct:start", new ByteArrayInputStream("{\"hello\": \"world!\"}".getBytes(StandardCharsets.UTF_8)));

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testOutputSourceHeader() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:sourceHeader");
        mock.expectedBodiesReceived("<?xml version=\"1.0\" encoding=\"UTF-8\"?><hello>world!</hello>");
        mock.message(0).body().isInstanceOf(String.class);

        ByteArrayInputStream body = new ByteArrayInputStream("{\"hello\": \"world!\"}".getBytes(StandardCharsets.UTF_8));

        template.send("direct:sourceHeader", exchange -> {
            exchange.getIn().setHeader("xmlSource", body);
        });

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testOutputSourceVariable() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:sourceVariable");
        mock.expectedBodiesReceived("<?xml version=\"1.0\" encoding=\"UTF-8\"?><hello>world!</hello>");
        mock.message(0).body().isInstanceOf(String.class);

        ByteArrayInputStream body = new ByteArrayInputStream("{\"hello\": \"world!\"}".getBytes(StandardCharsets.UTF_8));

        template.send("direct:sourceVariable", exchange -> {
            exchange.setVariable("xmlSource", body);
        });

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testOutputSourceProperty() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:sourceProperty");
        mock.expectedBodiesReceived("<?xml version=\"1.0\" encoding=\"UTF-8\"?><hello>world!</hello>");
        mock.message(0).body().isInstanceOf(String.class);

        ByteArrayInputStream body = new ByteArrayInputStream("{\"hello\": \"world!\"}".getBytes(StandardCharsets.UTF_8));

        template.send("direct:sourceProperty", exchange -> {
            exchange.setProperty("xmlSource", body);
        });

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .to("xj:hellojson2xml.xsl?transformDirection=JSON2XML")
                        .to("mock:result");

                from("direct:sourceHeader")
                        .to("xj:hellojson2xml.xsl?source=header:xmlSource&transformDirection=JSON2XML")
                        .to("mock:sourceHeader");

                from("direct:sourceVariable")
                        .to("xj:hellojson2xml.xsl?source=variable:xmlSource&transformDirection=JSON2XML")
                        .to("mock:sourceVariable");

                from("direct:sourceProperty")
                        .to("xj:hellojson2xml.xsl?source=property:xmlSource&transformDirection=JSON2XML")
                        .to("mock:sourceProperty");
            }
        };
    }
}
