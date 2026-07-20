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
package org.apache.camel.spring.processor;

import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spring.SpringRunWithTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ContextConfiguration
public class SpringTraceTest extends SpringRunWithTestSupport {

    @Autowired
    protected ProducerTemplate camelTemplate;

    @Test
    public void testTracing() throws Exception {
        CamelContext camelContext = camelTemplate.getCamelContext();

        // Verify that tracing is enabled by the Spring XML configuration (trace="true")
        assertEquals(Boolean.TRUE, camelContext.isTracing(), "Tracing should be enabled");
        assertNotNull(camelContext.getTracer(), "Tracer should be available");
        assertTrue(camelContext.getTracer().isEnabled(), "Tracer should be enabled");

        MockEndpoint mock = camelContext.getEndpoint("mock:result", MockEndpoint.class);
        mock.expectedMessageCount(2);
        // The route sets header "someHeader" to "${in.body} World!" — verify the traced route processes correctly
        mock.message(0).header("someHeader").isEqualTo("Hello World!");
        mock.message(1).header("someHeader").isEqualTo("1234 World!");

        camelTemplate.sendBody("Hello");
        camelTemplate.sendBody(1234);

        MockEndpoint.assertIsSatisfied(camelContext, 10, TimeUnit.SECONDS);
    }
}
