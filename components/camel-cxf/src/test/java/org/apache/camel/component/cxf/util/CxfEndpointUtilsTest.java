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
package org.apache.camel.component.cxf.util;

import javax.xml.namespace.QName;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelException;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.cxf.CxfComponent;
import org.apache.camel.component.cxf.CxfEndpoint;
import org.apache.camel.component.cxf.CxfEndpointUtils;
import org.apache.camel.component.cxf.DataFormat;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.Assert;
import org.junit.Test;

public class CxfEndpointUtilsTest extends Assert {
    // set up the port name and service name
    protected static final QName SERVICE_NAME =
        new QName("http://www.example.com/test", "ServiceName");
    protected static final QName PORT_NAME = 
        new QName("http://www.example.com/test", "PortName");

    private static final String CXF_BASE_URI = "cxf://http://www.example.com/testaddress"
        + "?serviceClass=org.apache.camel.component.cxf.HelloService"
        + "&portName={http://www.example.com/test}PortName"
        + "&serviceName={http://www.example.com/test}ServiceName"
        + "&setDefaultBus=true";

    private static final String NO_SERVICE_CLASS_URI = "cxf://http://www.example.com/testaddress"
        + "?portName={http://www.example.com/test}PortName"
        + "&serviceName={http://www.example.com/test}ServiceName";

    protected String getEndpointURI() {
        return CXF_BASE_URI;
    }

    protected String getNoServiceClassURI() {
        return NO_SERVICE_CLASS_URI;
    }

    protected CamelContext getCamelContext() throws Exception {
        return new DefaultCamelContext();
    }

    protected CxfEndpoint createEndpoint(String uri) throws Exception {
        CamelContext context = getCamelContext();
        return (CxfEndpoint)new CxfComponent(context).createEndpoint(uri);
    }

    @Test
    public void testGetProperties() throws Exception {
        CxfEndpoint endpoint = createEndpoint(getEndpointURI());
        QName service = CxfEndpointUtils.getQName(endpoint.getServiceName());
        assertEquals("We should get the right service name", service, SERVICE_NAME);
    }

    @Test
    public void testGetDataFormat() throws Exception {
        CxfEndpoint endpoint = createEndpoint(getEndpointURI() + "&dataFormat=MESSAGE");
        assertEquals("We should get the Message DataFormat", DataFormat.MESSAGE, endpoint.getDataFormat());
    }

    @Test
    public void testCheckServiceClassWithTheEndpoint() throws Exception {
        CxfEndpoint endpoint = createEndpoint(getNoServiceClassURI());
        try {
            CxfEndpointUtils.checkServiceClassName(endpoint.getServiceClass());
            fail("Should get a CamelException here");
        } catch (CamelException exception) {
            assertNotNull("Should get a CamelException here", exception);
            assertEquals("serviceClass is required for CXF endpoint configuration", exception.getMessage());
        }
    }

    @Test
    public void testCheckServiceClassProcedure() throws Exception {
        CxfEndpoint endpoint = createEndpoint(getNoServiceClassURI());
        try {
            endpoint.createProducer();
        } catch (IllegalArgumentException exception) {
            assertNotNull("Should get a CamelException here", exception);
        }
    }

    @Test
    public void testCheckServiceClassConsumer() throws Exception {
        CxfEndpoint endpoint = createEndpoint(getNoServiceClassURI());
        try {
            endpoint.createConsumer(new NullProcessor());
        } catch (IllegalArgumentException exception) {
            assertNotNull("Should get a CamelException here", exception);
            assertTrue(exception.getMessage().startsWith("serviceClass must be specified"));
        }
    }

    class NullProcessor implements Processor {

        public void process(Exchange exchange) throws Exception {
            // Do nothing here
        }

    }

}
