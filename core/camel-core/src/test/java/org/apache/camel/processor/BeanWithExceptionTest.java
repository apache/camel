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
package org.apache.camel.processor;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangeProperty;
import org.apache.camel.Header;
import org.apache.camel.Processor;
import org.apache.camel.ValidationException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.Registry;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BeanWithExceptionTest extends ContextTestSupport {
    protected MockEndpoint validEndpoint;
    protected MockEndpoint invalidEndpoint;

    @Test
    public void testValidMessage() throws Exception {
        validEndpoint.expectedMessageCount(1);
        invalidEndpoint.expectedMessageCount(0);

        template.send("direct:start", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody("<valid/>");
                exchange.getIn().setHeader("foo", "bar");
                exchange.setProperty("cheese", "old");
            }
        });

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testInvalidMessage() throws Exception {
        validEndpoint.expectedMessageCount(0);
        invalidEndpoint.expectedMessageCount(1);

        Exchange exchange = template.send("direct:start", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody("<invalid/>");
                exchange.getIn().setHeader("foo", "notMatchedHeaderValue");
                exchange.setProperty("cheese", "old");
            }
        });

        assertNotNull(exchange.getException());
        ValidationException exception = assertIsInstanceOf(ValidationException.class, exchange.getException());
        assertEquals("Invalid header foo: notMatchedHeaderValue", exception.getMessage());

        assertMockEndpointsSatisfied();
    }

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();

        validEndpoint = resolveMandatoryEndpoint("mock:valid", MockEndpoint.class);
        invalidEndpoint = resolveMandatoryEndpoint("mock:invalid", MockEndpoint.class);
    }

    @Override
    protected Registry createRegistry() throws Exception {
        Registry answer = super.createRegistry();
        answer.bind("myBean", new ValidationBean());
        return answer;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                onException(ValidationException.class).to("mock:invalid");

                from("direct:start").bean("myBean").to("mock:valid");
            }
        };
    }

    public static class ValidationBean {
        private static final Logger LOG = LoggerFactory.getLogger(ValidationBean.class);

        public void someMethod(String body, @Header("foo") String header, @ExchangeProperty("cheese") String cheese) throws ValidationException {
            assertEquals("old", cheese);

            if ("bar".equals(header)) {
                LOG.info("someMethod() called with valid header and body: " + body);
            } else {
                throw new ValidationException(null, "Invalid header foo: " + header);
            }
        }
    }
}
