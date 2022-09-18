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
package org.apache.camel.component.undertow.ws;

import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.undertow.BaseUndertowTest;
import org.apache.camel.component.undertow.UndertowConstants;
import org.apache.camel.test.infra.common.http.WebsocketTestClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class UndertowWsProducerRouteTest extends BaseUndertowTest {
    @Produce("direct:shop")
    private ProducerTemplate producer;

    @Test
    public void testWSHttpCall() throws Exception {
        WebsocketTestClient testClient = new WebsocketTestClient("ws://localhost:" + getPort() + "/shop");
        testClient.connect();

        // Send message to the direct endpoint
        producer.sendBodyAndHeader("Beer on stock at Apache Mall", UndertowConstants.SEND_TO_ALL, "true");

        assertTrue(testClient.await(10));

        assertEquals(1, testClient.getReceived().size());
        Object r = testClient.getReceived().get(0);
        assertTrue(r instanceof String);
        assertEquals("Beer on stock at Apache Mall", r);

        testClient.close();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:shop").log(">>> Message received from Shopping center : ${body}")
                        .to("undertow:ws://localhost:" + getPort() + "/shop");
            }
        };
    }
}
