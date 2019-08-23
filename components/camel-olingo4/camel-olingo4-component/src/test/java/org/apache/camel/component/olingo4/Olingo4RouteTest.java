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
package org.apache.camel.component.olingo4;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.olingo.client.api.domain.ClientEntitySet;
import org.junit.Test;

public class Olingo4RouteTest extends CamelTestSupport {
    protected static final String TEST_SERVICE_BASE_URL = "http://services.odata.org/TripPinRESTierService";

    @SuppressWarnings("unchecked")
    protected <T> T requestBody(String endpoint, Object body) throws CamelExecutionException {
        return (T)template().requestBody(endpoint, body);
    }

    @Test
    public void testRead() throws Exception {
        // Read entity set of the People object
        final ClientEntitySet entities = (ClientEntitySet)requestBody("direct:readentities", null);
        assertNotNull(entities);
        assertEquals(20, entities.getEntities().size());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("direct:readentities").to("olingo4://read/People?serviceUri=" + TEST_SERVICE_BASE_URL);
            }
        };
    }
}
