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
package org.apache.camel.language.datasonnet;

import com.datasonnet.document.MediaTypes;
import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;

class DataWeaveAutoDetectTest extends CamelTestSupport {

    @Test
    void testDataWeaveResourceAutoDetect() throws Exception {
        template.sendBody("direct:dwlResource", "{\"name\": \"Camel\"}");
        MockEndpoint mock = getMockEndpoint("mock:result");
        Exchange exchange = mock.assertExchangeReceived(0);
        String response = exchange.getMessage().getBody(String.class);
        JSONAssert.assertEquals("{\"name\": \"Camel\", \"greeting\": \"Hello, Camel\"}", response, true);
    }

    @Test
    void testDataWeaveInlineAutoDetect() throws Exception {
        template.sendBody("direct:dwlInline", "{\"name\": \"Camel\"}");
        MockEndpoint mock = getMockEndpoint("mock:result");
        Exchange exchange = mock.assertExchangeReceived(0);
        String response = exchange.getMessage().getBody(String.class);
        JSONAssert.assertEquals("{\"greeting\": \"Hello, Camel\"}", response, true);
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:dwlResource")
                        .transform(datasonnet("resource:classpath:simpleMapping.dwl", String.class,
                                MediaTypes.APPLICATION_JSON_VALUE, MediaTypes.APPLICATION_JSON_VALUE))
                        .to("mock:result");

                from("direct:dwlInline")
                        .transform(datasonnet(
                                "%dw 2.0\noutput application/json\n---\n{ greeting: \"Hello, \" ++ payload.name }",
                                String.class,
                                MediaTypes.APPLICATION_JSON_VALUE, MediaTypes.APPLICATION_JSON_VALUE))
                        .to("mock:result");
            }
        };
    }
}
