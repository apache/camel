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

import java.util.Optional;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.consul.endpoint.ConsulKeyValueActions;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConsulKeyValueTest extends ConsulTestSupport {

    @Test
    public void testKeyPut() throws Exception {
        String key = generateKey();
        String val = generateRandomString();

        MockEndpoint mock = getMockEndpoint("mock:kv");
        mock.expectedMinimumMessageCount(1);
        mock.expectedBodiesReceived(val);
        mock.expectedHeaderReceived(ConsulConstants.CONSUL_RESULT, true);

        fluentTemplate().withHeader(ConsulConstants.CONSUL_ACTION, ConsulKeyValueActions.PUT).withHeader(ConsulConstants.CONSUL_KEY, key).withBody(val).to("direct:kv").send();

        mock.assertIsSatisfied();

        Optional<String> keyVal = getConsul().keyValueClient().getValueAsString(key);

        assertTrue(keyVal.isPresent());
        assertEquals(val, keyVal.get());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:kv").to("consul:kv").to("mock:kv");
            }
        };
    }
}
