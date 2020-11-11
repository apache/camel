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
package org.apache.camel.component.velocity;

import java.util.Map.Entry;
import java.util.Set;

import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.apache.camel.test.junit5.TestSupport.assertMessageBodyEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VelocityTemplateInHeaderTest extends CamelTestSupport {

    @Test
    public void testReceivesFooResponse() throws Exception {
        assertRespondsWith("cheese", "foo", "<hello>foo</hello>");
    }

    @Test
    public void testReceivesBarResponse() throws Exception {
        assertRespondsWith("cheese", "bar", "<hello>bar</hello>");
    }

    @Test
    public void testRespectHeaderNamesUpperCase() throws Exception {
        assertRespondsWith("Cheese", "bar", "<hello>bar</hello>");
    }

    @Test
    public void testRespectHeaderNamesCamelCase() throws Exception {
        assertRespondsWith("CorrelationID", "bar", "<hello>bar</hello>");
    }

    protected void assertRespondsWith(final String headerName, final String headerValue, String expectedBody)
            throws InvalidPayloadException {
        Exchange response = template.request("direct:a", new Processor() {
            public void process(Exchange exchange) throws Exception {
                Message in = exchange.getIn();
                in.setHeader(VelocityConstants.VELOCITY_TEMPLATE, "<hello>${headers." + headerName + "}</hello>");
                in.setHeader(headerName, headerValue);
            }
        });
        assertMessageBodyEquals(response, expectedBody);

        Object template = response.getMessage().getHeader(VelocityConstants.VELOCITY_TEMPLATE);
        assertNull(template, "Template header should have been removed");

        Set<Entry<String, Object>> entrySet = response.getMessage().getHeaders().entrySet();
        boolean keyFound = false;
        for (Entry<String, Object> entry : entrySet) {
            if (entry.getKey().equals(headerName)) {
                keyFound = true;
            }
        }
        assertTrue(keyFound, "Header should been found");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                VelocityComponent vc = context.getComponent("velocity", VelocityComponent.class);
                vc.setAllowTemplateFromHeader(true);

                from("direct:a").to("velocity://dummy");
            }
        };
    }

}
