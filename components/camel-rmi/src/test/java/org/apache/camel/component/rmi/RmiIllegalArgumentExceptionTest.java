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

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.JndiRegistry;
import org.junit.Test;

/**
 * @version 
 */
public class RmiIllegalArgumentExceptionTest extends RmiRouteTestSupport {
    private boolean created;

    protected int getStartPort() {
        return 37502;
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        if (classPathHasSpaces()) {
            return null;
        }

        if (!created) {
            LocateRegistry.createRegistry(getPort());
            created = true;
        }

        JndiRegistry context = super.createRegistry();
        context.bind("echo", new EchoService());
        return context;
    }

    @Test
    public void tesIllegal() throws Exception {
        if (classPathHasSpaces()) {
            return;
        }

        getMockEndpoint("mock:result").expectedMessageCount(0);

        try {
            template.sendBody("direct:echo", "Hello World");
            fail("Should have thrown an exception");
        } catch (CamelExecutionException e) {
            assertIsInstanceOf(RemoteException.class, e.getCause());
            // wrapped far down
            IllegalArgumentException iae = assertIsInstanceOf(IllegalArgumentException.class, e.getCause().getCause().getCause().getCause());
            assertEquals("Illegal", iae.getMessage());
        }

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // setup the jmi server endpoint
                RmiEndpoint echo = (RmiEndpoint)endpoint("rmi://localhost:" + getPort() + "/echo");
                echo.setRemoteInterfaces(IEcho.class);
                from(echo).to("bean:echo");

                // and our route where we call the server
                from("direct:echo").to("rmi://localhost:" + getPort() + "/echo?method=foo").to("mock:result");
            }
        };
    }

}