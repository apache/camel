/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.mllp;

import org.apache.camel.EndpointInject;
import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.blueprint.CamelBlueprintTestSupport;
import org.apache.camel.test.junit.rule.mllp.MllpServerResource;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

import java.util.Dictionary;
import java.util.concurrent.TimeUnit;

import static org.apache.camel.test.Hl7MessageGenerator.generateMessage;

@Ignore( value = "Diagnosing intermittent Context startup/timeout errors - Bundle MllpTcpClientProducerBlueprintTest/1.0.0 is waiting for dependencies [(&(component=mllp)(objectClass=org.apache.camel.spi.ComponentResolver))]")
public class MllpTcpClientProducerBlueprintTest extends CamelBlueprintTestSupport {

    @Rule
    public MllpServerResource mllpServer = new MllpServerResource();

    String targetURI = "direct://source";

    @EndpointInject(uri = "mock://acknowledged")
    MockEndpoint acknowledged;

    @EndpointInject(uri = "mock://timeout-ex")
    MockEndpoint timeout;

    @EndpointInject(uri = "mock://frame-ex")
    MockEndpoint frame;

    @Override
    protected String getBlueprintDescriptor() {
        return "OSGI-INF/blueprint/mllp-tcp-client-producer-test.xml";
    }

    @Override
    protected String useOverridePropertiesWithConfigAdmin(Dictionary props) throws Exception {

        props.put("mllp.port", mllpServer.getListenPort() );

        return "MllpTcpClientProducer";
    }

    @Test()
    public void testSendMultipleMessages() throws Exception {
        int messageCount = 500;
        acknowledged.setExpectedMessageCount(messageCount);
        timeout.setExpectedMessageCount(0);
        frame.setExpectedMessageCount(0);

        // Uncomment one of these lines to see the NACKs handled
        // mllpServer.setSendApplicationRejectAcknowledgementModulus(10);
        // mllpServer.setSendApplicationErrorAcknowledgementModulus(10);

        for (int i = 0; i < messageCount; ++i) {
            log.debug( "Triggering message {}", i);
            Object response = template.requestBodyAndHeader(targetURI, generateMessage(i), "CamelMllpMessageControlId", String.format("%05d", i));
            log.debug("response {}\n{}", i, response);
        }

        assertMockEndpointsSatisfied(15, TimeUnit.SECONDS);
    }


}
