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
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.blueprint.CamelBlueprintTestSupport;
import org.apache.camel.test.junit.rule.mllp.MllpClientResource;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

import java.util.Dictionary;
import java.util.concurrent.TimeUnit;

import static org.apache.camel.test.Hl7MessageGenerator.generateMessage;

@Ignore( value = "Not Yet Implemented")
// TODO: Implement this
public class MllpTcpServerConsumerBlueprintTest extends CamelBlueprintTestSupport {
    @Rule
    public MllpClientResource mllpClient = new MllpClientResource();

    @EndpointInject(uri = "mock://received")
    MockEndpoint received;

    @EndpointInject(uri = "mock://timeout-ex")
    MockEndpoint timeout;

    @EndpointInject(uri = "mock://frame-ex")
    MockEndpoint frame;


    @Override
    protected String getBlueprintDescriptor() {
        return "OSGI-INF/blueprint/mllp-tcp-server-consumer.xml";
    }

    @Override
    protected void doPreSetup() throws Exception {
        mllpClient.setMllpPort(AvailablePortFinder.getNextAvailable());

        super.doPreSetup();
    }

    @Override
    protected String useOverridePropertiesWithConfigAdmin(Dictionary props) throws Exception {

        props.put("mllp.port", mllpClient.getMllpHost() );

        return "MllpTcpServerConsumerBlueprintTest";
    }

    @Test
    public void testReceiveMultipleMessages() throws Exception {
        int sendMessageCount = 5;
        received.expectedMinimumMessageCount(5);

        mllpClient.connect();

        for ( int i=1; i<=sendMessageCount; ++i ) {
            mllpClient.sendMessageAndWaitForAcknowledgement(generateMessage(i));
        }

        assertMockEndpointsSatisfied(10, TimeUnit.SECONDS);
    }


}
