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
import org.apache.camel.component.cxf.CxfEndpoint;
import org.apache.camel.component.cxf.CxfEndpointUtils;
import org.apache.camel.component.cxf.CxfSpringEndpoint;
import org.apache.camel.component.cxf.DataFormat;
import org.apache.camel.spring.SpringCamelContext;
import org.apache.camel.util.IOHelper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class CxfEndpointUtilsWithSpringTest extends CxfEndpointUtilsTest {
    protected AbstractXmlApplicationContext applicationContext;

    @Before
    public void setUp() throws Exception {
        applicationContext = createApplicationContext();        
        assertNotNull("Should have created a valid spring context", applicationContext);

    }

    @After
    public void tearDown() throws Exception {
        IOHelper.close(applicationContext);
    }

    @Override
    protected CamelContext getCamelContext() throws Exception {
        return SpringCamelContext.springCamelContext(applicationContext);
    }

    protected ClassPathXmlApplicationContext createApplicationContext() {
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
        CxfSpringEndpoint endpoint = (CxfSpringEndpoint)createEndpoint(getEndpointURI());
        QName service = endpoint.getServiceName();
        assertEquals("We should get the right service name", SERVICE_NAME, service);
        assertEquals("The cxf endpoint's DataFromat should be RAW", DataFormat.RAW,
                     endpoint.getDataFormat().dealias());
        
        endpoint = (CxfSpringEndpoint)createEndpoint("cxf:bean:testPropertiesEndpoint");
        service = CxfEndpointUtils.getServiceName(endpoint);
        assertEquals("We should get the right service name", SERVICE_NAME, service);
        QName port = CxfEndpointUtils.getPortName(endpoint);
        assertEquals("We should get the right endpoint name", PORT_NAME, port);
    }

    @Test
    public void testGetDataFormatFromCxfEndpontProperties() throws Exception {
        CxfEndpoint endpoint = createEndpoint(getEndpointURI() + "?dataFormat=PAYLOAD");
        assertEquals("We should get the PAYLOAD DataFormat", DataFormat.PAYLOAD, endpoint.getDataFormat());
    }



}
