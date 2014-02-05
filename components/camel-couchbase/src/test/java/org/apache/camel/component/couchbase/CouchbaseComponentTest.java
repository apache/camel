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

package org.apache.camel.component.couchbase;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(MockitoJUnitRunner.class)
public class CouchbaseComponentTest {

    @Mock
    private CamelContext context;

    @Test
    public void testEndpointCreated() throws Exception {
        Map<String, Object> params = new HashMap<String, Object>();

        String uri = "couchbase:http://localhost:9191/bucket";
        String remaining = "http://localhost:9191/bucket";

        Endpoint endpoint = new CouchbaseComponent(context).createEndpoint(uri, remaining, params);
        assertNotNull(endpoint);
    }

    @Test
    public void testPropertiesSet() throws Exception {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("username", "ugol");
        params.put("password", "pwd");

        String uri = "couchdb:http://localhost:91234/bucket";
        String remaining = "http://localhost:91234/bucket";

        CouchbaseEndpoint endpoint = new CouchbaseComponent(context).createEndpoint(uri, remaining, params);

        assertEquals("http", endpoint.getProtocol());
        assertEquals("localhost", endpoint.getHostname());
        assertEquals("bucket", endpoint.getBucket());
        assertEquals(91234, endpoint.getPort());
        assertEquals("ugol", endpoint.getUsername());
        assertEquals("pwd", endpoint.getPassword());

    }

    @Test
    public void testCouchbaseURI() throws Exception {

        Map<String, Object> params = new HashMap<String, Object>();
        String uri = "couchbase:http://localhost/bucket?param=true";
        String remaining = "http://localhost/bucket?param=true";

        CouchbaseEndpoint endpoint = new CouchbaseComponent(context).createEndpoint(uri, remaining, params);
        assertEquals("http://localhost:8091/pools", endpoint.makeBootstrapURI());

    }
}