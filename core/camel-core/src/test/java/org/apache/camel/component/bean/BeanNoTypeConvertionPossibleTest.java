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
package org.apache.camel.component.bean;

import java.util.Date;

import org.w3c.dom.Document;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class BeanNoTypeConvertionPossibleTest extends ContextTestSupport {

    @Test
    public void testBeanNoTypeConvertionPossibleFail() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);

        // we send in a Date object which cannot be converted to XML so it
        // should fail
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.requestBody("direct:start", new Date()),
                "Should have thrown an exception");

        NoTypeConversionAvailableException ntae
                = assertIsInstanceOf(NoTypeConversionAvailableException.class, e.getCause().getCause());
        assertEquals(Date.class, ntae.getFromType());
        assertEquals(Document.class, ntae.getToType());
        assertNotNull(ntae.getValue());
        assertNotNull(ntae.getMessage());

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testBeanNoTypeConvertionPossibleOK() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("77889,667,457");

        template.requestBody("direct:start", "<foo>bar</foo>");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testBeanNoTypeConvertionPossibleOKNullBody() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).body().isNull();

        String body = null;
        template.requestBody("direct:start", body);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                OrderServiceBean bean = new OrderServiceBean();
                bean.setConverter(context.getTypeConverter());
                from("direct:start").bean(bean, "handleXML").to("mock:result");
            }
        };
    }
}
