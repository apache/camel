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

package org.apache.camel.component.netty;

import java.io.File;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.test.CamelTestSupport;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NettySSLTest extends CamelTestSupport {
    private static final transient Logger LOG = LoggerFactory.getLogger(NettySSLTest.class);
  
    @Produce(uri = "direct:start")
    protected ProducerTemplate producerTemplate;
    
    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry registry = new JndiRegistry(createJndiContext());
        registry.bind("password", "changeit");
        registry.bind("ksf", new File("src/test/resources/keystore.jks"));
        registry.bind("tsf", new File("src/test/resources/keystore.jks"));
        return registry;
    }
    
    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    private void sendRequest() throws Exception {
        String response = producerTemplate.requestBody(
            "netty:tcp://localhost:5150?sync=true&ssl=true&passphrase=#password&keyStoreFile=#ksf&trustStoreFile=#tsf", 
            "Epitaph in Kohima, India marking the WWII Battle of Kohima and Imphal, Burma Campaign - Attributed to John Maxwell Edmonds", String.class);        
        assertEquals("When You Go Home, Tell Them Of Us And Say, For Your Tomorrow, We Gave Our Today.", response);
    }
 
    
    @Test
    public void testSSLInOutWithNettyConsumer() throws Exception {
        // ibm jdks dont have sun security algorithms
        if (isJavaVendor("ibm")) {
            return;
        }

        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("netty:tcp://localhost:5150?sync=true&ssl=true&passphrase=#password&keyStoreFile=#ksf&trustStoreFile=#tsf")
                    .process(new Processor() {
                        public void process(Exchange exchange) throws Exception {
                            exchange.getOut().setBody("When You Go Home, Tell Them Of Us And Say, For Your Tomorrow, We Gave Our Today.");                           
                        }
                    });
            }
        });
        context.start();
        
        LOG.debug("Beginning Test ---> testSSLInOutWithNettyConsumer()");       
        sendRequest();
        LOG.debug("Completed Test ---> testSSLInOutWithNettyConsumer()");
        context.stop();
    }    

}
