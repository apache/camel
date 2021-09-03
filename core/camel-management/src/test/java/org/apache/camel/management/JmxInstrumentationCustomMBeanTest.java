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

import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import javax.management.ObjectName;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.direct.DirectEndpoint;
import org.apache.camel.component.mock.MockComponent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * JmxInstrumentationCustomMBeanTest will verify that all endpoints are registered with the mbean server.
 */
@DisabledOnOs(OS.AIX)
public class JmxInstrumentationCustomMBeanTest extends JmxInstrumentationUsingDefaultsTest {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        context.addComponent("custom", new CustomComponent());

        return context;
    }

    @Test
    public void testCustomEndpoint() throws Exception {
        assertDefaultDomain();

        resolveMandatoryEndpoint("custom://end", CustomEndpoint.class);

        Set<ObjectName> s = mbsc.queryNames(new ObjectName(domainName + ":type=endpoints,*"), null);
        assertEquals(2, s.size(), "Could not find 2 endpoints: " + s);

        // get custom
        Iterator<ObjectName> it = s.iterator();
        ObjectName on1 = it.next();
        ObjectName on2 = it.next();

        if (on1.getCanonicalName().contains("custom")) {
            assertEquals("bar", mbsc.getAttribute(on1, "Foo"));
        } else {
            assertEquals("bar", mbsc.getAttribute(on2, "Foo"));
        }
    }

    @Test
    public void testManagedEndpoint() throws Exception {
        assertDefaultDomain();

        resolveMandatoryEndpoint("direct:start", DirectEndpoint.class);

        ObjectName objName = new ObjectName(domainName + ":type=endpoints,*");
        Set<ObjectName> s = mbsc.queryNames(objName, null);
        assertEquals(2, s.size());
    }

    @Override
    @Test
    public void testCounters() throws Exception {
        CustomEndpoint resultEndpoint = resolveMandatoryEndpoint("custom:end", CustomEndpoint.class);
        resultEndpoint.expectedBodiesReceived("<hello>world!</hello>");
        sendBody("direct:start", "<hello>world!</hello>");

        resultEndpoint.assertIsSatisfied();

        verifyCounter(mbsc, new ObjectName(domainName + ":type=routes,*"));
    }

    @Override
    @Test
    public void testMBeansRegistered() throws Exception {
        assertDefaultDomain();

        Set<ObjectName> s = mbsc.queryNames(new ObjectName(domainName + ":type=endpoints,*"), null);
        assertEquals(2, s.size(), "Could not find 2 endpoints: " + s);

        s = mbsc.queryNames(new ObjectName(domainName + ":type=context,*"), null);
        assertEquals(1, s.size(), "Could not find 1 context: " + s);

        s = mbsc.queryNames(new ObjectName(domainName + ":type=processors,*"), null);
        assertEquals(2, s.size(), "Could not find 1 processors: " + s);

        s = mbsc.queryNames(new ObjectName(domainName + ":type=routes,*"), null);
        assertEquals(1, s.size(), "Could not find 1 route: " + s);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // need a little delay for fast computers being able to process
                // the exchange in 0 millis and we need to simulate a little computation time
                from("direct:start").delay(10).to("custom:end");
            }
        };
    }

    private static class CustomComponent extends MockComponent {
        @Override
        protected Endpoint createEndpoint(final String uri, final String remaining, final Map<String, Object> parameters)
                throws Exception {
            return new CustomEndpoint(uri, this);
        }
    }
}
