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
package org.apache.camel.web.resources;

import java.util.List;

import org.apache.camel.web.model.Camel;
import org.apache.camel.web.model.EndpointLink;
import org.apache.camel.web.model.Endpoints;
import org.junit.Test;

/**
 * @version 
 */
public class EndpointsTest extends TestSupport {

    @Test
    public void testCamelAsXml() throws Exception {
        Camel camel = resource("/").accept("application/xml").get(Camel.class);
        assertValidCamel(camel);

        camel = resource("/.xml").get(Camel.class);
        assertValidCamel(camel);
    }

    @Test
    public void testEndpointsAsXml() throws Exception {
        Endpoints endpoints = resource("endpoints").accept("application/xml").get(Endpoints.class);
        assertValidEndpoints(endpoints);

        endpoints = resource("endpoints.xml").get(Endpoints.class);
        assertValidEndpoints(endpoints);
    }


    // TODO test as JSON


    protected void assertValidCamel(Camel camel) {
        assertNotNull("Should have found camel", camel);

        log.info("Found: " + camel);
    }

    protected void assertValidEndpoints(Endpoints endpoints) {
        assertNotNull("Should have found endpoints", endpoints);

        log.info("Found: " + endpoints.getEndpoints());

        List<EndpointLink> list = endpoints.getEndpoints();
        assertTrue("Should have received some endpoints!", !list.isEmpty());
    }
}