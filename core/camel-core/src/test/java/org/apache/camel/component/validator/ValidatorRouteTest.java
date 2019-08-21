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
package org.apache.camel.component.validator;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.ValidationException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.processor.validation.NoXmlHeaderValidationException;
import org.junit.Before;
import org.junit.Test;

public class ValidatorRouteTest extends ContextTestSupport {

    protected MockEndpoint validEndpoint;
    protected MockEndpoint finallyEndpoint;
    protected MockEndpoint invalidEndpoint;

    @Test
    public void testValidMessage() throws Exception {
        validEndpoint.expectedMessageCount(1);
        finallyEndpoint.expectedMessageCount(1);

        template.sendBody("direct:start", "<mail xmlns='http://foo.com/bar'><subject>Hey</subject><body>Hello world!</body></mail>");

        MockEndpoint.assertIsSatisfied(validEndpoint, invalidEndpoint, finallyEndpoint);
    }

    @Test
    public void testValidMessageInHeader() throws Exception {
        validEndpoint.expectedMessageCount(1);
        finallyEndpoint.expectedMessageCount(1);

        template.sendBodyAndHeader("direct:startHeaders", null, "headerToValidate", "<mail xmlns='http://foo.com/bar'><subject>Hey</subject><body>Hello world!</body></mail>");

        MockEndpoint.assertIsSatisfied(validEndpoint, invalidEndpoint, finallyEndpoint);
    }

    @Test
    public void testInvalidMessage() throws Exception {
        invalidEndpoint.expectedMessageCount(1);
        finallyEndpoint.expectedMessageCount(1);

        template.sendBody("direct:start", "<mail xmlns='http://foo.com/bar'><body>Hello world!</body></mail>");

        MockEndpoint.assertIsSatisfied(validEndpoint, invalidEndpoint, finallyEndpoint);
    }

    @Test
    public void testInvalidMessageInHeader() throws Exception {
        invalidEndpoint.expectedMessageCount(1);
        finallyEndpoint.expectedMessageCount(1);

        template.sendBodyAndHeader("direct:startHeaders", null, "headerToValidate", "<mail xmlns='http://foo.com/bar'><body>Hello world!</body></mail>");

        MockEndpoint.assertIsSatisfied(validEndpoint, invalidEndpoint, finallyEndpoint);
    }

    @Test
    public void testNullHeaderNoFail() throws Exception {
        validEndpoint.expectedMessageCount(1);

        template.sendBodyAndHeader("direct:startNullHeaderNoFail", null, "headerToValidate", null);

        MockEndpoint.assertIsSatisfied(validEndpoint);
    }

    @Test
    public void testNullHeader() throws Exception {
        validEndpoint.setExpectedMessageCount(0);

        Exchange in = resolveMandatoryEndpoint("direct:startNoHeaderException").createExchange(ExchangePattern.InOut);

        in.getIn().setBody(null);
        in.getIn().setHeader("headerToValidate", null);

        Exchange out = template.send("direct:startNoHeaderException", in);

        MockEndpoint.assertIsSatisfied(validEndpoint, invalidEndpoint, finallyEndpoint);

        Exception exception = out.getException();
        assertTrue("Should be failed", out.isFailed());
        assertTrue("Exception should be correct type", exception instanceof NoXmlHeaderValidationException);
        assertTrue("Exception should mention missing header", exception.getMessage().contains("headerToValidate"));
    }

    @Test
    public void testInvalideBytesMessage() throws Exception {
        invalidEndpoint.expectedMessageCount(1);
        finallyEndpoint.expectedMessageCount(1);

        template.sendBody("direct:start", "<mail xmlns='http://foo.com/bar'><body>Hello world!</body></mail>".getBytes());

        MockEndpoint.assertIsSatisfied(validEndpoint, invalidEndpoint, finallyEndpoint);
    }

    @Test
    public void testInvalidBytesMessageInHeader() throws Exception {
        invalidEndpoint.expectedMessageCount(1);
        finallyEndpoint.expectedMessageCount(1);

        template.sendBodyAndHeader("direct:startHeaders", null, "headerToValidate", "<mail xmlns='http://foo.com/bar'><body>Hello world!</body></mail>".getBytes());

        MockEndpoint.assertIsSatisfied(validEndpoint, invalidEndpoint, finallyEndpoint);
    }

    @Test
    public void testUseNotASharedSchema() throws Exception {
        validEndpoint.expectedMessageCount(1);

        template.sendBody("direct:useNotASharedSchema", "<mail xmlns='http://foo.com/bar'><subject>Hey</subject><body>Hello world!</body></mail>");

        MockEndpoint.assertIsSatisfied(validEndpoint, invalidEndpoint, finallyEndpoint);
    }

    @Test
    public void testConcurrentUseNotASharedSchema() throws Exception {
        validEndpoint.expectedMessageCount(10);
        // latch for the 10 exchanges we expect
        final CountDownLatch latch = new CountDownLatch(10);
        // setup a task executor to be able send the messages in parallel
        ExecutorService executor = Executors.newCachedThreadPool();
        for (int i = 0; i < 10; i++) {
            executor.execute(new Runnable() {
                public void run() {
                    template.requestBody("direct:useNotASharedSchema", "<mail xmlns='http://foo.com/bar'><subject>Hey</subject><body>Hello world!</body></mail>");
                    latch.countDown();
                }
            });
        }

        try {
            // wait for test completion, timeout after 30 sec to let other unit
            // test run to not wait forever
            assertTrue(latch.await(30000L, TimeUnit.MILLISECONDS));
            assertEquals("Latch should be zero", 0, latch.getCount());
        } finally {
            executor.shutdown();
        }
        MockEndpoint.assertIsSatisfied(validEndpoint, invalidEndpoint, finallyEndpoint);
    }

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();

        validEndpoint = resolveMandatoryEndpoint("mock:valid", MockEndpoint.class);
        invalidEndpoint = resolveMandatoryEndpoint("mock:invalid", MockEndpoint.class);
        finallyEndpoint = resolveMandatoryEndpoint("mock:finally", MockEndpoint.class);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").doTry().to("validator:org/apache/camel/component/validator/schema.xsd").to("mock:valid").doCatch(ValidationException.class).to("mock:invalid")
                    .doFinally().to("mock:finally").end();

                from("direct:startHeaders").doTry().to("validator:org/apache/camel/component/validator/schema.xsd?headerName=headerToValidate").to("mock:valid")
                    .doCatch(ValidationException.class).to("mock:invalid").doFinally().to("mock:finally").end();

                from("direct:startNoHeaderException").to("validator:org/apache/camel/component/validator/schema.xsd?headerName=headerToValidate").to("mock:valid");

                from("direct:startNullHeaderNoFail").to("validator:org/apache/camel/component/validator/schema.xsd?headerName=headerToValidate&failOnNullHeader=false")
                    .to("mock:valid");

                from("direct:useNotASharedSchema").to("validator:org/apache/camel/component/validator/schema.xsd?useSharedSchema=false").to("mock:valid");
            }
        };
    }

}
