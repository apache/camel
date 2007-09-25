/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.management;

import java.util.Iterator;
import java.util.Set;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.management.InstrumentationAgentImpl;

public class JmxInstrumentationUsingDefaultsTest extends ContextTestSupport {
	
	public static final int DEFAULT_PORT = 1099;

    protected InstrumentationAgentImpl iAgent;
    protected String domainName = InstrumentationAgentImpl.DEFAULT_DOMAIN;
    protected boolean sleepSoYouCanBrowseInJConsole = false;

    public void testMBeansRegistered() throws Exception {
        assertNotNull(iAgent.getMBeanServer());
        //assertEquals(domainName, iAgent.getMBeanServer().getDefaultDomain());

    	resolveMandatoryEndpoint("mock:end", MockEndpoint.class);

        ObjectName name = new ObjectName(domainName + ":group=endpoints,*");
        Set s = iAgent.getMBeanServer().queryNames(name, null);

        if (sleepSoYouCanBrowseInJConsole) {
            Thread.sleep(100000);
        }
        assertEquals("Could not find 2 endpoints: " + s, 2, s.size());
    }

    public void testCounters() throws Exception {
    	MockEndpoint resultEndpoint = resolveMandatoryEndpoint("mock:end", MockEndpoint.class);
        resultEndpoint.expectedBodiesReceived("<hello>world!</hello>");
        sendBody("direct:start", "<hello>world!</hello>");

        resultEndpoint.assertIsSatisfied();

        /* 
         * Fixme: after stats are merged with the mbean they represent
         * 
        MBeanServer mbs = iAgent.getMBeanServer();
        ObjectName name = new ObjectName(domainName + ":name=Stats,*");
        Set s = mbs.queryNames(name, null);
        assertEquals("Found mbeans: " + s, 1, s.size());

        Iterator iter = s.iterator();
        ObjectName pcob = (ObjectName)iter.next();

        Long valueofNumExchanges = (Long)mbs.getAttribute(pcob, "NumExchanges");
        assertNotNull("Expected attribute not found. MBean registerred under a " +
        		"'<domain>:name=Stats,*' key must be of type PerformanceCounter.class", valueofNumExchanges);
        assertTrue(valueofNumExchanges == 1);
        Long valueofNumCompleted = (Long)mbs.getAttribute(pcob, "NumCompleted");
        assertNotNull("Expected attribute not found. MBean registerred under a " +
        		"'<domain>:name=Stats,*' key must be of type PerformanceCounter.class", valueofNumCompleted);
        assertTrue(valueofNumCompleted == 1);
        Long valueofNumFailed = (Long)mbs.getAttribute(pcob, "NumFailed");
        assertNotNull("Expected attribute not found. MBean registerred under a " +
        		"'<domain>:name=Stats,*' key must be of type PerformanceCounter.class", valueofNumFailed);
        assertTrue(valueofNumFailed == 0);
        Long valueofMinProcessingTime = (Long)mbs.getAttribute(pcob, "MinProcessingTime");
        assertNotNull("Expected attribute not found. MBean registerred under a " +
        		"'<domain>:name=Stats,*' key must be of type PerformanceCounter.class", valueofMinProcessingTime);
        assertTrue(valueofMinProcessingTime > 0);
        Long valueofMaxProcessingTime = (Long)mbs.getAttribute(pcob, "MaxProcessingTime");
        assertNotNull("Expected attribute not found. MBean registerred under a " +
        		"'<domain>:name=Stats,*' key must be of type PerformanceCounter.class", valueofMaxProcessingTime);
        assertTrue(valueofMaxProcessingTime > 0);
        Long valueofMeanProcessingTime = (Long)mbs.getAttribute(pcob, "MeanProcessingTime");
        assertNotNull("Expected attribute not found. MBean registerred under a " +
        		"'<domain>:name=Stats,*' key must be of type PerformanceCounter.class", valueofMeanProcessingTime);
        assertTrue(valueofMeanProcessingTime >= valueofMinProcessingTime &&
        		valueofMeanProcessingTime <= valueofMaxProcessingTime);
         */
    }

    protected void enableJmx() {
		iAgent.enableJmx(null, 0);
	}
	
    protected CamelContext createCamelContext() throws Exception {
    	CamelContext context = super.createCamelContext();
    	
    	createInstrumentationAgent(context, DEFAULT_PORT);

    	return context;
    }

    protected void createInstrumentationAgent(CamelContext context, int port) throws Exception {
    	iAgent = new InstrumentationAgentImpl();
    	iAgent.setCamelContext(context);
    	enableJmx();
    	iAgent.start();
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start").to("mock:end");
            }
        };
    }


    @Override
    protected void tearDown() throws Exception {
        iAgent.stop();
        super.tearDown();
    }
}
