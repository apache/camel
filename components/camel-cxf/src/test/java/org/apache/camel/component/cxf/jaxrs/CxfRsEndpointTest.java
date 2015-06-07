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

import org.apache.camel.CamelContext;
import org.apache.camel.component.cxf.CXFTestSupport;
import org.apache.camel.component.cxf.jaxrs.testbean.CustomerService;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.client.JAXRSClientFactoryBean;
import org.apache.cxf.jaxrs.provider.json.JSONProvider;
import org.junit.Test;

public class CxfRsEndpointTest extends CamelTestSupport {
    private static final String CTX = CXFTestSupport.getPort1() + "/CxfRsEndpointTest";
    
    @Test
    public void testCreateCxfRsEndpoint() throws Exception {
        String endpointUri = "cxfrs://http://localhost:" + CTX + ""
            + "?loggingFeatureEnabled=true&loggingSizeLimit=200"
            + "&resourceClasses=org.apache.camel.component.cxf.jaxrs.testbean.CustomerService,"
            + "java.lang.String,org.apache.camel.component.cxf.jaxrs.testbean.Order";
            
        CxfRsComponent component = new CxfRsComponent(context);
        CxfRsEndpoint endpoint = (CxfRsEndpoint)component.createEndpoint(endpointUri);
        
        assertNotNull("The endpoint should not be null ", endpoint);
        assertEquals("Get a wrong address ", endpointUri, endpoint.getEndpointUri());
        assertEquals("Get a wrong size of resouces classes", 3, endpoint.getResourceClasses().size());
        assertEquals("Get a wrong resources class", CustomerService.class, endpoint.getResourceClasses().get(0));
        assertEquals("Get a wrong loggingFeatureEnabled setting", true, endpoint.isLoggingFeatureEnabled());
        assertEquals("Get a wrong loggingSizeLimit setting", 200, endpoint.getLoggingSizeLimit());
    }
    
    @Test
    public void testCxfRsEndpointParameters() throws Exception {
        CxfRsComponent component = new CxfRsComponent(context);
        String endpointUri = "cxfrs://http://localhost:" + CTX + "/templatetest/TID/ranges/start=0;end=1?"
            + "continuationTimeout=80000&httpClientAPI=true&loggingFeatureEnabled=true&loggingSizeLimit=200&q1=11&q2=12";
           
        CxfRsEndpoint endpoint = (CxfRsEndpoint)component.createEndpoint(endpointUri);
        
        assertEquals("Get a wrong URI ", endpointUri, endpoint.getEndpointUri());
        assertEquals("Get a wrong usingClientAPI option", true, endpoint.isHttpClientAPI());
        assertNotNull("The Parameter should not be null" + endpoint.getParameters());
        assertEquals("Get a wrong parameter map", "{q1=11, q2=12}", endpoint.getParameters().toString());
        assertEquals("Get a wrong continucationTimeout", 80000, endpoint.getContinuationTimeout());
    }
    
    @Test
    public void testCxfRsEndpointResourceClass() throws Exception {
        String endpointUri = "cxfrs://http://localhost:" + CTX + ""
            + "?resourceClass=org.apache.camel.component.cxf.jaxrs.testbean.CustomerService";
            
        CxfRsComponent component = new CxfRsComponent(context);
        CxfRsEndpoint endpoint = (CxfRsEndpoint)component.createEndpoint(endpointUri);
        
        assertNotNull("The endpoint should not be null ", endpoint);
        assertEquals("Get a wrong address ", endpointUri, endpoint.getEndpointUri());
        assertEquals("Get a wrong size of resouces classes", 1, endpoint.getResourceClasses().size());
        assertEquals("Get a wrong resources class", CustomerService.class, endpoint.getResourceClasses().get(0));
        // check the default continuation value
        assertEquals("Get a wrong continucationTimeout", 30000, endpoint.getContinuationTimeout());
    }
    
    @Test
    public void testCxfRsEndpointSetProvider() throws Exception {

        String endpointUri = "cxfrs://http://localhost:" + CTX + ""
                             + "?resourceClass=org.apache.camel.component.cxf.jaxrs.testbean.CustomerService";

        CxfRsComponent component = new CxfRsComponent(context);
        CxfRsEndpoint endpoint = (CxfRsEndpoint)component.createEndpoint(endpointUri);
        JSONProvider<?> jsonProvider = new JSONProvider<Object>();
        jsonProvider.setDropRootElement(true);
        jsonProvider.setSupportUnwrapped(true);
        endpoint.setProvider(jsonProvider);
        JAXRSServerFactoryBean sfb = endpoint.createJAXRSServerFactoryBean();
        assertEquals("Get a wrong proider size", 1, sfb.getProviders().size());
        JAXRSClientFactoryBean cfb = endpoint.createJAXRSClientFactoryBean();
        assertEquals("Get a wrong proider size", 1, cfb.getProviders().size());
    }
    
    @Test
    public void testCxfRsEndpointCamelContextAware() throws Exception {
        String endpointUri = "cxfrs://simple";
        CxfRsEndpoint endpoint = new CxfRsEndpoint();
        endpoint.setAddress("http://localhost:9000/test");
        endpoint.setResourceClasses(CustomerService.class);
        CamelContext context = new DefaultCamelContext();
        context.addEndpoint(endpointUri, endpoint);
        
        assertEquals("Get a wrong camel context.", context, endpoint.getCamelContext());
    }

}
