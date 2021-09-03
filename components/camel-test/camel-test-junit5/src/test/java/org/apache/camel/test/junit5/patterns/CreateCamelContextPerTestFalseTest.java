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
package org.apache.camel.test.junit5.patterns;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_METHOD)
public class CreateCamelContextPerTestFalseTest extends CamelTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(CreateCamelContextPerTestFalseTest.class);

    private static final AtomicInteger CREATED_CONTEXTS = new AtomicInteger();
    private static final AtomicInteger POST_TEAR_DOWN = new AtomicInteger();

    @EndpointInject("mock:result")
    protected MockEndpoint resultEndpoint;

    @Produce("direct:start")
    protected ProducerTemplate template;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        LOG.info("createCamelContext()");
        CREATED_CONTEXTS.incrementAndGet();
        return super.createCamelContext();
    }

    @Override
    protected void doPostTearDown() throws Exception {
        LOG.info("doPostTearDown()");
        POST_TEAR_DOWN.incrementAndGet();
        super.doPostTearDown();
    }

    @Test
    public void testSendMatchingMessage() throws Exception {
        String expectedBody = "<matched/>";

        resultEndpoint.expectedBodiesReceived(expectedBody);

        template.sendBodyAndHeader(expectedBody, "foo", "bar");

        resultEndpoint.assertIsSatisfied();

        assertTrue(CREATED_CONTEXTS.get() >= 1, "Should create 1 or more CamelContext per test class");
    }

    @Test
    public void testSendAnotherMatchingMessage() throws Exception {
        String expectedBody = "<matched/>";

        resultEndpoint.expectedBodiesReceived(expectedBody);

        template.sendBodyAndHeader(expectedBody, "foo", "bar");

        resultEndpoint.assertIsSatisfied();

        assertTrue(CREATED_CONTEXTS.get() >= 1, "Should create 1 or more CamelContext per test class");
    }

    @Test
    public void testSendNotMatchingMessage() throws Exception {
        resultEndpoint.expectedMessageCount(0);

        template.sendBodyAndHeader("<notMatched/>", "foo", "notMatchedHeaderValue");

        resultEndpoint.assertIsSatisfied();

        assertTrue(CREATED_CONTEXTS.get() >= 1, "Should create 1 or more CamelContext per test class");
    }

    @AfterAll
    public static void validateTearDown() {
        assertEquals(3, CREATED_CONTEXTS.get());
        assertEquals(3, POST_TEAR_DOWN.get());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start").filter(header("foo").isEqualTo("bar")).to("mock:result");
            }
        };
    }
}
