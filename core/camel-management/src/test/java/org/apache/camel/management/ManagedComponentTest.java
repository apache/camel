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
package org.apache.camel.management;

import java.util.Collections;
import java.util.Map;

import javax.management.MBeanServer;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.api.management.mbean.ComponentVerifierExtension;
import org.apache.camel.api.management.mbean.ComponentVerifierExtension.Result;
import org.apache.camel.api.management.mbean.ComponentVerifierExtension.Scope;
import org.apache.camel.component.direct.DirectComponent;
import org.apache.camel.component.extension.verifier.DefaultComponentVerifierExtension;
import org.apache.camel.component.extension.verifier.ResultBuilder;
import org.apache.camel.support.DefaultComponent;
import org.junit.Test;

public class ManagedComponentTest extends ManagementTestSupport {
    private static final String[] VERIFY_SIGNATURE = new String[] {
        "java.lang.String", "java.util.Map"
    };

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.init();
        context.addComponent("my-verifiable-component", new MyVerifiableComponent());
        context.addComponent("direct", new DirectComponent());

        return context;
    }

    @Test
    public void testVerifySupported() throws Exception {
        // JMX tests don't work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }

        MBeanServer mbeanServer = getMBeanServer();

        ObjectName on;

        on = ObjectName.getInstance("org.apache.camel:context=camel-1,type=components,name=\"my-verifiable-component\"");
        assertTrue(mbeanServer.isRegistered(on));
        assertTrue(invoke(mbeanServer, on, "isVerifySupported"));

        on = ObjectName.getInstance("org.apache.camel:context=camel-1,type=components,name=\"direct\"");
        assertTrue(mbeanServer.isRegistered(on));
        assertFalse(invoke(mbeanServer, on, "isVerifySupported"));
    }

    @Test
    public void testVerify() throws Exception {
        // JMX tests don't work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }

        MBeanServerConnection mbeanServer = getMBeanServer();

        ObjectName on = ObjectName.getInstance("org.apache.camel:context=camel-1,type=components,name=\"my-verifiable-component\"");
        assertTrue(mbeanServer.isRegistered(on));
        assertTrue(invoke(mbeanServer, on, "isVerifySupported"));

        ComponentVerifierExtension.Result res;

        // check lowercase
        res = invoke(mbeanServer, on, "verify", new Object[]{"connectivity", Collections.emptyMap()}, VERIFY_SIGNATURE);
        assertEquals(Result.Status.OK, res.getStatus());
        assertEquals(Scope.CONNECTIVITY, res.getScope());

        // check mixed case
        res = invoke(mbeanServer, on, "verify", new Object[]{"ConnEctivIty", Collections.emptyMap()}, VERIFY_SIGNATURE);
        assertEquals(Result.Status.OK, res.getStatus());
        assertEquals(Scope.CONNECTIVITY, res.getScope());

        // check uppercase
        res = invoke(mbeanServer, on, "verify", new Object[]{"PARAMETERS", Collections.emptyMap()}, VERIFY_SIGNATURE);
        assertEquals(Result.Status.OK, res.getStatus());
        assertEquals(Scope.PARAMETERS, res.getScope());
    }

    // ***********************************
    //
    // ***********************************

    private static class MyVerifiableComponent extends DefaultComponent {
        public MyVerifiableComponent() {
            registerExtension(() -> new DefaultComponentVerifierExtension("my-verifiable-component", getCamelContext()) {
                @Override
                protected Result verifyConnectivity(Map<String, Object> parameters) {
                    return ResultBuilder.withStatusAndScope(Result.Status.OK, Scope.CONNECTIVITY).build();
                }
                @Override
                protected Result verifyParameters(Map<String, Object> parameters) {
                    return ResultBuilder.withStatusAndScope(Result.Status.OK, Scope.PARAMETERS).build();
                }
            });
        }

        @Override
        protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
            throw new UnsupportedOperationException();
        }
    }
}
