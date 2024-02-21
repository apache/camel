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
package org.apache.camel.component.cxf.util;

import javax.xml.namespace.QName;

import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.cxf.common.DataFormat;
import org.apache.camel.component.cxf.jaxws.CxfComponent;
import org.apache.camel.component.cxf.jaxws.CxfEndpoint;
import org.apache.camel.component.cxf.spring.jaxws.CxfSpringEndpoint;
import org.apache.camel.component.cxf.spring.jaxws.CxfSpringEndpointUtils;
import org.apache.camel.test.spring.junit5.CamelSpringTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CxfEndpointUtilsWithSpringTest extends CamelSpringTestSupport {
    // set up the port name and service name
    protected static final QName SERVICE_NAME = new QName("http://www.example.com/test", "ServiceName");
    protected static final QName PORT_NAME = new QName("http://www.example.com/test", "PortName");

    private static final String CXF_BASE_URI = "cxf://http://www.example.com/testaddress"
                                               + "?serviceClass=org.apache.camel.component.cxf.HelloService"
                                               + "&portName={http://www.example.com/test}PortName"
                                               + "&serviceName={http://www.example.com/test}ServiceName"
                                               + "&defaultBus=true";

    private static final String NO_SERVICE_CLASS_URI = "cxf://http://www.example.com/testaddress"
                                                       + "?portName={http://www.example.com/test}PortName"
                                                       + "&serviceName={http://www.example.com/test}ServiceName";

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/component/cxf/util/CxfEndpointBeans.xml");
    }

    protected String getEndpointURI() {
        return "cxf:bean:testEndpoint";
    }

    protected String getNoServiceClassURI() {
        return "cxf:bean:noServiceClassEndpoint";
    }

    @Test
    public void testGetServiceClass() throws Exception {
        CxfEndpoint endpoint = createEndpoint("cxf:bean:helloServiceEndpoint?serviceClass=#helloServiceImpl");
        assertEquals("org.apache.camel.component.cxf.HelloServiceImpl",
                endpoint.getServiceClass().getName());
    }

    public char sepChar() {
        return '?';
    }

    @Test
    public void testGetProperties() throws Exception {
        CxfSpringEndpoint endpoint = (CxfSpringEndpoint) createEndpoint(getEndpointURI());
        QName service = endpoint.getServiceNameAsQName();
        assertEquals(SERVICE_NAME, service, "We should get the right service name");
        assertEquals(DataFormat.RAW, endpoint.getDataFormat().dealias(), "The cxf endpoint's DataFromat should be RAW");

        endpoint = (CxfSpringEndpoint) createEndpoint("cxf:bean:testPropertiesEndpoint");
        service = CxfSpringEndpointUtils.getServiceName(endpoint);
        assertEquals(SERVICE_NAME, service, "We should get the right service name");
        QName port = CxfSpringEndpointUtils.getPortName(endpoint);
        assertEquals(PORT_NAME, port, "We should get the right endpoint name");
    }

    @Test
    public void testGetDataFormatFromCxfEndpontProperties() throws Exception {
        CxfEndpoint endpoint = createEndpoint(getEndpointURI() + "?dataFormat=PAYLOAD");
        assertEquals(DataFormat.PAYLOAD, endpoint.getDataFormat(), "We should get the PAYLOAD DataFormat");
    }

    @Test
    public void testGetDataFormatCXF() throws Exception {
        CxfEndpoint endpoint = createEndpoint(getEndpointURI() + sepChar() + "dataFormat=CXF_MESSAGE");
        assertEquals(DataFormat.CXF_MESSAGE, endpoint.getDataFormat(), "We should get the Message DataFormat");
    }

    @Test
    public void testGetDataFormatRAW() throws Exception {
        CxfEndpoint endpoint = createEndpoint(getEndpointURI() + sepChar() + "dataFormat=RAW");
        assertEquals(DataFormat.RAW, endpoint.getDataFormat(), "We should get the Message DataFormat");
    }

    @Test
    public void testCheckServiceClassWithTheEndpoint() throws Exception {
        CxfEndpoint endpoint = createEndpoint(getNoServiceClassURI());
        assertNull(endpoint.getServiceClass());
    }

    @Test
    public void testCheckServiceClassProcedure() throws Exception {
        CxfEndpoint endpoint = createEndpoint(getNoServiceClassURI());
        assertNotNull(endpoint.createProducer());
    }

    @Test
    public void testCheckServiceClassConsumer() throws Exception {
        CxfEndpoint endpoint = createEndpoint(getNoServiceClassURI());

        Consumer cxfConsumer = endpoint.createConsumer(new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                // noop
            }
        });

        Exception ex = assertThrows(IllegalArgumentException.class, () -> cxfConsumer.start());
        assertNotNull(ex, "Should get a CamelException here");
        assertTrue(ex.getMessage().startsWith("serviceClass must be specified"));
    }

    protected CxfEndpoint createEndpoint(String uri) throws Exception {
        return (CxfEndpoint) new CxfComponent(context).createEndpoint(uri);
    }
}
