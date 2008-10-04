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
package org.apache.camel.rest.resources;

import java.util.List;

import com.sun.jersey.api.client.WebResource;

import org.apache.camel.rest.model.EndpointLink;
import org.apache.camel.rest.model.Endpoints;


/**
 * @version $Revision$
 */
public class EndpointsTest extends TestSupport {

    public void testEndpointsAsXml() throws Exception {
        Endpoints endpoints = resource("endpoints").accept("application/xml").get(Endpoints.class);
        assertValidEndpoints(endpoints);

        endpoints = resource("endpoints.xml").get(Endpoints.class);
        assertValidEndpoints(endpoints);
    }

    // TODO test as JSON

    protected void assertValidEndpoints(Endpoints endpoints) {
        assertNotNull("Should have found endpoints", endpoints);

        System.out.println("Found: " + endpoints.getEndpoints());

        List<EndpointLink> list = endpoints.getEndpoints();
        assertTrue("Should have received some endpoints!", !list.isEmpty());
    }
}