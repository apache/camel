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
package org.apache.camel.impl.validator;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Message;
import org.apache.camel.ValidationException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.DataType;
import org.apache.camel.spi.Registry;
import org.apache.camel.spi.Validator;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BeanValidatorOutputValidateTest extends ContextTestSupport {

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                validator().type("toValidate").withBean("testValidator");

                onException(ValidationException.class).handled(true).log("Invalid validation: ${exception.message}").to("mock:invalid");

                from("direct:in").outputTypeWithValidate("toValidate").to("mock:out");
            }
        };
    }

    public static class TestValidator extends Validator {
        private static final Logger LOG = LoggerFactory.getLogger(TestValidator.class);

        @Override
        public void validate(Message message, DataType type) throws ValidationException {
            Object body = message.getBody();
            LOG.info("Validating : [{}]", body);
            if (body instanceof String && body.equals("valid")) {
                LOG.info("OK");
            } else {
                throw new ValidationException(message.getExchange(), "Wrong content");
            }
        }
    }

    @Override
    protected Registry createRegistry() throws Exception {
        Registry registry = super.createRegistry();

        registry.bind("testValidator", new TestValidator());

        return registry;
    }

    @Test
    public void testValid() throws InterruptedException {
        getMockEndpoint("mock:out").expectedMessageCount(1);
        getMockEndpoint("mock:invalid").expectedMessageCount(0);

        template.sendBody("direct:in", "valid");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testInvalid() throws InterruptedException {
        getMockEndpoint("mock:out").expectedMessageCount(1);
        getMockEndpoint("mock:invalid").expectedMessageCount(0);

        try {
            template.sendBody("direct:in", "wrong");
            fail("Should have thrown exception");
        } catch (CamelExecutionException e) {
            assertIsInstanceOf(ValidationException.class, e.getCause());
            assertTrue(e.getCause().getMessage().startsWith("Wrong content"));
        }

        assertMockEndpointsSatisfied();
    }
}
