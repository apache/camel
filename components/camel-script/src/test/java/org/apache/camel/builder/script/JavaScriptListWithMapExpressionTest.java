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
package org.apache.camel.builder.script;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

/**
 * Tests cast routing expression using JavaScript
 */
public class JavaScriptListWithMapExpressionTest extends CamelTestSupport {

    @EndpointInject(uri = "mock:result")
    MockEndpoint resultEndpoint;

    private static final String HEADER_NAME = "myHeader";

    @Test
    public void testSendMatchingMessage() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() throws Exception {
                from("direct:start").setHeader(HEADER_NAME).javaScript("[ {\"firstField\": \"firstValue\", \"secondField\":\"secondValue\"} ]").to(resultEndpoint);
            }
        });

        template.sendBody("direct:start", "BODY");
        
        assertNotEquals(resultEndpoint.getExchanges().size(), 0);

        Object header = resultEndpoint.getExchanges().get(0).getIn().getHeader(HEADER_NAME);

        log.info("Check header instance: {}", header.getClass().getCanonicalName());
        
        assertTrue(header instanceof List);
        assertNotEquals(((List)header).size(), 0);
        assertTrue(((List)header).get(0) instanceof Map);

        assertMockEndpointsSatisfied(10, TimeUnit.SECONDS);
    }
}
