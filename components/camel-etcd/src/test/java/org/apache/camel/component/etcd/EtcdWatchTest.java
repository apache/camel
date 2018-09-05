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

import mousio.etcd4j.EtcdClient;
import mousio.etcd4j.responses.EtcdErrorCode;
import mousio.etcd4j.responses.EtcdException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class EtcdWatchTest extends EtcdTestSupport {

    @Test
    public void testWatchWithPath() throws Exception {
        testWatch("mock:watch-with-path", "/myKey1", 10);
    }

    @Test
    public void testWatchWithConfigPath() throws Exception {
        testWatch("mock:watch-with-config-path", "/myKey2", 10);
    }

    @Test
    public void testWatchRecursive() throws Exception {
        testWatch("mock:watch-recursive", "/recursive/myKey1", 10);
    }

    @Test
    public void testWatchRecovery() throws Exception {
        final String key = "/myKeyRecovery";
        final EtcdClient client = getClient();

        try {
            // Delete the key if present
            client.delete(key).send().get();
        } catch (EtcdException e) {
            if (!e.isErrorCode(EtcdErrorCode.KeyNotFound)) {
                throw e;
            }
        }

        // Fill the vent backlog ( > 1000)
        for (int i = 0; i < 2000; i++) {
            client.put(key, "v" + i).send().get();
        }

        context().startRoute("watchRecovery");

        testWatch("mock:watch-recovery", key, 10);
    }

    @Test
    public void testWatchWithTimeout() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:watch-with-timeout");
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived(EtcdConstants.ETCD_NAMESPACE, EtcdNamespace.watch.name());
        mock.expectedHeaderReceived(EtcdConstants.ETCD_PATH, "/timeoutKey");
        mock.expectedHeaderReceived(EtcdConstants.ETCD_TIMEOUT, true);
        mock.allMessages().body().isNull();
        mock.assertIsSatisfied();
    }

    private void testWatch(String mockEndpoint, final String key, int updates) throws Exception {
        final String[] values = new String[updates];
        for (int i = 0; i < updates; i++) {
            values[i] = key + "=myValue-" + i;
        }

        MockEndpoint mock = getMockEndpoint(mockEndpoint);
        mock.expectedMessageCount(2);
        mock.expectedHeaderReceived(EtcdConstants.ETCD_NAMESPACE, EtcdNamespace.watch.name());
        mock.expectedHeaderReceived(EtcdConstants.ETCD_PATH, key);
        mock.expectedBodiesReceived(values);

        final EtcdClient client = getClient();
        for (int i = 0; i < updates; i++) {
            client.put(key, "myValue-" + i).send().get();
        }

        mock.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("etcd:watch/myKey1")
                    .process(NODE_TO_VALUE_IN)
                    .to("mock:watch-with-path");
                fromF("etcd:watch/myKeyRecovery?timeout=%s&fromIndex=%s", 1000 * 60 * 5, 1)
                    .id("watchRecovery")
                    .autoStartup(false)
                    .process(NODE_TO_VALUE_IN)
                    .to("mock:watch-recovery");
                from("etcd:watch/recursive?recursive=true")
                   .process(NODE_TO_VALUE_IN)
                    .to("log:org.apache.camel.component.etcd?level=INFO")
                    .to("mock:watch-recursive");
                from("etcd:watch/myKey2")
                    .process(NODE_TO_VALUE_IN)
                    .to("mock:watch-with-config-path");
                from("etcd:watch/timeoutKey?timeout=250&sendEmptyExchangeOnTimeout=true")
                    .to("mock:watch-with-timeout");
            }
        };
    }
}
