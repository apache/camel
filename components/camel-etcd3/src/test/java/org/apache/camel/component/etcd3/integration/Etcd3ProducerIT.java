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
package org.apache.camel.component.etcd3.integration;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.KeyValue;
import io.etcd.jetcd.kv.DeleteResponse;
import io.etcd.jetcd.kv.GetResponse;
import org.apache.camel.Exchange;
import org.apache.camel.Predicate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.etcd3.Etcd3Constants;
import org.apache.camel.component.etcd3.support.Etcd3TestSupport;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Etcd3ProducerIT extends Etcd3TestSupport {

    @Test
    void testKeys() throws Exception {
        final String path = "/camel/" + UUID.randomUUID();
        final String value = UUID.randomUUID().toString();
        final Client client = getClient();
        final Map<String, Object> headers = new HashMap<>();

        // *******************************************
        // SET
        // *******************************************

        headers.put(Etcd3Constants.ETCD_ACTION, Etcd3Constants.ETCD_KEYS_ACTION_SET);
        headers.put(Etcd3Constants.ETCD_PATH, path);

        MockEndpoint mockSet = getMockEndpoint("mock:result-set");
        mockSet.expectedMessageCount(1);
        mockSet.expectedHeaderReceived(Etcd3Constants.ETCD_PATH, path);
        sendBody("direct:keys-set", value, headers);
        mockSet.assertIsSatisfied();

        // *******************************************
        // GET
        // *******************************************

        headers.clear();
        headers.put(Etcd3Constants.ETCD_ACTION, Etcd3Constants.ETCD_KEYS_ACTION_GET);
        headers.put(Etcd3Constants.ETCD_PATH, path);

        MockEndpoint mockGet = getMockEndpoint("mock:result-get");
        mockGet.expectedMessageCount(1);
        mockGet.expectedHeaderReceived(Etcd3Constants.ETCD_PATH, path);
        mockGet.expectedMessagesMatches(new Predicate() {
            @Override
            public boolean matches(Exchange exchange) {
                GetResponse keysResponse = exchange.getIn().getBody(GetResponse.class);
                assertNotNull(keysResponse);
                assertEquals(1, keysResponse.getCount());
                assertNotNull(keysResponse.getKvs());
                assertFalse(keysResponse.getKvs().isEmpty());
                ByteSequence actual = keysResponse.getKvs().get(0).getValue();
                assertNotNull(actual);
                return actual.toString().equals(value);
            }
        });

        sendBody("direct:keys-get", value, headers);
        mockGet.assertIsSatisfied();

        // *******************************************
        // DELETE
        // *******************************************

        headers.clear();
        headers.put(Etcd3Constants.ETCD_ACTION, Etcd3Constants.ETCD_KEYS_ACTION_DELETE);
        headers.put(Etcd3Constants.ETCD_PATH, path);

        MockEndpoint mockDel = getMockEndpoint("mock:result-del");
        mockDel.expectedMessageCount(1);
        mockDel.expectedHeaderReceived(Etcd3Constants.ETCD_PATH, path);
        sendBody("direct:keys-del", "value", headers);
        mockDel.assertIsSatisfied();

        // *******************************************
        // VALIDATION
        // *******************************************

        GetResponse response = client.getKVClient().get(ByteSequence.from(path.getBytes())).get();
        assertNotNull(response);
        assertEquals(0, response.getCount());
    }

    @Test
    void testEncoding() throws Exception {
        final String path = "/âè/" + UUID.randomUUID();
        final String value = "çà" + UUID.randomUUID();
        final Map<String, Object> headers = new HashMap<>();

        // *******************************************
        // SET
        // *******************************************

        headers.put(Etcd3Constants.ETCD_ACTION, Etcd3Constants.ETCD_KEYS_ACTION_SET);
        headers.put(Etcd3Constants.ETCD_PATH, path);
        headers.put(Etcd3Constants.ETCD_KEY_CHARSET, "ISO-8859-1");
        headers.put(Etcd3Constants.ETCD_VALUE_CHARSET, "ISO-8859-1");

        MockEndpoint mockSet = getMockEndpoint("mock:result-set");
        mockSet.expectedMessageCount(1);
        mockSet.expectedHeaderReceived(Etcd3Constants.ETCD_PATH, path);
        sendBody("direct:keys-set", value, headers);
        mockSet.assertIsSatisfied();

        // *******************************************
        // GET
        // *******************************************

        headers.clear();
        headers.put(Etcd3Constants.ETCD_ACTION, Etcd3Constants.ETCD_KEYS_ACTION_GET);
        headers.put(Etcd3Constants.ETCD_PATH, path);
        headers.put(Etcd3Constants.ETCD_KEY_CHARSET, "ISO-8859-1");
        headers.put(Etcd3Constants.ETCD_VALUE_CHARSET, "ISO-8859-1");

        MockEndpoint mockGet = getMockEndpoint("mock:result-get");
        mockGet.expectedMessageCount(1);
        mockGet.expectedHeaderReceived(Etcd3Constants.ETCD_PATH, path);
        mockGet.expectedMessagesMatches(new Predicate() {
            @Override
            public boolean matches(Exchange exchange) {
                GetResponse keysResponse = exchange.getIn().getBody(GetResponse.class);
                assertNotNull(keysResponse);
                assertEquals(1, keysResponse.getCount());
                assertNotNull(keysResponse.getKvs());
                assertFalse(keysResponse.getKvs().isEmpty());
                ByteSequence actual = keysResponse.getKvs().get(0).getValue();
                assertNotNull(actual);
                return actual.toString(StandardCharsets.ISO_8859_1).equals(value);
            }
        });

        sendBody("direct:keys-get", value, headers);
        mockGet.assertIsSatisfied();

        // *******************************************
        // DELETE
        // *******************************************

        headers.clear();
        headers.put(Etcd3Constants.ETCD_ACTION, Etcd3Constants.ETCD_KEYS_ACTION_DELETE);
        headers.put(Etcd3Constants.ETCD_PATH, path);
        headers.put(Etcd3Constants.ETCD_KEY_CHARSET, "ISO-8859-1");
        headers.put(Etcd3Constants.ETCD_VALUE_CHARSET, "ISO-8859-1");

        MockEndpoint mockDel = getMockEndpoint("mock:result-del");
        mockDel.expectedMessageCount(1);
        mockDel.expectedHeaderReceived(Etcd3Constants.ETCD_PATH, path);
        sendBody("direct:keys-del", "value", headers);
        mockDel.assertIsSatisfied();

        // *******************************************
        // VALIDATION
        // *******************************************

        GetResponse response = getClient().getKVClient().get(
                ByteSequence.from(path, StandardCharsets.ISO_8859_1)).get();
        assertNotNull(response);
        assertEquals(0, response.getCount());
    }

    @Test
    void testPrefix() throws Exception {
        final String path = "/camel/" + UUID.randomUUID();
        final String value = UUID.randomUUID().toString();
        final Client client = getClient();
        final Map<String, Object> headers = new HashMap<>();

        // *******************************************
        // SET
        // *******************************************

        headers.put(Etcd3Constants.ETCD_ACTION, Etcd3Constants.ETCD_KEYS_ACTION_SET);
        headers.put(Etcd3Constants.ETCD_PATH, path);

        MockEndpoint mockSet = getMockEndpoint("mock:result-set");
        mockSet.expectedMessageCount(1);
        mockSet.expectedHeaderReceived(Etcd3Constants.ETCD_PATH, path);
        sendBody("direct:keys-set", value, headers);
        mockSet.assertIsSatisfied();

        final String pathChild = String.format("%s/child", path);
        headers.put(Etcd3Constants.ETCD_PATH, pathChild);

        mockSet.reset();
        mockSet.expectedMessageCount(1);
        mockSet.expectedHeaderReceived(Etcd3Constants.ETCD_PATH, pathChild);
        sendBody("direct:keys-set", value, headers);
        mockSet.assertIsSatisfied();

        final String pathSiblingWithSamePrefix = String.format("%s-sibling", path);
        headers.put(Etcd3Constants.ETCD_PATH, pathSiblingWithSamePrefix);

        mockSet.reset();
        mockSet.expectedMessageCount(1);
        mockSet.expectedHeaderReceived(Etcd3Constants.ETCD_PATH, pathSiblingWithSamePrefix);
        sendBody("direct:keys-set", value, headers);
        mockSet.assertIsSatisfied();

        // *******************************************
        // GET
        // *******************************************

        headers.clear();
        headers.put(Etcd3Constants.ETCD_ACTION, Etcd3Constants.ETCD_KEYS_ACTION_GET);
        headers.put(Etcd3Constants.ETCD_PATH, path);

        MockEndpoint mockGet = getMockEndpoint("mock:result-get");
        mockGet.expectedMessageCount(1);
        mockGet.expectedHeaderReceived(Etcd3Constants.ETCD_PATH, path);
        mockGet.expectedMessagesMatches(new Predicate() {
            @Override
            public boolean matches(Exchange exchange) {
                GetResponse keysResponse = exchange.getIn().getBody(GetResponse.class);
                assertNotNull(keysResponse);
                assertEquals(1, keysResponse.getCount());
                assertNotNull(keysResponse.getKvs());
                assertFalse(keysResponse.getKvs().isEmpty());
                ByteSequence actual = keysResponse.getKvs().get(0).getValue();
                assertNotNull(actual);
                return actual.toString().equals(value);
            }
        });

        sendBody("direct:keys-get", value, headers);
        mockGet.assertIsSatisfied();

        headers.put(Etcd3Constants.ETCD_IS_PREFIX, true);

        mockGet.reset();
        mockGet.expectedMessageCount(1);
        mockGet.expectedHeaderReceived(Etcd3Constants.ETCD_PATH, path);
        mockGet.expectedMessagesMatches(new Predicate() {
            @Override
            public boolean matches(Exchange exchange) {
                GetResponse response = exchange.getIn().getBody(GetResponse.class);
                assertNotNull(response);
                assertEquals(3, response.getCount());
                assertNotNull(response.getKvs());
                assertEquals(3, response.getKvs().size());
                Set<String> keys = new HashSet<>(List.of(path, pathChild, pathSiblingWithSamePrefix));
                for (KeyValue kv : response.getKvs()) {
                    ByteSequence actualValue = kv.getValue();
                    assertNotNull(actualValue);
                    assertEquals(value, actualValue.toString());
                    assertTrue(keys.remove(kv.getKey().toString()));
                }
                assertTrue(keys.isEmpty());
                return true;
            }
        });

        sendBody("direct:keys-get", value, headers);
        mockGet.assertIsSatisfied();

        // *******************************************
        // DELETE
        // *******************************************

        headers.clear();
        headers.put(Etcd3Constants.ETCD_ACTION, Etcd3Constants.ETCD_KEYS_ACTION_DELETE);
        headers.put(Etcd3Constants.ETCD_PATH, path);
        headers.put(Etcd3Constants.ETCD_IS_PREFIX, true);

        MockEndpoint mockDel = getMockEndpoint("mock:result-del");
        mockDel.expectedMessageCount(1);
        mockGet.expectedHeaderReceived(Etcd3Constants.ETCD_PATH, path);
        mockGet.expectedMessagesMatches(new Predicate() {
            @Override
            public boolean matches(Exchange exchange) {
                DeleteResponse response = exchange.getIn().getBody(DeleteResponse.class);
                assertNotNull(response);
                assertEquals(3, response.getDeleted());
                return true;
            }
        });
        sendBody("direct:keys-del", "value", headers);
        mockDel.assertIsSatisfied();

        // *******************************************
        // VALIDATION
        // *******************************************

        GetResponse response = client.getKVClient().get(ByteSequence.from(path.getBytes())).get();
        assertNotNull(response);
        assertEquals(0, response.getCount());
        response = client.getKVClient().get(ByteSequence.from(pathChild.getBytes())).get();
        assertNotNull(response);
        assertEquals(0, response.getCount());
        response = client.getKVClient().get(ByteSequence.from(pathSiblingWithSamePrefix.getBytes())).get();
        assertNotNull(response);
        assertEquals(0, response.getCount());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("direct:keys-set")
                        .to("etcd3:dummy")
                        .to("mock:result-set");
                from("direct:keys-get")
                        .to("etcd3:dummy")
                        .to("mock:result-get");
                from("direct:keys-del")
                        .to("etcd3:dummy")
                        .to("mock:result-del");
            }
        };
    }
}
