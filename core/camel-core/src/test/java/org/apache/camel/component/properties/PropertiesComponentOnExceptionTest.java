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
package org.apache.camel.component.properties;

import java.util.Properties;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.Registry;
import org.junit.Test;

public class PropertiesComponentOnExceptionTest extends ContextTestSupport {

    @Test
    public void testPropertiesComponentOnException() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:dead");
        mock.expectedMessageCount(1);
        mock.message(0).header(Exchange.REDELIVERED).isEqualTo(true);
        mock.message(0).header(Exchange.REDELIVERY_COUNTER).isEqualTo(3);

        try {
            template.sendBody("direct:start", "Hello World");
            fail("Should throw exception");
        } catch (Exception e) {
            // expected
        }

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                onException(Exception.class).redeliveryDelay("{{delay}}").maximumRedeliveries("{{max}}").to("mock:dead");

                from("direct:start").throwException(new IllegalAccessException("Damn"));
            }
        };
    }

    @Override
    protected Registry createRegistry() throws Exception {
        Registry jndi = super.createRegistry();

        Properties cool = new Properties();
        cool.put("delay", "25");
        cool.put("max", "3");
        jndi.bind("myprop", cool);

        return jndi;
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getPropertiesComponent().setLocation("ref:myprop");
        return context;
    }

}
