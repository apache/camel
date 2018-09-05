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
package org.apache.camel.component.jsonvalidator;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.ValidationException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class ValidatorRouteTest extends CamelTestSupport {
    
    @EndpointInject(uri = "mock:valid")
    protected MockEndpoint validEndpoint;
    
    @EndpointInject(uri = "mock:finally")
    protected MockEndpoint finallyEndpoint;
    
    @EndpointInject(uri = "mock:invalid")
    protected MockEndpoint invalidEndpoint;

    @Test
    public void testValidMessage() throws Exception {
        validEndpoint.expectedMessageCount(1);
        finallyEndpoint.expectedMessageCount(1);

        template.sendBody("direct:start",
                "{ \"name\": \"Joe Doe\", \"id\": 1, \"price\": 12.5 }");

        MockEndpoint.assertIsSatisfied(validEndpoint, invalidEndpoint, finallyEndpoint);
    }

    @Test
    public void testValidMessageInHeader() throws Exception {
        validEndpoint.expectedMessageCount(1);
        finallyEndpoint.expectedMessageCount(1);

        template.sendBodyAndHeader("direct:startHeaders",
                null,
                "headerToValidate",
                "{ \"name\": \"Joe Doe\", \"id\": 1, \"price\": 12.5 }");

        MockEndpoint.assertIsSatisfied(validEndpoint, invalidEndpoint, finallyEndpoint);
    }

    @Test
    public void testInvalidMessage() throws Exception {
        invalidEndpoint.expectedMessageCount(1);
        finallyEndpoint.expectedMessageCount(1);

        template.sendBody("direct:start",
                "{ \"name\": \"Joe Doe\", \"id\": \"ABC123\", \"price\": 12.5 }");

        MockEndpoint.assertIsSatisfied(validEndpoint, invalidEndpoint, finallyEndpoint);
    }

    @Test
    public void testInvalidMessageInHeader() throws Exception {
        invalidEndpoint.expectedMessageCount(1);
        finallyEndpoint.expectedMessageCount(1);

        template.sendBodyAndHeader("direct:startHeaders",
                null,
                "headerToValidate",
                "{ \"name\": \"Joe Doe\", \"id\": \"ABC123\", \"price\": 12.5 }");

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
        assertTrue("Exception should be correct type", exception instanceof NoJsonHeaderValidationException);
        assertTrue("Exception should mention missing header", exception.getMessage().contains("headerToValidate"));
    }

    @Test
    public void testInvalideBytesMessage() throws Exception {
        invalidEndpoint.expectedMessageCount(1);
        finallyEndpoint.expectedMessageCount(1);

        template.sendBody("direct:start",
                "{ \"name\": \"Joe Doe\", \"id\": \"ABC123\", \"price\": 12.5 }".getBytes());

        MockEndpoint.assertIsSatisfied(validEndpoint, invalidEndpoint, finallyEndpoint);
    }

    @Test
    public void testInvalidBytesMessageInHeader() throws Exception {
        invalidEndpoint.expectedMessageCount(1);
        finallyEndpoint.expectedMessageCount(1);

        template.sendBodyAndHeader("direct:startHeaders",
                null,
                "headerToValidate",
                "{ \"name\": \"Joe Doe\", \"id\": \"ABC123\", \"price\": 12.5 }".getBytes());

        MockEndpoint.assertIsSatisfied(validEndpoint, invalidEndpoint, finallyEndpoint);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .doTry()
                        .to("json-validator:org/apache/camel/component/jsonvalidator/schema.json")
                        .to("mock:valid")
                    .doCatch(ValidationException.class)
                        .to("mock:invalid")
                    .doFinally()
                        .to("mock:finally")
                    .end();

                from("direct:startHeaders")
                    .doTry()
                        .to("json-validator:org/apache/camel/component/jsonvalidator/schema.json?headerName=headerToValidate")
                        .to("mock:valid")
                    .doCatch(ValidationException.class)
                        .to("mock:invalid")
                    .doFinally()
                        .to("mock:finally")
                    .end();

                from("direct:startNoHeaderException")
                        .to("json-validator:org/apache/camel/component/jsonvalidator/schema.json?headerName=headerToValidate")
                        .to("mock:valid");

                from("direct:startNullHeaderNoFail")
                        .to("json-validator:org/apache/camel/component/jsonvalidator/schema.json?headerName=headerToValidate&failOnNullHeader=false")
                        .to("mock:valid");
            }
        };
    }
}
