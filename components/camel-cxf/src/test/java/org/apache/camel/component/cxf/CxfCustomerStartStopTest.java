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
package org.apache.camel.component.cxf;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.apache.cxf.transport.http_jetty.JettyHTTPServerEngine;
import org.apache.cxf.transport.http_jetty.JettyHTTPServerEngineFactory;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class CxfCustomerStartStopTest extends Assert {
    
    @Test
    public void startAndStopService() throws Exception {
        CamelContext context = new DefaultCamelContext();
        // start a server    
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("cxf:http://localhost:7070/test?serviceClass=org.apache.camel.component.cxf.HelloService")
                    .to("log:endpoint");
            }
        });
        
        context.start();
        Thread.sleep(300);
        context.stop();
        Bus bus = BusFactory.getDefaultBus();
        JettyHTTPServerEngineFactory factory = bus.getExtension(JettyHTTPServerEngineFactory.class);
        JettyHTTPServerEngine engine = factory.retrieveJettyHTTPServerEngine(7070);
        assertNotNull("Jetty engine should be found there", engine);
        // Need to call the bus shutdown ourselves.
        bus.shutdown(true);
        engine = factory.retrieveJettyHTTPServerEngine(7070);
        assertNull("Jetty engine should be removed", engine);
    }
    
    @Test
    public void startAndStopServiceFromSpring() throws Exception {
        ClassPathXmlApplicationContext applicationContext = 
            new ClassPathXmlApplicationContext("org/apache/camel/component/cxf/CamelCxfConsumerContext.xml");
        Bus bus = (Bus)applicationContext.getBean("cxf");
        // Bus shutdown will be called when the application context is closed.
        applicationContext.close();
        JettyHTTPServerEngineFactory factory = bus.getExtension(JettyHTTPServerEngineFactory.class);
        // test if the port is still used
        JettyHTTPServerEngine engine = factory.retrieveJettyHTTPServerEngine(9003);
        assertNull("Jetty engine should be removed", engine);
    }
    
    
    
    
    
    

}
