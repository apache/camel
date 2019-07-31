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
package org.apache.camel.jaxb;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.converter.jaxb.JaxbDataFormat;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class JaxbDataFormatMustBeJAXBElementTest extends CamelTestSupport {

    @Test
    public void testJaxbMarshalling() throws InterruptedException {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).body().endsWith("<foo><bar>Hello Bar</bar></foo>");

        template.sendBody("direct:start", "<foo><bar>Hello Bar</bar></foo>");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testJaxbMarshalling2() throws InterruptedException {
        getMockEndpoint("mock:result").expectedMessageCount(0);

        try {
            template.sendBody("direct:start2", "<foo><bar>Hello Bar</bar></foo>");
            fail("Should have thrown exception");
        } catch (CamelExecutionException e) {
            InvalidPayloadException ipe = assertIsInstanceOf(InvalidPayloadException.class, e.getCause().getCause());
            assertNotNull(ipe);
            assertEquals(JAXBElement.class, ipe.getType());
        }

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                JaxbDataFormat jaxb = new JaxbDataFormat(JAXBContext.newInstance(Foo.class));
                jaxb.setPrettyPrint(false);
                jaxb.setMustBeJAXBElement(false);

                from("direct:start").marshal(jaxb).to("log:xml", "mock:result");

                JaxbDataFormat jaxb2 = new JaxbDataFormat(JAXBContext.newInstance(Foo.class));
                jaxb2.setPrettyPrint(false);
                jaxb2.setMustBeJAXBElement(true);

                from("direct:start2").marshal(jaxb2).to("log:xml", "mock:result2");
            }
        };
    }

    @XmlRootElement
    public static class Foo {
        private String bar;

        public String getBar() {
            return bar;
        }

        public void setBar(String bar) {
            this.bar = bar;
        }
    }

}
