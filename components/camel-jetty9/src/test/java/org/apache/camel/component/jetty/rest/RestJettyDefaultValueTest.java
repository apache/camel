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
package org.apache.camel.component.jetty.rest;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jetty.BaseJettyTest;
import org.apache.camel.component.jetty.JettyRestHttpBinding;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.model.rest.RestParamType;
import org.apache.camel.util.ObjectHelper;
import org.junit.Test;

public class RestJettyDefaultValueTest extends BaseJettyTest {
    
    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("mybinding", new JettyRestHttpBinding());
        return jndi;
    }

    @Test
    public void testDefaultValue() throws Exception {
        String out = template.requestBody("http://localhost:" + getPort() + "/users/123/basic", null, String.class);
        assertEquals("123;Donald Duck", out);

        String out2 = template.requestBody("http://localhost:" + getPort() + "/users/123/basic?verbose=true", null, String.class);
        assertEquals("123;Donald Duck;1113 Quack Street Duckburg", out2);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // configure to use jetty on localhost with the given port
                restConfiguration().component("jetty").host("localhost").port(getPort()).endpointProperty("httpBindingRef", "#mybinding");

                // use the rest DSL to define the rest services
                rest("/users/")
                        .get("{id}/basic").param().name("verbose").type(RestParamType.query).defaultValue("false").endParam()
                        .route()
                        .process(new Processor() {
                            public void process(Exchange exchange) throws Exception {
                                String id = exchange.getIn().getHeader("id", String.class);

                                Object verbose = exchange.getIn().getHeader("verbose");
                                ObjectHelper.notNull(verbose, "verbose");

                                if ("true".equals(verbose)) {
                                    exchange.getOut().setBody(id + ";Donald Duck;1113 Quack Street Duckburg");
                                }
                                if ("false".equals(verbose)) {
                                    exchange.getOut().setBody(id + ";Donald Duck");
                                }
                            }
                        });
            }
        };
    }

}
