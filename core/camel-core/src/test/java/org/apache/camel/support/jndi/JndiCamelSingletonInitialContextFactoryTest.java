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
package org.apache.camel.support.jndi;

import java.util.Hashtable;

import javax.naming.Context;
import javax.naming.InitialContext;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.DefaultRegistry;
import org.junit.Before;
import org.junit.Test;

/**
 *
 */
public class JndiCamelSingletonInitialContextFactoryTest extends ContextTestSupport {

    private static final String FAKE = "!!! Get DataSource fake !!!";
    private final Hashtable<String, String> env = new Hashtable<>();

    @Override
    @Before
    public void setUp() throws Exception {
        // use the singleton context factory
        env.put(Context.INITIAL_CONTEXT_FACTORY, CamelSingletonInitialContextFactory.class.getName());
        super.setUp();
    }

    @Override
    protected Registry createRegistry() throws Exception {
        Context context = new InitialContext(env);
        context.bind("jdbc/myDataSource", FAKE);
        return new DefaultRegistry(new JndiBeanRepository(context));
    }

    @Test
    public void testSingletonJndiContext() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived(FAKE);

        template.sendBody("direct:simple", "Dummy");

        mock.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:simple").process(new Processor() {
                    @Override
                    public void process(Exchange exchange) throws Exception {
                        // calling this should get us the existing context
                        Context context = new InitialContext(env);
                        exchange.getIn().setBody(context.lookup("jdbc/myDataSource").toString());
                    }
                }).to("mock:result");
            }
        };
    }
}
