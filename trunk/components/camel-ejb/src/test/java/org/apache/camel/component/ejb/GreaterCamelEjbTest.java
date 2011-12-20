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
package org.apache.camel.component.ejb;

import java.util.Properties;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

/**
 * @version 
 */
public class GreaterCamelEjbTest extends CamelTestSupport {

    // START SNIPPET: e1
    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext answer = new DefaultCamelContext();

        // enlist EJB component using the JndiContext
        EjbComponent ejb = answer.getComponent("ejb", EjbComponent.class);
        ejb.setContext(createEjbContext());

        return answer;
    }

    private static Context createEjbContext() throws NamingException {
        // here we need to define our context factory to use OpenEJB for our testing
        Properties properties = new Properties();
        properties.setProperty(Context.INITIAL_CONTEXT_FACTORY, "org.apache.openejb.client.LocalInitialContextFactory");

        return new InitialContext(properties);
    }
    // END SNIPPET: e1

    @Test
    public void testGreaterViaCamelEjb() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World");

        template.sendBody("direct:start", "World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // START SNIPPET: e2
                from("direct:start")
                    // invoke the greeter EJB using the local interface and invoke the hello method
                    .to("ejb:GreaterImplLocal?method=hello")
                    .to("mock:result");
                // END SNIPPET: e2
            }
        };
    }

}