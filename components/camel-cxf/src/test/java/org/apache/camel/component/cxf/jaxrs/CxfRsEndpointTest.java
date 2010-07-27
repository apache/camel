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

import org.apache.camel.component.cxf.jaxrs.testbean.CustomerService;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class CxfRsEndpointTest extends CamelTestSupport {
    
    @Test
    public void testCreateCxfRsEndpoint() throws Exception {
        String endpointUri = "cxfrs://http://localhost:9000"
            + "?resourceClasses=org.apache.camel.component.cxf.jaxrs.testbean.CustomerService, "
            + "java.lang.String ; org.apache.camel.component.cxf.jaxrs.testbean.Order";
        CxfRsComponent component = new CxfRsComponent(context);
        CxfRsEndpoint endpoint = (CxfRsEndpoint)component.createEndpoint(endpointUri);
        
        assertNotNull("The endpoint should not be null ", endpoint);
        assertEquals("Get a wrong address ", endpointUri, endpoint.getEndpointUri());
        assertEquals("Get a wrong size of resouces classes", 3, endpoint.getResourceClasses().size());
        assertEquals("Get a wrong resources class", CustomerService.class, endpoint.getResourceClasses().get(0));
    }
    
    @Test
    public void testCxfRsEndpointParameters() throws Exception {
        CxfRsComponent component = new CxfRsComponent(context);
        CxfRsEndpoint endpoint = (CxfRsEndpoint)component.createEndpoint("cxfrs://http://localhost:9000/templatetest/TID/ranges/start=0;end=1?"
            + "httpClientAPI=true&q1=11&q2=12");
        
        assertEquals("Get a wrong URI ", "cxfrs://http://localhost:9000/templatetest/TID/ranges/start=0;end=1?httpClientAPI=true&q1=11&q2=12", endpoint.getEndpointUri());
        assertEquals("Get a wrong usingClientAPI option", true, endpoint.isHttpClientAPI());
        assertNotNull("The Parameter should not be null" + endpoint.getParameters());
        assertEquals("Get a wrong parameter map", "{q1=11, q2=12}", endpoint.getParameters().toString());
    }

}
