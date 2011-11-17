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
package org.apache.camel.component.cxf.jaxrs;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.test.AvailablePortFinder;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class CxfRsProducerAddressOverrideTest extends CxfRsProducerTest {
    private static int port1 = AvailablePortFinder.getNextAvailable(); 
    private static int port2 = AvailablePortFinder.getNextAvailable(); 
    
    public int getPort1() {
        return port1;
    }
    public int getPort2() {
        return port2;
    }
    
    
    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {       
        System.setProperty("CxfRsProducerAddressOverrideTest.port1", Integer.toString(port1));
        System.setProperty("CxfRsProducerAddressOverrideTest.port2", Integer.toString(port2));
        return new ClassPathXmlApplicationContext("org/apache/camel/component/cxf/jaxrs/CxfRsSpringProducerAddressOverride.xml");
    }
    
    protected void setupDestinationURL(Message inMessage) {
        inMessage.setHeader(Exchange.DESTINATION_OVERRIDE_URL, 
            "http://localhost:" + port1);
    }
}
