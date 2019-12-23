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
package org.apache.camel.component.couchbase;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class CouchbaseComponentTest extends CamelTestSupport {

    private CouchbaseComponent component;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        component = context.getComponent("couchbase",  CouchbaseComponent.class);
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testEndpointCreated() throws Exception {
        Map<String, Object> params = new HashMap<>();

        String uri = "couchbase:http://localhost:9191/bucket";
        String remaining = "http://localhost:9191/bucket";

        CouchbaseEndpoint endpoint = component.createEndpoint(uri, remaining, params);
        assertNotNull(endpoint);
    }

    @Test
    public void testPropertiesSet() throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("username", "ugol");
        params.put("password", "pwd");
        params.put("additionalHosts", "127.0.0.1,example.com,another-host");
        params.put("persistTo", 2);
        params.put("replicateTo", 3);

        String uri = "couchdb:http://localhost:91234/bucket";
        String remaining = "http://localhost:91234/bucket";

        CouchbaseEndpoint endpoint = component.createEndpoint(uri, remaining, params);

        assertEquals("http", endpoint.getProtocol());
        assertEquals("localhost", endpoint.getHostname());
        assertEquals("bucket", endpoint.getBucket());
        assertEquals(91234, endpoint.getPort());
        assertEquals("ugol", endpoint.getUsername());
        assertEquals("pwd", endpoint.getPassword());
        assertEquals("127.0.0.1,example.com,another-host", endpoint.getAdditionalHosts());
        assertEquals(2, endpoint.getPersistTo());
        assertEquals(3, endpoint.getReplicateTo());
    }

    @Test
    public void testCouchbaseURI() throws Exception {
        Map<String, Object> params = new HashMap<>();
        String uri = "couchbase:http://localhost/bucket?param=true";
        String remaining = "http://localhost/bucket?param=true";

        CouchbaseEndpoint endpoint = new CouchbaseComponent(context).createEndpoint(uri, remaining, params);
        assertEquals(new URI("http://localhost:8091/pools"), endpoint.makeBootstrapURI()[0]);
    }

    @Test
    public void testCouchbaseAdditionalHosts() throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("additionalHosts", "127.0.0.1,example.com,another-host");
        String uri = "couchbase:http://localhost/bucket?param=true";
        String remaining = "http://localhost/bucket?param=true";

        CouchbaseEndpoint endpoint = new CouchbaseComponent(context).createEndpoint(uri, remaining, params);

        URI[] endpointArray = endpoint.makeBootstrapURI();
        assertEquals(new URI("http://localhost:8091/pools"), endpointArray[0]);
        assertEquals(new URI("http://127.0.0.1:8091/pools"), endpointArray[1]);
        assertEquals(new URI("http://example.com:8091/pools"), endpointArray[2]);
        assertEquals(new URI("http://another-host:8091/pools"), endpointArray[3]);
        assertEquals(4, endpointArray.length);
    }

    @Test
    public void testCouchbaseAdditionalHostsWithSpaces() throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("additionalHosts", " 127.0.0.1, example.com, another-host ");
        String uri = "couchbase:http://localhost/bucket?param=true";
        String remaining = "http://localhost/bucket?param=true";

        CouchbaseEndpoint endpoint = new CouchbaseComponent(context).createEndpoint(uri, remaining, params);

        URI[] endpointArray = endpoint.makeBootstrapURI();
        assertEquals(new URI("http://localhost:8091/pools"), endpointArray[0]);
        assertEquals(new URI("http://127.0.0.1:8091/pools"), endpointArray[1]);
        assertEquals(new URI("http://example.com:8091/pools"), endpointArray[2]);
        assertEquals(new URI("http://another-host:8091/pools"), endpointArray[3]);
        assertEquals(4, endpointArray.length);
    }

    @Test
    public void testCouchbaseDuplicateAdditionalHosts() throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("additionalHosts", "127.0.0.1,localhost, localhost");
        String uri = "couchbase:http://localhost/bucket?param=true";
        String remaining = "http://localhost/bucket?param=true";

        CouchbaseEndpoint endpoint = new CouchbaseComponent(context).createEndpoint(uri, remaining, params);
        URI[] endpointArray = endpoint.makeBootstrapURI();
        assertEquals(2, endpointArray.length);
        assertEquals(new URI("http://localhost:8091/pools"), endpointArray[0]);
        assertEquals(new URI("http://127.0.0.1:8091/pools"), endpointArray[1]);
    }

    @Test
    public void testCouchbaseNullAdditionalHosts() throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("additionalHosts", null);
        String uri = "couchbase:http://localhost/bucket?param=true";
        String remaining = "http://localhost/bucket?param=true";

        CouchbaseEndpoint endpoint = new CouchbaseComponent(context).createEndpoint(uri, remaining, params);

        URI[] endpointArray = endpoint.makeBootstrapURI();

        assertEquals(1, endpointArray.length);
    }

}
