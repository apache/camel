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
package org.apache.camel.component.apns.spring;

import com.notnoop.apns.utils.ApnsServerStub;
import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.component.apns.model.InactiveDevice;
import org.apache.camel.component.apns.util.ApnsUtils;
import org.apache.camel.component.apns.util.TestConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Unit test that we can produce JMS message from files
 */
@ContextConfiguration
@ExtendWith(SpringExtension.class)
public class SpringApnsConsumerTest {

    @Autowired
    protected CamelContext camelContext;

    @EndpointInject("mock:result")
    protected MockEndpoint mock;

    private ApnsServerStub server;

    @BeforeEach
    public void startup() throws InterruptedException {
        server = ApnsUtils.prepareAndStartServer(TestConstants.TEST_GATEWAY_PORT, TestConstants.TEST_FEEDBACK_PORT);
    }

    @AfterEach
    public void stop() {
        server.stop();
    }

    @Test
    @Timeout(5)
    public void testConsumer() throws Exception {

        byte[] deviceTokenBytes = ApnsUtils.createRandomDeviceTokenBytes();
        String deviceToken = ApnsUtils.encodeHexToken(deviceTokenBytes);

        mock.expectedMessageCount(1);
        mock.message(0).body().isInstanceOf(InactiveDevice.class);

        byte[] feedBackBytes = ApnsUtils.generateFeedbackBytes(deviceTokenBytes);
        server.getToSend().write(feedBackBytes);

        Thread.sleep(1000);

        mock.assertIsSatisfied();

        InactiveDevice inactiveDevice = (InactiveDevice) mock.getExchanges().get(0).getIn().getBody();
        assertNotNull(inactiveDevice);
        assertNotNull(inactiveDevice.getDate());
        assertNotNull(inactiveDevice.getDeviceToken());
        assertEquals(deviceToken, inactiveDevice.getDeviceToken());
    }

}
