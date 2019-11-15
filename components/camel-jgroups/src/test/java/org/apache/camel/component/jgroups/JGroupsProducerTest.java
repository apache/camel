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
package org.apache.camel.component.jgroups;

import java.util.concurrent.TimeUnit;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.jgroups.JChannel;
import org.jgroups.Message;
import org.jgroups.ReceiverAdapter;
import org.junit.After;
import org.junit.Test;

public class JGroupsProducerTest extends CamelTestSupport {
   
    static final String CLUSTER_NAME = "CLUSTER_NAME";

    static final String MESSAGE = "MESSAGE";

    // Fixtures

    JChannel channel;

    Object messageReceived;

    // Routes fixture

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("jgroups:" + CLUSTER_NAME);
            }
        };
    }

    // Fixture setup

    @Override
    protected void doPreSetup() throws Exception {
        super.doPreSetup();
        channel = new JChannel();
        channel.setReceiver(new ReceiverAdapter() {
            @Override
            public void receive(Message msg) {
                messageReceived = msg.getObject();
            }
        });
        channel.connect(CLUSTER_NAME);
    }

    @Override
    @After
    public void tearDown() throws Exception {
        channel.close();
        super.tearDown();
    }

    @Test
    public void shouldReceiveMulticastedBody() throws Exception {
        // When
        sendBody("direct:start", MESSAGE);

        // Then
        waitForMulticastChannel(5);
        assertEquals(MESSAGE, messageReceived);
    }

    @Test
    public void shouldNotSendNullMessage() throws Exception {
        // When
        sendBody("direct:start", null);

        // Then
        waitForMulticastChannel(2);
        assertNull(messageReceived);
    }

    // Helpers

    private void waitForMulticastChannel(int attempts) throws InterruptedException {
        while (messageReceived == null && attempts > 0) {
            TimeUnit.SECONDS.sleep(1);
            attempts--;
        }
    }

}
