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
package org.apache.camel.component.rabbitmq;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.fail;

public class RabbitConsumerHangTest {

    private RabbitMQConsumer consumer = Mockito.mock(RabbitMQConsumer.class);
    private RabbitMQEndpoint endpoint = Mockito.mock(RabbitMQEndpoint.class);
    private Connection conn = Mockito.mock(Connection.class);
    private Channel channel = Mockito.mock(Channel.class);

    @Test(timeout = 5000)
    public void testHandleDeliveryShouldNotHangForeverIfChanelWasClosed() throws Exception {
        Mockito.when(consumer.getEndpoint()).thenReturn(endpoint);
        Mockito.when(consumer.getConnection()).thenReturn(conn);
        Mockito.when(conn.createChannel()).thenReturn(channel);
        Mockito.when(channel.isOpen()).thenReturn(false).thenReturn(true);
        Mockito.when(consumer.getEndpoint()).thenReturn(endpoint);

        RabbitConsumer rabbitConsumer = new RabbitConsumer(consumer);

        rabbitConsumer.handleDelivery(null, null, null, null);
        // will now fail with some NPE which is expected as we have not mocked
        // all the inner details
        try {
            rabbitConsumer.handleDelivery(null, null, null, null);
            fail("Should have thrown NPE");
        } catch (NullPointerException e) {
            // expected
        }

        rabbitConsumer.stop();
    }
}
