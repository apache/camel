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
package org.apache.camel.component.asterisk;

import org.apache.camel.test.junit6.CamelTestSupport;
import org.asteriskjava.manager.ManagerConnection;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks that the ssl option reaches the AMI connection of the consumer and the producer. Creating the connection does
 * not connect to the server, so no Asterisk server is needed.
 */
public class AsteriskSslTest extends CamelTestSupport {

    private static final String URI = "asterisk:myVoIP?hostname=localhost&username=admin&password=secret";

    @Test
    void sslIsOffByDefault() throws Exception {
        AsteriskEndpoint endpoint = context.getEndpoint(URI + "&action=QUEUE_STATUS", AsteriskEndpoint.class);
        assertFalse(endpoint.isSsl());

        assertFalse(managerConnectionOf((AsteriskProducer) endpoint.createProducer()).isSsl());
        assertFalse(managerConnectionOf((AsteriskConsumer) endpoint.createConsumer(exchange -> {
        })).isSsl());
    }

    @Test
    void sslReachesTheManagerConnection() throws Exception {
        AsteriskEndpoint endpoint = context.getEndpoint(URI + "&action=QUEUE_STATUS&ssl=true", AsteriskEndpoint.class);
        assertTrue(endpoint.isSsl());

        ManagerConnection producerConnection = managerConnectionOf((AsteriskProducer) endpoint.createProducer());
        assertTrue(producerConnection.isSsl());
        assertEquals("localhost", producerConnection.getHostname());
        assertEquals("admin", producerConnection.getUsername());

        ManagerConnection consumerConnection = managerConnectionOf((AsteriskConsumer) endpoint.createConsumer(exchange -> {
        }));
        assertTrue(consumerConnection.isSsl());
        assertEquals("localhost", consumerConnection.getHostname());
    }

    private static ManagerConnection managerConnectionOf(AsteriskProducer producer) {
        AsteriskConnection connection = producer.getConnection();
        connection.connect();
        return connection.getManagerConnection();
    }

    private static ManagerConnection managerConnectionOf(AsteriskConsumer consumer) {
        AsteriskConnection connection = consumer.getConnection();
        connection.connect();
        return connection.getManagerConnection();
    }
}
