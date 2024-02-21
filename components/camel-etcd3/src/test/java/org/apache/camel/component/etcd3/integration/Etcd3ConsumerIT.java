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

import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.KeyValue;
import io.etcd.jetcd.kv.GetResponse;
import io.etcd.jetcd.options.GetOption;
import io.etcd.jetcd.watch.WatchEvent;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.etcd3.Etcd3Constants;
import org.apache.camel.component.etcd3.support.Etcd3TestSupport;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

class Etcd3ConsumerIT extends Etcd3TestSupport {

    private static final int BATCH_SIZE_PART_1 = 2_345;
    private static final String ENCODING_TEST_KEY = "/éç";
    private static final Processor NODE_TO_VALUE_IN_ISO_8859_1 = exchange -> {
        WatchEvent event = exchange.getMessage().getBody(WatchEvent.class);
        if (event != null) {
            KeyValue keyValue = event.getKeyValue();
            exchange.getMessage().setBody(keyValue.getKey().toString(StandardCharsets.ISO_8859_1) + "="
                                          + keyValue.getValue().toString(StandardCharsets.ISO_8859_1));
        }
    };

    @Test
    void testWatchWithPath() throws Exception {
        testWatch("mock:watch-with-path", "/myKey1", 10);
    }

    @Test
    void testWatchWithConfigPath() throws Exception {
        testWatch("mock:watch-with-config-path", "/myKey2", 11);
    }

    @Test
    void testWatchPrefix() throws Exception {
        testWatch("mock:watch-prefix", "/prefix/myKey1", 12);
    }

    @Test
    void testWatchEncoding() throws Exception {
        String value = "àè";
        MockEndpoint mock = getMockEndpoint("mock:watch-encoding");
        mock.expectedHeaderReceived(Etcd3Constants.ETCD_PATH, ENCODING_TEST_KEY);
        mock.expectedBodiesReceived(String.format("%s=%s", ENCODING_TEST_KEY, value));

        final Client client = getClient();
        client.getKVClient().put(
                ByteSequence.from(ENCODING_TEST_KEY.getBytes(StandardCharsets.ISO_8859_1)),
                ByteSequence.from(value.getBytes(StandardCharsets.ISO_8859_1))).get();

        mock.assertIsSatisfied();
    }

    @Test
    void testWatchRecovery() throws Exception {
        final String key = "/myKeyRecovery";
        final Client client = getClient();

        final int batchSizePart2 = 13;
        // Fill the vent backlog ( > 1000)
        for (int i = 0; i < BATCH_SIZE_PART_1 + batchSizePart2; i++) {
            client.getKVClient().put(ByteSequence.from(key.getBytes()), ByteSequence.from(("v" + i).getBytes())).get();
            client.getKVClient().put(ByteSequence.from((key + "/" + i).getBytes()), ByteSequence.from("v".getBytes())).get();
        }

        final Object[] values = new String[batchSizePart2];
        for (int i = 0; i < values.length; i++) {
            values[i] = key + "=v" + (i + BATCH_SIZE_PART_1);
        }
        MockEndpoint mock = getMockEndpoint("mock:watch-recovery");
        mock.expectedHeaderReceived(Etcd3Constants.ETCD_PATH, key);
        mock.expectedBodiesReceived(values);
        context().getRouteController().startRoute("watchRecovery");

        mock.assertIsSatisfied();
    }

    private void testWatch(String mockEndpoint, final String key, int updates) throws Exception {
        final Object[] values = new String[updates];
        for (int i = 0; i < updates; i++) {
            values[i] = key + "=myValue-" + i;
        }

        MockEndpoint mock = getMockEndpoint(mockEndpoint);
        mock.expectedHeaderReceived(Etcd3Constants.ETCD_PATH, key);
        mock.expectedBodiesReceivedInAnyOrder(values);

        final Client client = getClient();
        for (int i = 0; i < updates; i++) {
            client.getKVClient().put(ByteSequence.from(key.getBytes()), ByteSequence.from(("myValue-" + i).getBytes())).get();
        }

        mock.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                GetResponse response = getClient().getKVClient().get(
                        ByteSequence.from("/".getBytes()), GetOption.newBuilder().isPrefix(true).withCountOnly(true).build())
                        .get();
                // To make sure that the recovery test won't be affected by other tests
                long toSkip = response.getCount() == 0 ? 0L : response.getHeader().getRevision();
                from("etcd3:myKey1")
                        .process(NODE_TO_VALUE_IN)
                        .to("mock:watch-with-path");
                fromF("etcd3:myKeyRecovery?fromIndex=%s", toSkip + 2 * BATCH_SIZE_PART_1 + 1)
                        .id("watchRecovery")
                        .autoStartup(false)
                        .process(NODE_TO_VALUE_IN)
                        .to("mock:watch-recovery");
                from("etcd3:prefix?prefix=true")
                        .process(NODE_TO_VALUE_IN)
                        .to("log:org.apache.camel.component.etcd?level=INFO")
                        .to("mock:watch-prefix");
                from("etcd3:myKey2")
                        .process(NODE_TO_VALUE_IN)
                        .to("mock:watch-with-config-path");
                fromF("etcd3:%s?keyCharset=ISO-8859-1", ENCODING_TEST_KEY)
                        .process(NODE_TO_VALUE_IN_ISO_8859_1)
                        .to("mock:watch-encoding");
            }
        };
    }
}
