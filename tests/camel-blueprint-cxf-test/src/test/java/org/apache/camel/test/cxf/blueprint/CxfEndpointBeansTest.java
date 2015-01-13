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

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import javax.xml.namespace.QName;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.cxf.CXFTestSupport;
import org.apache.camel.component.cxf.CxfEndpoint;
import org.apache.camel.component.cxf.common.message.CxfConstants;
import org.apache.camel.test.blueprint.CamelBlueprintTestSupport;
import org.apache.camel.util.URISupport;
import org.apache.cxf.clustering.FailoverFeature;
import org.apache.cxf.transport.http.HTTPException;
import org.junit.Test;

public class CxfEndpointBeansTest extends CamelBlueprintTestSupport {

    @Override
    protected String getBlueprintDescriptor() {
        return "org/apache/camel/test/cxf/blueprint/CxfEndpointBeans.xml";
    }

    @Override
    protected String getBundleDirectives() {
        return "blueprint.aries.xml-validation:=false";
    }

    @Override
    protected Properties useOverridePropertiesWithPropertiesComponent() {
        Properties extra = new Properties();
        extra.put("router.address", "http://localhost:" + CXFTestSupport.getPort1() + "/CxfEndpointBeansRouterTest/router");
        extra.put("service.address", "http://localhost:" + CXFTestSupport.getPort2() + "/CxfEndpointBeansRouterTest/service");
        extra.put("test.address", "http://localhost:" + CXFTestSupport.getPort3() + "/testEndpoint");
        return extra;
    }
    
    @Test
    public void testCxfBusInjection() {
        CxfEndpoint serviceEndpoint = context.getEndpoint("cxf:bean:serviceEndpoint", CxfEndpoint.class);
        CxfEndpoint routerEndpoint = context.getEndpoint("cxf:bean:routerEndpoint", CxfEndpoint.class);
        assertEquals("These endpoints don't share the same bus", serviceEndpoint.getBus().getId(), routerEndpoint.getBus().getId());
    }
    
    @Test
    public void testCxfFeatureSetting() {
        CxfEndpoint routerEndpoint = context.getEndpoint("cxf:bean:routerEndpoint", CxfEndpoint.class);
        assertEquals("Get a wrong size of features.", 1, routerEndpoint.getFeatures().size());
        assertTrue("Get a wrong feature instance.", routerEndpoint.getFeatures().get(0) instanceof FailoverFeature);
    }

    
    @Test
    public void testCxfEndpointBeanDefinitionParser() {
        CxfEndpoint routerEndpoint = context.getEndpoint("routerEndpoint", CxfEndpoint.class);
        assertEquals("Got the wrong endpoint address", routerEndpoint.getAddress(),
                     "http://localhost:" + CXFTestSupport.getPort1() + "/CxfEndpointBeansRouterTest/router");
        assertEquals("Got the wrong endpont service class", 
                     "org.apache.camel.component.cxf.HelloService", 
                     routerEndpoint.getServiceClass().getName());
    }
    
    @Test
    public void testCreateCxfEndpointFromURI() throws Exception {
        CxfEndpoint endpoint1 = context.getEndpoint("cxf:bean:routerEndpoint?address=http://localhost:9000/test1", CxfEndpoint.class);
        CxfEndpoint endpoint2 = context.getEndpoint("cxf:bean:routerEndpoint?address=http://localhost:8000/test2", CxfEndpoint.class);
        assertEquals("Get a wrong endpoint address.", "http://localhost:9000/test1", endpoint1.getAddress());
        assertEquals("Get a wrong endpoint address.", "http://localhost:8000/test2", endpoint2.getAddress());

        // the uri will always be normalized
        String uri1 = URISupport.normalizeUri("cxf://bean:routerEndpoint?address=http://localhost:9000/test1");
        String uri2 = URISupport.normalizeUri("cxf://bean:routerEndpoint?address=http://localhost:8000/test2");
        assertEquals("Get a wrong endpoint key.", uri1, endpoint1.getEndpointKey());
        assertEquals("Get a wrong endpoint key.", uri2, endpoint2.getEndpointKey());
    }

    @Test
    public void testCxfBusConfiguration() throws Exception {
        // get the camelContext from application context
        ProducerTemplate template = context.createProducerTemplate();

        Exchange reply = template.request("cxf:bean:serviceEndpoint", new Processor() {
            public void process(final Exchange exchange) {
                final List<String> params = new ArrayList<String>();
                params.add("hello");
                exchange.getIn().setBody(params);
                exchange.getIn().setHeader(CxfConstants.OPERATION_NAME, "echo");
            }
        });

        Exception ex = reply.getException();
        assertTrue("Should get the fault here", 
                   ex instanceof org.apache.cxf.interceptor.Fault
                   || ex instanceof HTTPException);
    }

    @Test
    public void testCxfBeanWithCamelPropertiesHolder() throws Exception {
        // get the camelContext from application context
        CxfEndpoint testEndpoint = context.getEndpoint("cxf:bean:testEndpoint", CxfEndpoint.class);
        QName endpointName = QName.valueOf("{http://org.apache.camel.component.cxf}myEndpoint");
        QName serviceName = QName.valueOf("{http://org.apache.camel.component.cxf}myService");

        assertEquals("Got a wrong address", 
                     "http://localhost:" + CXFTestSupport.getPort3() + "/testEndpoint", testEndpoint.getAddress());
        assertEquals("Got a wrong bindingId", "http://schemas.xmlsoap.org/wsdl/soap12/", testEndpoint.getBindingId());
        assertEquals("Got a wrong transportId", "http://cxf.apache.org/transports/http", testEndpoint.getTransportId());
        assertEquals("Got a wrong endpointName", endpointName, testEndpoint.getPortName());
        assertEquals("Got a wrong WsdlURL", "wsdl/test.wsdl", testEndpoint.getWsdlURL());
        assertEquals("Got a wrong serviceName", serviceName, testEndpoint.getServiceName());
    }
}
