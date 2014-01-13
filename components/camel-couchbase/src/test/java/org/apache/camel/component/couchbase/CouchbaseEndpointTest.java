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

import org.junit.Test;

import static org.apache.camel.component.couchbase.CouchbaseConstants.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CouchbaseEndpointTest {

    @Test
    public void assertSingleton() throws Exception {
        CouchbaseEndpoint endpoint = new CouchbaseEndpoint("couchbase:http://localhost/bucket", "http://localhost/bucket", new CouchbaseComponent());
        assertTrue(endpoint.isSingleton());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBucketRequired() throws Exception {
        new CouchbaseEndpoint("couchbase:http://localhost:80", "http://localhost:80", new CouchbaseComponent());
    }

    @Test
    public void testDefaultPortIsSet() throws Exception {
        CouchbaseEndpoint endpoint = new CouchbaseEndpoint("couchbase:http://localhost/bucket", "http://localhost/bucket", new CouchbaseComponent());
        assertEquals(COUCHBASE_DEFAULT_PORT, endpoint.getPort());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testHostnameRequired() throws Exception {
        new CouchbaseEndpoint("couchbase:http://:80/bucket", "couchbase://:80/bucket", new CouchbaseComponent());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSchemeRequired() throws Exception {
        new CouchbaseEndpoint("couchdb:localhost:80/bucket", "localhost:80/bucket", new CouchbaseComponent());
    }

}
