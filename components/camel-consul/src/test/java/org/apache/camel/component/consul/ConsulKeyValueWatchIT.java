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
package org.apache.camel.component.consul;

import java.security.SecureRandom;
import java.util.List;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import org.kiwiproject.consul.KeyValueClient;

public class ConsulKeyValueWatchIT extends ConsulTestSupport {
    private String key;
    private KeyValueClient client;
    private SecureRandom random;

    @Override
    public void doPreSetup() {
        key = generateKey();
        client = getConsul().keyValueClient();
        random = new SecureRandom();
    }

    @Test
    public void testWatchKey() throws Exception {
        List<String> values = generateRandomListOfStrings(3);

        MockEndpoint mock = getMockEndpoint("mock:kv-watch");
        mock.expectedMessageCount(values.size());
        mock.expectedBodiesReceived(values);
        mock.expectedHeaderReceived(ConsulConstants.CONSUL_RESULT, true);

        for (String val : values) {
            client.putValue(key, val);
            Thread.sleep(250 + random.nextInt(250));
        }

        mock.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                fromF("consul:kv?key=%s&valueAsString=true", key)
                        .to("log:org.apache.camel.component.consul?level=INFO&showAll=true").to("mock:kv-watch");
            }
        };
    }
}
