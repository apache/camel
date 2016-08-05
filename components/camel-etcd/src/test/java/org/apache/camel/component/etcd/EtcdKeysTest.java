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
package org.apache.camel.component.etcd;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import mousio.etcd4j.EtcdClient;
import mousio.etcd4j.responses.EtcdException;
import mousio.etcd4j.responses.EtcdKeysResponse;
import org.apache.camel.Exchange;
import org.apache.camel.Predicate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class EtcdKeysTest extends EtcdTestSupport {

    @Test(expected = EtcdException.class)
    public void testKeys() throws Exception {
        final String path = "/camel/" + UUID.randomUUID().toString();
        final String value = UUID.randomUUID().toString();
        final EtcdClient client = getClient();
        final Map<String, Object> headers = new HashMap<>();

        // *******************************************
        // SET
        // *******************************************

        headers.clear();
        headers.put(EtcdConstants.ETCD_ACTION, EtcdConstants.ETCD_KEYS_ACTION_SET);
        headers.put(EtcdConstants.ETCD_PATH, path);

        sendBody("direct:keys-set", value, headers);

        MockEndpoint mockSet = getMockEndpoint("mock:result-set");
        mockSet.expectedMinimumMessageCount(1);
        mockSet.expectedHeaderReceived(EtcdConstants.ETCD_NAMESPACE, EtcdNamespace.keys.name());
        mockSet.expectedHeaderReceived(EtcdConstants.ETCD_PATH, path);
        mockSet.assertIsSatisfied();

        // *******************************************
        // GET
        // *******************************************

        headers.clear();
        headers.put(EtcdConstants.ETCD_ACTION, EtcdConstants.ETCD_KEYS_ACTION_GET);
        headers.put(EtcdConstants.ETCD_PATH, path);

        sendBody("direct:keys-get", value, headers);

        MockEndpoint mockGet = getMockEndpoint("mock:result-get");
        mockGet.expectedMinimumMessageCount(1);
        mockSet.expectedHeaderReceived(EtcdConstants.ETCD_NAMESPACE, EtcdNamespace.keys.name());
        mockGet.expectedHeaderReceived(EtcdConstants.ETCD_PATH, path);
        mockGet.expectedMessagesMatches(new Predicate() {
            @Override
            public boolean matches(Exchange exchange) {
                EtcdKeysResponse keysResponse = exchange.getIn().getBody(EtcdKeysResponse.class);
                assertNotNull(keysResponse);
                assertNotNull(keysResponse.node);
                assertNotNull(keysResponse.node.value);

                return keysResponse.node.value.equals(value);
            }
        });

        mockGet.assertIsSatisfied();

        // *******************************************
        // DELETE
        // *******************************************

        headers.clear();
        headers.put(EtcdConstants.ETCD_ACTION, EtcdConstants.ETCD_KEYS_ACTION_DELETE);
        headers.put(EtcdConstants.ETCD_PATH, path);

        sendBody("direct:keys-del", "value", headers);

        MockEndpoint mockDel = getMockEndpoint("mock:result-del");
        mockDel.expectedMinimumMessageCount(1);
        mockSet.expectedHeaderReceived(EtcdConstants.ETCD_NAMESPACE, EtcdNamespace.keys.name());
        mockDel.expectedHeaderReceived(EtcdConstants.ETCD_PATH, path);
        mockDel.assertIsSatisfied();

        // *******************************************
        // VALIDATION
        // *******************************************

        client.get(path).send().get();

        fail("EtcdException should have been thrown");
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("direct:keys-set")
                    .to("etcd:keys")
                        .to("mock:result-set");
                from("direct:keys-get")
                    .to("etcd:keys")
                        .to("mock:result-get");
                from("direct:keys-del")
                    .to("etcd:keys")
                        .to("mock:result-del");
            }
        };
    }
}
