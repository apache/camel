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
package org.apache.camel.component.couchdb;

import java.util.UUID;

import com.google.gson.JsonObject;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CouchDbEndpointTest {

    @Test
    public void testCreateCouchExchangeHeadersAreSet() throws Exception {
        CouchDbEndpoint endpoint = new CouchDbEndpoint("couchdb:http://localhost/db", "http://localhost/db", new CouchDbComponent(new DefaultCamelContext()));

        String id = UUID.randomUUID().toString();
        String rev = UUID.randomUUID().toString();
        String seq = "seq123";

        JsonObject doc = new JsonObject();
        doc.addProperty("_id", id);
        doc.addProperty("_rev", rev);

        Exchange exchange = endpoint.createExchange(seq, id, doc, false);
        assertEquals(id, exchange.getIn().getHeader(CouchDbConstants.HEADER_DOC_ID));
        assertEquals(rev, exchange.getIn().getHeader(CouchDbConstants.HEADER_DOC_REV));
        assertEquals(seq, exchange.getIn().getHeader(CouchDbConstants.HEADER_SEQ));
        assertEquals("UPDATE", exchange.getIn().getHeader(CouchDbConstants.HEADER_METHOD));
        assertEquals("db", exchange.getIn().getHeader(CouchDbConstants.HEADER_DATABASE));
    }

    @Test
    public void assertSingleton() throws Exception {
        CouchDbEndpoint endpoint = new CouchDbEndpoint("couchdb:http://localhost/db", "http://localhost/db", new CouchDbComponent());
        assertTrue(endpoint.isSingleton());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDbRequired() throws Exception {
        new CouchDbEndpoint("couchdb:http://localhost:80", "http://localhost:80", new CouchDbComponent());
    }

    @Test
    public void testDefaultPortIsSet() throws Exception {
        CouchDbEndpoint endpoint = new CouchDbEndpoint("couchdb:http://localhost/db", "http://localhost/db", new CouchDbComponent());
        assertEquals(CouchDbEndpoint.DEFAULT_PORT, endpoint.getPort());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testHostnameRequired() throws Exception {
        new CouchDbEndpoint("couchdb:http://:80/db", "http://:80/db", new CouchDbComponent());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSchemeRequired() throws Exception {
        new CouchDbEndpoint("couchdb:localhost:80/db", "localhost:80/db", new CouchDbComponent());
    }
}
