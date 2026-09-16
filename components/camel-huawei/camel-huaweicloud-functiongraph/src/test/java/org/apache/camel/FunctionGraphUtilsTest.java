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
package org.apache.camel;

import org.apache.camel.constants.FunctionGraphConstants;
import org.apache.camel.models.ClientConfigurations;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FunctionGraphUtilsTest extends CamelTestSupport {

    // --- extractJsonFieldAsString: must not assume 'body' is a JSON object (HTTP-triggered functions
    // return it as a JSON-encoded string/primitive), and must tolerate an absent/null field ---

    @Test
    public void extractObjectBodyReturnsItsJson() {
        String result = FunctionGraphUtils.extractJsonFieldAsString("{\"body\":{\"orderId\":1,\"ok\":true}}", "body");
        assertEquals("{\"orderId\":1,\"ok\":true}", result);
    }

    @Test
    public void extractStringBodyReturnsTheRawString() {
        // previously threw ClassCastException because getAsJsonObject was forced on a string member
        String result = FunctionGraphUtils.extractJsonFieldAsString("{\"body\":\"hello world\"}", "body");
        assertEquals("hello world", result);
    }

    @Test
    public void extractNumericBodyReturnsItsValue() {
        String result = FunctionGraphUtils.extractJsonFieldAsString("{\"body\":42}", "body");
        assertEquals("42", result);
    }

    @Test
    public void extractAbsentFieldReturnsNull() {
        // previously threw NullPointerException
        assertNull(FunctionGraphUtils.extractJsonFieldAsString("{\"statusCode\":200}", "body"));
    }

    // --- ClientConfigurations must keep the region for the invoke URN even when 'endpoint' is also set ---

    @Test
    public void urnKeepsRegionWhenEndpointIsAlsoConfigured() {
        FunctionGraphEndpoint endpoint = context.getEndpoint(
                "hwcloud-functiongraph:invokeFunction?region=eu-west-101&endpoint=https://function.example.com"
                                                             + "&projectId=proj-1&functionName=fn&functionPackage=pkg"
                                                             + "&accessKey=ak&secretKey=sk&ignoreSslVerification=true",
                FunctionGraphEndpoint.class);

        ClientConfigurations clientConfigurations = new ClientConfigurations(endpoint);

        assertEquals("eu-west-101", clientConfigurations.getRegion(),
                "region must be populated even when the client is initialized from the endpoint");
        // functionName/functionPackage are filled in by the producer at invoke time; here we only assert the
        // region segment is present (previously it was 'urn:fss:null:...' whenever endpoint was configured)
        String urn = FunctionGraphUtils.composeUrn(FunctionGraphConstants.URN_FORMAT, clientConfigurations);
        assertTrue(urn.startsWith("urn:fss:eu-west-101:proj-1:function:"),
                "the invoke URN must carry the region, was: " + urn);
    }
}
