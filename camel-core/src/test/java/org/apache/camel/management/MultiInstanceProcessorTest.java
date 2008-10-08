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

import java.util.Set;

import javax.management.ObjectName;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * A unit test to verify mbean registration of multi-instances of a processor
 *
 * @version $Revision$
 */
public class MultiInstanceProcessorTest extends JmxInstrumentationUsingDefaultsTest {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start").to("mock:end").to("mock:end");
            }
        };
    }

    /**
     * It retrieves a mbean for each "to" processor instance in the query ":type=processor"
     */
    @Override
    public void testMBeansRegistered() throws Exception {
        if (System.getProperty(JmxSystemPropertyKeys.USE_PLATFORM_MBS) != null
                && !Boolean.getBoolean(JmxSystemPropertyKeys.USE_PLATFORM_MBS)) {
            assertEquals(domainName, mbsc.getDefaultDomain());
        }

        resolveMandatoryEndpoint("mock:end", MockEndpoint.class);

        Set s = mbsc.queryNames(
                new ObjectName(domainName + ":type=endpoints,*"), null);
        assertEquals("Could not find 2 endpoints: " + s, 2, s.size());

        s = mbsc.queryNames(
                new ObjectName(domainName + ":name=context,*"), null);
        assertEquals("Could not find 1 context: " + s, 1, s.size());

        s = mbsc.queryNames(
                new ObjectName(domainName + ":type=processors,*"), null);
        assertEquals("Could not find 2 processor: " + s, 2, s.size());

        s = mbsc.queryNames(
                new ObjectName(domainName + ":type=routes,*"), null);
        assertEquals("Could not find 1 route: " + s, 1, s.size());

    }

    @Override
    public void testCounters() throws Exception {

        MockEndpoint resultEndpoint = resolveMandatoryEndpoint("mock:end", MockEndpoint.class);
        resultEndpoint.expectedBodiesReceived(
                new Object[] {"<hello>world!</hello>", "<hello>world!</hello>"});
        sendBody("direct:start", "<hello>world!</hello>");

        resultEndpoint.assertIsSatisfied();

        verifyCounter(mbsc, new ObjectName(domainName + ":type=routes,*"));
        verifyCounter(mbsc, new ObjectName(domainName + ":type=processors,nodeid=to3,*"));
        verifyCounter(mbsc, new ObjectName(domainName + ":type=processors,nodeid=to4,*"));

    }

}
