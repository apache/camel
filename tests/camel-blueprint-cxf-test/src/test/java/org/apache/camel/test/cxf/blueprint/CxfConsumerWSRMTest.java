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

package org.apache.camel.test.cxf.blueprint;

import java.util.Properties;

import javax.xml.namespace.QName;
import javax.xml.ws.Service;

import org.apache.camel.component.cxf.CXFTestSupport;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.blueprint.CamelBlueprintTestSupport;
import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.apache.hello_world_soap_http.Greeter;
import org.junit.Test;

public class CxfConsumerWSRMTest extends CamelBlueprintTestSupport {
    private static final QName SERVICE_NAME = new QName("http://apache.org/hello_world_soap_http",
                                                "SOAPService");
    private static final QName PORT_NAME = new QName("http://apache.org/hello_world_soap_http",
                                                "SoapPort");

    @Override
    protected Properties useOverridePropertiesWithPropertiesComponent() {
        Properties extra = new Properties();
        extra.put("router.address", "http://localhost:" + CXFTestSupport.getPort1() + "/CxfConsumerWSRMTest/router");
        return extra;
    }
    
    @Override
    protected String getBlueprintDescriptor() {
        return "org/apache/camel/test/cxf/blueprint/CxfConsumerWSRMBeans.xml";
    }
   
    public boolean isCreateCamelContextPerClass() {
        return true;
    }

    @Test
    public void testInvokeGreeter() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        Bus clientBus = context.getRegistry().lookupByNameAndType("client-bus", Bus.class);
        assertNotNull(clientBus);
        
        BusFactory.setThreadDefaultBus(clientBus);
        try {
            Service service = Service.create(SERVICE_NAME);
            service.addPort(PORT_NAME, "http://schemas.xmlsoap.org/soap/",
                            "http://localhost:" + CXFTestSupport.getPort1() + "/CxfConsumerWSRMTest/router"); 
            Greeter greeter = service.getPort(PORT_NAME, Greeter.class);
            
            greeter.greetMeOneWay("test");
        } finally {
            BusFactory.setThreadDefaultBus(null);
        }

        assertMockEndpointsSatisfied();
    }
}
