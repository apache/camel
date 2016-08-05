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
package org.apache.camel.component.apns.spring;

import com.notnoop.apns.utils.ApnsServerStub;

import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.component.apns.model.InactiveDevice;
import org.apache.camel.component.apns.util.ApnsUtils;
import org.apache.camel.component.apns.util.TestConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

/**
 * Unit test that we can produce JMS message from files
 */
@ContextConfiguration
public class SpringApnsConsumerTest extends AbstractJUnit4SpringContextTests {

    @Autowired
    protected CamelContext camelContext;

    @EndpointInject(uri = "mock:result")
    protected MockEndpoint mock;

    private ApnsServerStub server;

    @Before
    public void startup() throws InterruptedException {
        server = ApnsUtils.prepareAndStartServer(TestConstants.TEST_GATEWAY_PORT, TestConstants.TEST_FEEDBACK_PORT);
    }

    @After
    public void stop() {
        server.stop();
    }

    @Test(timeout = 5000)
    public void testConsumer() throws Exception {

        byte[] deviceTokenBytes = ApnsUtils.createRandomDeviceTokenBytes();
        String deviceToken = ApnsUtils.encodeHexToken(deviceTokenBytes);

        mock.expectedMessageCount(1);
        mock.message(0).body().isInstanceOf(InactiveDevice.class);

        byte[] feedBackBytes = ApnsUtils.generateFeedbackBytes(deviceTokenBytes);
        server.getToSend().write(feedBackBytes);

        Thread.sleep(1000);

        mock.assertIsSatisfied();

        InactiveDevice inactiveDevice = (InactiveDevice)mock.getExchanges().get(0).getIn().getBody();
        Assert.assertNotNull(inactiveDevice);
        Assert.assertNotNull(inactiveDevice.getDate());
        Assert.assertNotNull(inactiveDevice.getDeviceToken());
        Assert.assertEquals(deviceToken, inactiveDevice.getDeviceToken());
    }

}
