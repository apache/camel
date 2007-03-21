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
package org.apache.camel.spring;

import java.util.Map;
import java.util.Set;

import junit.framework.TestCase;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.processor.DeadLetterChannel;
import org.apache.camel.processor.SendProcessor;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * @version $Revision: 520164 $
 */
public class SpringXMLExtensionTest extends TestCase {
	
	private ClassPathXmlApplicationContext ctx;

	@Override
	protected void setUp() throws Exception {
		ctx = new ClassPathXmlApplicationContext("org/apache/camel/spring/spring_xml_extension_test.xml");
	}

	@Override
	protected void tearDown() throws Exception {
		ctx.close();
	}
	
	public void testSimpleRoute() {
		RouteBuilder builder = (RouteBuilder) ctx.getBean("testSimpleRoute");
		assertNotNull(builder);
		
        Map<Endpoint<Exchange>, Processor<Exchange>> routeMap = builder.getRouteMap();
        Set<Map.Entry<Endpoint<Exchange>, Processor<Exchange>>> routes = routeMap.entrySet();
        assertEquals("Number routes created", 1, routes.size());
        for (Map.Entry<Endpoint<Exchange>, Processor<Exchange>> route : routes) {
            Endpoint<Exchange> key = route.getKey();
            assertEquals("From endpoint", "queue:a", key.getEndpointUri());
            Processor processor = getProcessorWithoutErrorHandler(route);

            assertTrue("Processor should be a SendProcessor but was: " + processor + " with type: " + processor.getClass().getName(), processor instanceof SendProcessor);
            SendProcessor sendProcessor = (SendProcessor) processor;
            assertEquals("Endpoint URI", "queue:b", sendProcessor.getDestination().getEndpointUri());
        }
		
	}
	
	
    /**
     * By default routes should be wrapped in the {@link DeadLetterChannel} so lets unwrap that and return the actual processor
     */
    protected Processor<Exchange> getProcessorWithoutErrorHandler(Map.Entry<Endpoint<Exchange>, Processor<Exchange>> route) {
        Processor<Exchange> processor = route.getValue();
        return unwrapErrorHandler(processor);
    }

    protected Processor<Exchange> unwrapErrorHandler(Processor<Exchange> processor) {
        assertTrue("Processor should be a DeadLetterChannel but was: " + processor + " with type: " + processor.getClass().getName(), processor instanceof DeadLetterChannel);
        DeadLetterChannel deadLetter = (DeadLetterChannel) processor;
        return deadLetter.getOutput();
    }

    protected void assertEndpointUri(Endpoint<Exchange> endpoint, String uri) {
        assertEquals("Endoint uri for: " + endpoint, uri, endpoint.getEndpointUri());
    }
	
}
