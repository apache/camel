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
package org.apache.camel.component.cxf.jaxrs;

import org.apache.camel.CamelContext;
import org.apache.camel.component.cxf.common.CXFTestSupport;
import org.apache.camel.component.cxf.jaxrs.testbean.CustomerService;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.client.JAXRSClientFactoryBean;
import org.apache.cxf.jaxrs.provider.json.JSONProvider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class CxfRsEndpointTest extends CamelTestSupport {
    private static final String CTX = CXFTestSupport.getPort1() + "/CxfRsEndpointTest";

    @Test
    public void testCreateCxfRsEndpoint() throws Exception {
        String endpointUri = "cxfrs://http://localhost:" + CTX + ""
                             + "?loggingFeatureEnabled=true&loggingSizeLimit=200"
                             + "&resourceClasses=org.apache.camel.component.cxf.jaxrs.testbean.CustomerService,"
                             + "java.lang.String,org.apache.camel.component.cxf.jaxrs.testbean.Order";

        CxfRsComponent component = new CxfRsComponent(context);
        CxfRsEndpoint endpoint = (CxfRsEndpoint) component.createEndpoint(endpointUri);

        assertNotNull(endpoint, "The endpoint should not be null");
        assertEquals(endpointUri, endpoint.getEndpointUri(), "Get a wrong address");
        assertEquals(3, endpoint.getResourceClasses().size(), "Get a wrong size of resouces classes");
        assertEquals(CustomerService.class, endpoint.getResourceClasses().get(0), "Get a wrong resources class");
        assertEquals(true, endpoint.isLoggingFeatureEnabled(), "Get a wrong loggingFeatureEnabled setting");
        assertEquals(200, endpoint.getLoggingSizeLimit(), "Get a wrong loggingSizeLimit setting");
    }

    @Test
    public void testCxfRsEndpointParameters() throws Exception {
        CxfRsComponent component = new CxfRsComponent(context);
        String endpointUri = "cxfrs://http://localhost:" + CTX + "/templatetest/TID/ranges/start=0;end=1?"
                             + "continuationTimeout=80000&httpClientAPI=true&loggingFeatureEnabled=true&loggingSizeLimit=200&q1=11&q2=12";

        CxfRsEndpoint endpoint = (CxfRsEndpoint) component.createEndpoint(endpointUri);

        assertEquals(endpointUri, endpoint.getEndpointUri(), "Get a wrong URI");
        assertEquals(true, endpoint.isHttpClientAPI(), "Get a wrong usingClientAPI option");
        assertNotNull(endpoint.getParameters(), "The Parameter should not be null");
        assertEquals("{q1=11, q2=12}", endpoint.getParameters().toString(), "Get a wrong parameter map");
        assertEquals(80000, endpoint.getContinuationTimeout(), "Get a wrong continucationTimeout");
    }

    @Test
    public void testCxfRsEndpointResourceClass() throws Exception {
        String endpointUri = "cxfrs://http://localhost:" + CTX + ""
                             + "?resourceClass=org.apache.camel.component.cxf.jaxrs.testbean.CustomerService";

        CxfRsComponent component = new CxfRsComponent(context);
        CxfRsEndpoint endpoint = (CxfRsEndpoint) component.createEndpoint(endpointUri);

        assertNotNull(endpoint, "The endpoint should not be null");
        assertEquals(endpointUri, endpoint.getEndpointUri(), "Get a wrong address");
        assertEquals(1, endpoint.getResourceClasses().size(), "Get a wrong size of resouces classes");
        assertEquals(CustomerService.class, endpoint.getResourceClasses().get(0), "Get a wrong resources class");
        // check the default continuation value
        assertEquals(30000, endpoint.getContinuationTimeout(), "Get a wrong continucationTimeout");
    }

    @Test
    public void testCxfRsEndpointSetProvider() throws Exception {

        String endpointUri = "cxfrs://http://localhost:" + CTX + ""
                             + "?resourceClass=org.apache.camel.component.cxf.jaxrs.testbean.CustomerService";

        CxfRsComponent component = new CxfRsComponent(context);
        CxfRsEndpoint endpoint = (CxfRsEndpoint) component.createEndpoint(endpointUri);
        JSONProvider<?> jsonProvider = new JSONProvider<>();
        jsonProvider.setDropRootElement(true);
        jsonProvider.setSupportUnwrapped(true);
        endpoint.setProvider(jsonProvider);
        JAXRSServerFactoryBean sfb = endpoint.createJAXRSServerFactoryBean();
        assertEquals(1, sfb.getProviders().size(), "Get a wrong proider size");
        JAXRSClientFactoryBean cfb = endpoint.createJAXRSClientFactoryBean();
        assertEquals(1, cfb.getProviders().size(), "Get a wrong proider size");
    }

    @Test
    public void testCxfRsEndpointCamelContextAware() throws Exception {
        String endpointUri = "cxfrs://simple";
        CxfRsEndpoint endpoint = new CxfRsEndpoint();
        endpoint.setAddress("http://localhost:9000/test");
        endpoint.setResourceClasses(CustomerService.class);
        CamelContext context = new DefaultCamelContext();
        context.addEndpoint(endpointUri, endpoint);

        assertEquals(context, endpoint.getCamelContext(), "Get a wrong camel context.");
    }

}
