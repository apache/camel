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
package org.apache.camel.component.bean;

import org.w3c.dom.Document;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * @version 
 */
public class BeanNoTypeConvertionPossibleWhenHeaderTest extends ContextTestSupport {

    public void testBeanHeaderNoTypeConvertionPossibleFail() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);

        // we send in a bar string as header which cannot be converted to a number so it should fail
        try {
            template.requestBodyAndHeader("direct:start", "Hello World", "foo", 555);
            fail("Should have thrown an exception");
        } catch (CamelExecutionException e) {
            ParameterBindingException pbe = assertIsInstanceOf(ParameterBindingException.class, e.getCause());
            assertEquals(1, pbe.getIndex());
            assertTrue(pbe.getMethod().getName().contains("hello"));
            assertEquals(555, pbe.getParameterValue());

            NoTypeConversionAvailableException ntae = assertIsInstanceOf(NoTypeConversionAvailableException.class, e.getCause().getCause());
            assertEquals(Integer.class, ntae.getFromType());
            assertEquals(Document.class, ntae.getToType());
            assertEquals(555, ntae.getValue());
            assertNotNull(ntae.getMessage());
        }

        assertMockEndpointsSatisfied();
    }

    public void testBeanHeaderNoTypeConvertionPossibleOK() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        template.requestBodyAndHeader("direct:start", "Hello World", "foo", "<?xml version=\"1.0\"?><foo>bar</foo>");

        assertMockEndpointsSatisfied();
    }

    public void testBeanHeaderNoTypeConvertionPossibleOKNullHeader() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived("Hello World");
        mock.message(0).header("foo").isNull();

        template.requestBodyAndHeader("direct:start", "Hello World", "foo", (Object) null);

        assertMockEndpointsSatisfied();
    }

    public void testBeanHeaderNoTypeConvertionPossibleOKNoHeader() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived("Hello World");
        mock.message(0).header("foo").isNull();

        template.requestBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").bean(BeanWithHeaderAnnotation.class).to("mock:result");
            }
        };
    }
}