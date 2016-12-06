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
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultComponentResolver;
import org.apache.camel.spi.ComponentResolver;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.blueprint.CamelBlueprintTestSupport;
import org.apache.camel.test.junit.rule.mllp.MllpServerResource;
import org.apache.camel.util.KeyValueHolder;
import org.junit.Rule;
import org.junit.Test;

import static org.apache.camel.test.mllp.Hl7MessageGenerator.generateMessage;

public class MllpTcpClientProducerBlueprintTest extends CamelBlueprintTestSupport {
    @Rule
    public MllpServerResource mllpServer = new MllpServerResource("localhost", AvailablePortFinder.getNextAvailable());

    final String sourceUri = "direct://source";
    final String mockAcknowledgedUri = "mock://acknowledged";
    final String mockTimeoutUri = "mock://timeoutError-ex";
    final String mockAeExUri = "mock://ae-ack";
    final String mockArExUri = "mock://ar-ack";
    final String mockFrameExUri = "mock://frameError-ex";

    @EndpointInject(uri = sourceUri)
    ProducerTemplate source;
    @EndpointInject(uri = mockAcknowledgedUri)
    MockEndpoint acknowledged;
    @EndpointInject(uri = mockTimeoutUri)
    MockEndpoint timeout;
    @EndpointInject(uri = mockAeExUri)
    MockEndpoint ae;
    @EndpointInject(uri = mockArExUri)
    MockEndpoint ar;
    @EndpointInject(uri = mockFrameExUri)
    MockEndpoint frame;

    @Override
    protected String getBlueprintDescriptor() {
        return "OSGI-INF/blueprint/mllp-tcp-client-producer-test.xml";
    }

    @Override
    protected Properties useOverridePropertiesWithPropertiesComponent() {
        Properties props = new Properties();

        props.setProperty("sourceUri", sourceUri);
        props.setProperty("acknowledgedUri", mockAcknowledgedUri);
        props.setProperty("timeoutUri", mockTimeoutUri);
        props.setProperty("frameErrorUri", mockFrameExUri);
        props.setProperty("errorAcknowledgementUri", mockAeExUri);
        props.setProperty("rejectAcknowledgementUri", mockArExUri);

        props.setProperty("mllp.port", Integer.toString(mllpServer.getListenPort()));

        return props;
    }

    /*
        This doesn't seem to work
        @Override
        protected String useOverridePropertiesWithConfigAdmin(Dictionary props) throws Exception {

            props.put("mllp.port", mllpServer.getListenPort() );

            return "MllpTcpClientProducer";
        }
    */

    @Override
    protected void addServicesOnStartup(Map<String, KeyValueHolder<Object, Dictionary>> services) {
        ComponentResolver testResolver = new DefaultComponentResolver();

        services.put(ComponentResolver.class.getName(), asService(testResolver, "component", "mllp"));
    }

    @Test()
    public void testSendMultipleMessages() throws Exception {
        int messageCount = 500;
        acknowledged.expectedMessageCount(messageCount);
        timeout.expectedMessageCount(0);
        frame.expectedMessageCount(0);
        ae.expectedMessageCount(0);
        ar.expectedMessageCount(0);

        // Uncomment one of these lines to see the NACKs handled
        // mllpServer.setSendApplicationRejectAcknowledgementModulus(10);
        // mllpServer.setSendApplicationErrorAcknowledgementModulus(10);

        for (int i = 0; i < messageCount; ++i) {
            log.debug("Triggering message {}", i);
            Object response = source.requestBodyAndHeader(generateMessage(i), "CamelMllpMessageControlId", String.format("%05d", i));
            log.debug("response {}\n{}", i, response);
        }

        assertMockEndpointsSatisfied(15, TimeUnit.SECONDS);
    }


}
