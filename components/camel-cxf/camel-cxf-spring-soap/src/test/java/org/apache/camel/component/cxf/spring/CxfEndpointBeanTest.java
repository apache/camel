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
package org.apache.camel.component.cxf.spring;

import javax.xml.namespace.QName;

import org.apache.camel.CamelContext;
import org.apache.camel.component.cxf.common.CXFTestSupport;
import org.apache.camel.component.cxf.jaxws.CxfEndpoint;
import org.apache.camel.component.cxf.jaxws.CxfProducer;
import org.apache.cxf.binding.soap.SoapBindingConfiguration;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.transport.http.HTTPConduit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CxfEndpointBeanTest extends AbstractSpringBeanTestSupport {
    static int port1 = CXFTestSupport.getPort1();
    static {
        //set them as system properties so Spring can use the property placeholder
        //things to set them into the URL's in the spring contexts
        System.setProperty("CxfEndpointBeans.serviceName", "{http://camel.apache.org/wsdl-first}PersonService");
        System.setProperty("CxfEndpointBeans.endpointName", "{http://camel.apache.org/wsdl-first}soap");
    }
    private QName serviceName = QName.valueOf("{http://camel.apache.org/wsdl-first}PersonService");
    private QName endpointName = QName.valueOf("{http://camel.apache.org/wsdl-first}soap");

    @Override
    protected String[] getApplicationContextFiles() {
        return new String[] { "org/apache/camel/component/cxf/spring/CxfEndpointBeans.xml" };
    }

    @Test
    public void testCxfEndpointBeanDefinitionParser() {
        CxfEndpoint routerEndpoint = ctx.getBean("routerEndpoint", CxfEndpoint.class);
        assertEquals("http://localhost:" + port1
                     + "/CxfEndpointBeanTest/router",
                routerEndpoint.getAddress(), "Got the wrong endpoint address");
        assertEquals("org.apache.camel.component.cxf.HelloService",
                routerEndpoint.getServiceClass().getName(), "Got the wrong endpont service class");
        assertEquals(false, routerEndpoint.isLoggingFeatureEnabled(), "loggingFeatureEnabled should be false");
        assertEquals(0, routerEndpoint.getLoggingSizeLimit(), "loggingSizeLimit should not be set");
        assertEquals(1, routerEndpoint.getHandlers().size(), "Got the wrong handlers size");
        assertEquals(1, routerEndpoint.getSchemaLocations().size(), "Got the wrong schemalocations size");
        assertEquals("classpath:wsdl/Message.xsd", routerEndpoint.getSchemaLocations().get(0), "Got the wrong schemalocation");
        assertEquals(60000, routerEndpoint.getContinuationTimeout(), "Got the wrong continuationTimeout");

        CxfEndpoint myEndpoint = ctx.getBean("myEndpoint", CxfEndpoint.class);
        assertEquals(endpointName, myEndpoint.getPortNameAsQName(), "Got the wrong endpointName");
        assertEquals(serviceName, myEndpoint.getServiceNameAsQName(), "Got the wrong serviceName");
        assertEquals(true, myEndpoint.isLoggingFeatureEnabled(), "loggingFeatureEnabled should be true");
        assertEquals(200, myEndpoint.getLoggingSizeLimit(), "loggingSizeLimit should be set");
        assertTrue(myEndpoint.getBindingConfig() instanceof SoapBindingConfiguration, "We should get a soap binding");
        SoapBindingConfiguration configuration = (SoapBindingConfiguration) myEndpoint.getBindingConfig();
        assertEquals("1.2", String.valueOf(configuration.getVersion().getVersion()), "We should get a right soap version");

    }

    @Test
    public void testCxfEndpointsWithCamelContext() {
        CamelContext context = ctx.getBean("myCamelContext", CamelContext.class);
        // try to create a new CxfEndpoint which could override the old bean's setting
        CxfEndpoint myLocalCxfEndpoint = (CxfEndpoint) context.getEndpoint("cxf:bean:routerEndpoint?address=http://localhost:"
                                                                           + port1 + "/CxfEndpointBeanTest/myCamelContext/");
        assertEquals("http://localhost:" + port1
                     + "/CxfEndpointBeanTest/myCamelContext/",
                myLocalCxfEndpoint.getAddress(), "Got the wrong endpoint address");

        CxfEndpoint routerEndpoint = ctx.getBean("routerEndpoint", CxfEndpoint.class);
        assertEquals("http://localhost:" + port1
                     + "/CxfEndpointBeanTest/router",
                routerEndpoint.getAddress(), "Got the wrong endpoint address");
    }

    @Test
    public void testPropertiesSettingOnCxfClient() throws Exception {
        CxfEndpoint clientEndpoint = ctx.getBean("clientEndpoint", CxfEndpoint.class);
        CxfProducer producer = (CxfProducer) clientEndpoint.createProducer();
        // need to start the producer to get the client
        producer.start();
        Client client = producer.getClient();
        HTTPConduit conduit = (HTTPConduit) client.getConduit();
        assertEquals("test", conduit.getAuthorization().getUserName(), "Got the wrong user name");
    }

}
