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
package org.apache.camel.management;

import java.util.Map;
import java.util.Set;
import javax.management.ObjectName;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.direct.DirectEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultComponent;

/**
 * JmxInstrumentationCustomMBeanTest will verify that all endpoints are registered
 * with the mbean server.
 */
public class JmxInstrumentationCustomMBeanTest extends JmxInstrumentationUsingDefaultsTest {

    public void testCustomEndpoint() throws Exception {
        if (!canRunOnThisPlatform()) {
            return;
        }

        if (System.getProperty(JmxSystemPropertyKeys.USE_PLATFORM_MBS) != null
                && !Boolean.getBoolean(JmxSystemPropertyKeys.USE_PLATFORM_MBS)) {
            assertEquals(domainName, mbsc.getDefaultDomain());
        }

        resolveMandatoryEndpoint("custom:end", CustomEndpoint.class);
        ObjectName objName = new ObjectName("testdomain:name=customEndpoint");

        assertEquals("bar", mbsc.getAttribute(objName, "Foo"));
    }

    public void testManagedEndpoint() throws Exception {
        if (!canRunOnThisPlatform()) {
            return;
        }

        if (System.getProperty(JmxSystemPropertyKeys.USE_PLATFORM_MBS) != null
                && !Boolean.getBoolean(JmxSystemPropertyKeys.USE_PLATFORM_MBS)) {
            assertEquals(domainName, mbsc.getDefaultDomain());
        }

        resolveMandatoryEndpoint("direct:start", DirectEndpoint.class);

        ObjectName objName = new ObjectName(domainName + ":type=endpoints,*");
        Set<ObjectName> s = mbsc.queryNames(objName, null);

        ObjectName dynamicallyGeneratedObjName = s.iterator().next();

        assertEquals("direct:start", mbsc.getAttribute(dynamicallyGeneratedObjName, "Uri"));
    }

    public void testCounters() throws Exception {
        if (!canRunOnThisPlatform()) {
            return;
        }

        CustomEndpoint resultEndpoint = resolveMandatoryEndpoint("custom:end", CustomEndpoint.class);
        resultEndpoint.expectedBodiesReceived("<hello>world!</hello>");
        sendBody("direct:start", "<hello>world!</hello>");

        resultEndpoint.assertIsSatisfied();

        verifyCounter(mbsc, new ObjectName(domainName + ":type=routes,*"));
        verifyCounter(mbsc, new ObjectName(domainName + ":type=processors,*"));
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

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = new DefaultCamelContext(createRegistry());
        context.addComponent("custom", new CustomComponent());

        return context;
    }

    private class CustomComponent extends DefaultComponent {
        protected Endpoint createEndpoint(final String uri, final String remaining, final Map parameters) throws Exception {
            return new CustomEndpoint("custom", this);
        }
    }
}
