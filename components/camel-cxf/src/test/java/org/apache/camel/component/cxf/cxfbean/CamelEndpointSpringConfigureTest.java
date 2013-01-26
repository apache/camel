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
package org.apache.camel.component.cxf.cxfbean;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.cxf.CxfConsumer;
import org.apache.camel.component.cxf.CxfEndpoint;
import org.apache.camel.component.cxf.CxfProducer;
import org.apache.camel.component.cxf.transport.CamelConduit;
import org.apache.camel.component.cxf.transport.CamelDestination;
import org.apache.camel.spring.SpringCamelContext;
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.junit.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class CamelEndpointSpringConfigureTest extends CamelSpringTestSupport {
    
    @Test
    public void testCreateDestinationFromSpring() throws Exception {
        CxfEndpoint cxfEndpoint = context.getEndpoint("cxf:bean:serviceEndpoint", CxfEndpoint.class);
        CxfProducer producer = (CxfProducer)cxfEndpoint.createProducer();
        assertNotNull("The producer should not be null", producer);        
        producer.start();
        CamelConduit conduit = (CamelConduit)producer.getClient().getConduit();
        assertTrue("we should get SpringCamelContext here", conduit.getCamelContext() instanceof SpringCamelContext);
        assertEquals("The context id should be camel_conduit", "camel_conduit", conduit.getCamelContext().getName());
        
        cxfEndpoint = context.getEndpoint("cxf:bean:routerEndpoint", CxfEndpoint.class);
        CxfConsumer consumer = (CxfConsumer)cxfEndpoint.createConsumer(new Processor() {
            public void process(Exchange exchange) throws Exception {
                // do nothing here                
            }            
        });
        assertNotNull("The consumer should not be null", consumer);        
        consumer.start();
        CamelDestination destination = (CamelDestination)consumer.getServer().getDestination();
        assertTrue("we should get SpringCamelContext here", destination.getCamelContext() instanceof SpringCamelContext);
        assertEquals("The context id should be camel_destination", "camel_destination", destination.getCamelContext().getName());
        
        
    }

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {        
        return new ClassPathXmlApplicationContext("/org/apache/camel/component/cxf/transport/CamelEndpointSpringConfigure.xml");
    }
   
}
