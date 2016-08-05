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
import org.apache.camel.test.junit.rule.mllp.MllpClientResource;
import org.apache.camel.util.KeyValueHolder;
import org.junit.Rule;
import org.junit.Test;

import static org.apache.camel.test.mllp.Hl7MessageGenerator.generateMessage;

public class MllpTcpServerConsumerBlueprintTest extends CamelBlueprintTestSupport {

    @Rule
    public MllpClientResource mllpClient = new MllpClientResource();

    final String receivedUri = "mock://received";
    final String mllpHost = "localhost";

    @EndpointInject(uri = receivedUri)
    MockEndpoint received;

    @Override
    protected String getBlueprintDescriptor() {
        return "OSGI-INF/blueprint/mllp-tcp-server-consumer-test.xml";
    }

    @Override
    protected void addServicesOnStartup(Map<String, KeyValueHolder<Object, Dictionary>> services) {
        ComponentResolver testResolver = new DefaultComponentResolver();

        services.put(ComponentResolver.class.getName(), asService(testResolver, "component", "mllp"));
    }

    @Override
    protected Properties useOverridePropertiesWithPropertiesComponent() {
        mllpClient.setMllpHost(mllpHost);
        mllpClient.setMllpPort(AvailablePortFinder.getNextAvailable());

        Properties props = new Properties();

        props.setProperty("receivedUri", receivedUri);
        props.setProperty("mllp.host", mllpClient.getMllpHost());
        props.setProperty("mllp.port", Integer.toString(mllpClient.getMllpPort()));

        return props;
    }

    /*
        This doesn't seem to work
    @Override
    protected String useOverridePropertiesWithConfigAdmin(Dictionary props) throws Exception {
        mllpClient.setMllpPort(AvailablePortFinder.getNextAvailable());

        props.put("mllp.port", mllpClient.getMllpPort() );

        return "MllpTcpServerConsumerBlueprintTest";
    }
    */

    @Test
    public void testReceiveMultipleMessages() throws Exception {
        int sendMessageCount = 5;
        received.expectedMinimumMessageCount(5);

        mllpClient.connect();

        for (int i = 1; i <= sendMessageCount; ++i) {
            mllpClient.sendMessageAndWaitForAcknowledgement(generateMessage(i));
        }

        mllpClient.close();

        assertMockEndpointsSatisfied(10, TimeUnit.SECONDS);
    }

}
