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
package org.apache.camel.component.rmi;

import java.rmi.registry.LocateRegistry;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.bean.ProxyHelper;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.util.jndi.JndiContext;
import org.junit.Test;

/**
 * @version 
 */
public class RmiRouteTest extends RmiRouteTestSupport {

    protected int getStartPort() {
        return 37503;
    }
    
    @Test
    public void testPojoRoutes() throws Exception {
        if (classPathHasSpaces()) {
            return;
        }

        // Boot up a local RMI registry
        LocateRegistry.createRegistry(getPort());

        // START SNIPPET: register
        JndiContext context = new JndiContext();
        context.bind("bye", new SayService("Good Bye!"));

        CamelContext camelContext = new DefaultCamelContext(context);
        // END SNIPPET: register

        camelContext.addRoutes(getRouteBuilder(camelContext));

        camelContext.start();

        // START SNIPPET: invoke
        Endpoint endpoint = camelContext.getEndpoint("direct:hello");
        ISay proxy = ProxyHelper.createProxy(endpoint, false, ISay.class);
        String rc = proxy.say();
        assertEquals("Good Bye!", rc);
        // END SNIPPET: invoke

        camelContext.stop();
    }

    protected RouteBuilder getRouteBuilder(final CamelContext context) {
        return new RouteBuilder() {
            // START SNIPPET: route
            // lets add simple route
            public void configure() {
                from("direct:hello").toF("rmi://localhost:%s/bye", getPort());

                // When exposing an RMI endpoint, the interfaces it exposes must
                // be configured.
                RmiEndpoint bye = (RmiEndpoint)endpoint("rmi://localhost:" + getPort() + "/bye");
                bye.setRemoteInterfaces(ISay.class);
                from(bye).to("bean:bye");
            }
            // END SNIPPET: route
        };
    }
    
}
