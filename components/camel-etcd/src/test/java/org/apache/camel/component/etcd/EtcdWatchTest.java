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
import org.apache.camel.Exchange;
import org.apache.camel.Predicate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Ignore;
import org.junit.Test;

@Ignore("Etcd must be started manually")
public class EtcdWatchTest extends EtcdTest {

    @Test
    public void testWatchWithPath() throws Exception {
        testWatch("mock:watch-with-path", "/myKey1", true);
    }

    @Test
    public void testWatchWithConfigPath() throws Exception {
        testWatch("mock:watch-with-config-path", "/myKey2", true);
    }

    @Test
    public void testWatchRecursive() throws Exception {
        testWatch("mock:watch-recursive", "/recursive/myKey1", true);
    }

    @Test
    public void testWatchWithTimeout() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:watch-with-timeout");
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived(EtcdConstants.ETCD_NAMESPACE, EtcdNamespace.watch.name());
        mock.expectedHeaderReceived(EtcdConstants.ETCD_PATH, "/timeoutKey");
        mock.expectedHeaderReceived(EtcdConstants.ETCD_TIMEOUT, true);
        mock.expectedMessagesMatches(new Predicate() {
            @Override
            public boolean matches(Exchange exchange) {
                return exchange.getIn().getBody() == null;
            }
        });

        mock.assertIsSatisfied();
    }

    private void testWatch(String mockEndpoint, final String key, boolean updateKey) throws Exception {
        MockEndpoint mock = getMockEndpoint(mockEndpoint);
        mock.expectedMessageCount(2);
        mock.expectedHeaderReceived(EtcdConstants.ETCD_NAMESPACE, EtcdNamespace.watch.name());
        mock.expectedHeaderReceived(EtcdConstants.ETCD_PATH, key);
        mock.expectedMessagesMatches(new Predicate() {
            @Override
            public boolean matches(Exchange exchange) {
                return exchange.getIn().getBody(String.class).startsWith(key + "=myValue-");
            }
        });

        if (updateKey) {
            EtcdClient client = getClient();
            client.put(key, "myValue-1").send().get();
            Thread.sleep(250);
            client.put(key, "myValue-2").send().get();
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
