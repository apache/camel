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
package org.apache.camel.component.apns;

import com.notnoop.apns.APNS;
import com.notnoop.apns.ApnsService;
import com.notnoop.apns.EnhancedApnsNotification;
import com.notnoop.apns.utils.ApnsServerStub;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.apns.factory.ApnsServiceFactory;
import org.apache.camel.component.apns.util.ApnsUtils;
import org.apache.camel.component.apns.util.TestConstants;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test that we can produce JMS message from files
 */
public class ApnsProducerWithoutTokensHeaderTest extends CamelTestSupport {

    private static final String FAKE_TOKEN = "19308314834701ACD8313AEBD92AEFDE192120371FE13982392831701318B943";

    private ApnsServerStub server;

    @Before
    public void startup() {
        server = ApnsUtils.prepareAndStartServer(TestConstants.TEST_GATEWAY_PORT, TestConstants.TEST_FEEDBACK_PORT);
    }

    @After
    public void stop() {
        server.stop();
    }

    @Test(timeout = 5000)
    public void testProducerWithoutTokenHeader() throws Exception {
        String message = "Hello World";
        String messagePayload = APNS.newPayload().alertBody(message).build();

        EnhancedApnsNotification apnsNotification = new EnhancedApnsNotification(1, EnhancedApnsNotification.MAXIMUM_EXPIRY, FAKE_TOKEN, messagePayload);
        server.stopAt(apnsNotification.length());

        template.sendBody("direct:test", message);

        server.getMessages().acquire();
        assertArrayEquals(apnsNotification.marshall(), server.getReceived().toByteArray());
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        ApnsServiceFactory apnsServiceFactory = ApnsUtils.createDefaultTestConfiguration(camelContext);
        ApnsService apnsService = apnsServiceFactory.getApnsService();

        ApnsComponent apnsComponent = new ApnsComponent(apnsService);
        camelContext.addComponent("apns", apnsComponent);

        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("direct:test")
                        .to("apns:notify?tokens=" + FAKE_TOKEN);
            }
        };
    }
}
