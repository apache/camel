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

package org.apache.camel.component.mllp;

import java.util.Dictionary;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.apache.camel.EndpointInject;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultComponentResolver;
import org.apache.camel.spi.ComponentResolver;

import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.blueprint.CamelBlueprintTestSupport;

import org.apache.camel.test.junit.rule.mllp.MllpServerResource;

import org.apache.camel.test.mllp.Hl7TestMessageGenerator;
import org.apache.camel.util.KeyValueHolder;
import org.junit.Rule;
import org.junit.Test;

public class MllpTcpClientProducerBlueprintTest extends CamelBlueprintTestSupport {
    static final String SOURCE_URI = "direct-vm://source";
    static final String MOCK_ACKNOWLEDGED_URI = "mock://acknowledged";
    static final String MOCK_TIMEOUT_URI = "mock://timeoutError-ex";
    static final String MOCK_AE_EX_URI = "mock://ae-ack";
    static final String MOCK_AR_EX_URI = "mock://ar-ack";
    static final String MOCK_FRAME_EX_URI = "mock://frameError-ex";

    @Rule
    public MllpServerResource mllpServer = new MllpServerResource("0.0.0.0", AvailablePortFinder.getNextAvailable());

    @EndpointInject(uri = MOCK_ACKNOWLEDGED_URI)
    MockEndpoint acknowledged;

    @EndpointInject(uri = MOCK_TIMEOUT_URI)
    MockEndpoint timeout;

    @EndpointInject(uri = MOCK_AE_EX_URI)
    MockEndpoint ae;

    @EndpointInject(uri = MOCK_AR_EX_URI)
    MockEndpoint ar;

    @EndpointInject(uri = MOCK_FRAME_EX_URI)
    MockEndpoint frame;

    @Override
    protected void addServicesOnStartup(Map<String, KeyValueHolder<Object, Dictionary>> services) {
        ComponentResolver testResolver = new DefaultComponentResolver();

        services.put(ComponentResolver.class.getName(), asService(testResolver, "component", "mllp"));
    }


    @Override
    protected String setConfigAdminInitialConfiguration(Properties props) {
        props.setProperty("sourceUri", SOURCE_URI);
        props.setProperty("acknowledgedUri", MOCK_ACKNOWLEDGED_URI);
        props.setProperty("timeoutUri", MOCK_TIMEOUT_URI);
        props.setProperty("errorAcknowledgementUri", MOCK_AE_EX_URI);
        props.setProperty("rejectAcknowledgementUri", MOCK_AR_EX_URI);

        props.setProperty("mllp.port", Integer.toString(mllpServer.getListenPort()));

        return "MllpTcpClientProducer";
    }


    @Override
    protected String getBlueprintDescriptor() {
        return "OSGI-INF/blueprint/mllp-tcp-client-producer-test.xml";
    }

    @Test()
    public void testSendMultipleMessages() throws Exception {
        int messageCount = 500;
        acknowledged.expectedMessageCount(messageCount);
        timeout.expectedMessageCount(0);
        frame.expectedMessageCount(0);
        ae.expectedMessageCount(0);
        ar.expectedMessageCount(0);

        startCamelContext();
        // Uncomment one of these lines to see the NACKs handled
        // mllpServer.setSendApplicationRejectAcknowledgementModulus(10);
        // mllpServer.setSendApplicationErrorAcknowledgementModulus(10);

        for (int i = 0; i < messageCount; ++i) {
            log.debug("Triggering message {}", i);
            // Thread.sleep(5000);
            Object response = template.requestBodyAndHeader(SOURCE_URI, Hl7TestMessageGenerator.generateMessage(i), "CamelMllpMessageControlId", String.format("%05d", i));
            log.debug("response {}\n{}", i, response);
        }

        assertMockEndpointsSatisfied(15, TimeUnit.SECONDS);
    }

}
