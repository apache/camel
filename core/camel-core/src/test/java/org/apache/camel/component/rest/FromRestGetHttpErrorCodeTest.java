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
package org.apache.camel.component.rest;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.Registry;
import org.junit.Test;

public class FromRestGetHttpErrorCodeTest extends ContextTestSupport {

    @Override
    protected Registry createRegistry() throws Exception {
        Registry jndi = super.createRegistry();
        jndi.bind("dummy-rest", new DummyRestConsumerFactory());
        return jndi;
    }

    @Test
    public void testFromRestModel() throws Exception {
        String out = template.requestBody("seda:get-say-bye", "I was here", String.class);
        assertEquals("Bye World", out);

        Exchange reply = template.request("seda:get-say-bye", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody("Kaboom");
            }
        });
        assertNotNull(reply);
        assertEquals(404, reply.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertEquals("text/plain", reply.getMessage().getHeader(Exchange.CONTENT_TYPE));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                restConfiguration().host("localhost");
                rest("/say/bye").get().route().choice().when(body().contains("Kaboom")).setHeader(Exchange.HTTP_RESPONSE_CODE, constant(404))
                    .setHeader(Exchange.CONTENT_TYPE, constant("text/plain")).setBody(constant("The data is invalid")).otherwise().transform().constant("Bye World");
            }
        };
    }
}
