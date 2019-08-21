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
import org.apache.camel.ValidationException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class ValidatorXmlSchemaTest extends ContextTestSupport {

    @Test
    public void shouldPass() throws Exception {
        final String body = "<user><name>Jan</name></user>";
        MockEndpoint mock = getMockEndpoint("mock:result");

        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived(body);

        template.sendBody("direct:in", body);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void shouldThrowException() throws Exception {
        final String body = "<fail/>";
        MockEndpoint mock = getMockEndpoint("mock:result");

        mock.expectedMessageCount(0);
        try {
            template.sendBody("direct:in", body);
            fail("Should throw exception");
        } catch (CamelExecutionException e) {
            assertIsInstanceOf(ValidationException.class, e.getCause());
        }

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                validator().type("xml").withUri("validator:org/apache/camel/impl/validate.xsd");

                from("direct:in").inputTypeWithValidate("xml").to("mock:result");
            }
        };
    }

}
