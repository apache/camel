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
package org.apache.camel.component.odata;

import java.net.URI;

import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ODataEndpointTest extends CamelTestSupport {

    @Test
    void testEndpointUriConfiguration() throws Exception {
        ODataEndpoint endpoint = context.getEndpoint(
                "odata:http://localhost:8080/odata/Products", ODataEndpoint.class);

        assertNotNull(endpoint);
        assertEquals(new URI("http://localhost:8080/odata/Products"), endpoint.getHttpUri());
        assertNotNull(endpoint.createProducer());
    }

    @Test
    void testEndpointWithQueryParameters() throws Exception {
        ODataEndpoint endpoint = context.getEndpoint(
                "odata:http://localhost:8080/odata/Products?top=10&filter=Name%20eq%20'Test'", ODataEndpoint.class);

        assertNotNull(endpoint);
        assertEquals(new URI("http://localhost:8080/odata/Products"), endpoint.getHttpUri());
        assertEquals(10, endpoint.getConfiguration().getTop());
        assertEquals("Name eq 'Test'", endpoint.getConfiguration().getFilter());
    }

    @Test
    void testEndpointSchemeAndMetadata() throws Exception {
        ODataEndpoint endpoint = context.getEndpoint(
                "odata:http://localhost:8080/odata/Products", ODataEndpoint.class);

        assertEquals("odata", endpoint.getEndpointKey().split(":")[0]);
        assertEquals("odata://http://localhost:8080/odata/Products", endpoint.getEndpointUri());
    }

    @Test
    void testSecretMaskingInUriAndToString() {
        String rawUri
                = "odata:http://localhost:8080/odata/Products?authUsername=user&authPassword=secretPassword&authBearerToken=myToken";
        ODataEndpoint endpoint = context.getEndpoint(rawUri, ODataEndpoint.class);

        assertNotNull(endpoint);

        // toString() must mask secrets
        String toString = endpoint.toString();
        assertFalse(toString.contains("secretPassword"), "toString() should not reveal authPassword");
        assertFalse(toString.contains("myToken"), "toString() should not reveal authBearerToken");
        assertTrue(toString.contains("authPassword=xxxxxx") || toString.contains("authPassword=******"));

        // Check URISupport sanitization directly if testing Camel's URI masking utility
        String sanitizedUri = org.apache.camel.util.URISupport.sanitizeUri(endpoint.getEndpointUri());
        assertFalse(sanitizedUri.contains("secretPassword"), "Sanitized URI should not contain authPassword");
        assertFalse(sanitizedUri.contains("myToken"), "Sanitized URI should not contain authBearerToken");
    }

    @Test
    void testSslContextParametersAware() {
        ODataEndpoint endpoint = context.getEndpoint(
                "odata:https://localhost:8443/odata/Products?useGlobalSslContextParameters=true",
                ODataEndpoint.class);

        assertTrue(endpoint.isUseGlobalSslContextParameters());

        SSLContextParameters sslParams = new SSLContextParameters();
        endpoint.setSslContextParameters(sslParams);

        assertEquals(sslParams, endpoint.getSslContextParameters());
    }
}
