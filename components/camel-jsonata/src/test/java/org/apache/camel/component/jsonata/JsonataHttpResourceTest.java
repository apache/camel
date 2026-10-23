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
package org.apache.camel.component.jsonata;

import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * For an http resource the endpoint options are used and only the other parameters are sent to the server.
 */
class JsonataHttpResourceTest extends CamelTestSupport {

    @Test
    void testEndpointOptionsAreNotSentToServer() {
        JsonataEndpoint endpoint = context.getEndpoint(
                "jsonata:http://localhost:8095/t.jsonata?inputType=JsonString&outputType=JsonString&prettyPrint=true"
                                                       + "&allowTemplateFromHeader=true&version=2",
                JsonataEndpoint.class);

        assertEquals(JsonataInputOutputType.JsonString, endpoint.getInputType());
        assertEquals(JsonataInputOutputType.JsonString, endpoint.getOutputType());
        assertTrue(endpoint.isPrettyPrint());
        assertTrue(endpoint.isAllowTemplateFromHeader());
        assertEquals("http://localhost:8095/t.jsonata?version=2", endpoint.getResourceUri());
    }

    @Test
    void testOnlyEndpointOptions() {
        JsonataEndpoint endpoint = context.getEndpoint(
                "jsonata:https://localhost:8095/t.jsonata?inputType=JsonString", JsonataEndpoint.class);

        assertEquals(JsonataInputOutputType.JsonString, endpoint.getInputType());
        assertEquals("https://localhost:8095/t.jsonata", endpoint.getResourceUri());
    }
}
