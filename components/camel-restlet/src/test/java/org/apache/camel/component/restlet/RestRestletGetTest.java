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
package org.apache.camel.component.restlet;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.JndiRegistry;
import org.junit.Ignore;
import org.junit.Test;

/**
 * @version 
 */
public class RestRestletGetTest extends RestletTestSupport {
    
    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("myBinding", new DefaultRestletBinding());
        return jndi;
    }

    @Test
    public void testRestletProducerGet() throws Exception {
        String out = template.requestBody("http://localhost:" + portNum + "/users/123/basic", null, String.class);
        assertEquals("123;Donald Duck", out);
    }

    @Test
    @Ignore("CAMEL-12320")
    public void testRestletProducerGetInvalid() throws Exception {
        try {
            template.requestBody("http://localhost:" + portNum + "/users/123/basicshouldnotbevalid", null, String.class);
        } catch (Exception e) {
            // should be a 404
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // configure to use restlet on localhost with the given port
                restConfiguration().component("restlet").host("localhost").port(portNum).endpointProperty("restletBinding", "#myBinding");

                // use the rest DSL to define the rest services
                rest("/users/")
                    .get("{id}/basic")
                        .route()
                        .to("mock:input")
                        .process(new Processor() {
                            public void process(Exchange exchange) throws Exception {
                                String id = exchange.getIn().getHeader("id", String.class);
                                exchange.getOut().setBody(id + ";Donald Duck");
                            }
                        });
            }
        };
    }
}
