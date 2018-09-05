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
import java.util.concurrent.TimeUnit;

import org.apache.camel.EndpointInject;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultComponentResolver;
import org.apache.camel.spi.ComponentResolver;
import org.apache.camel.test.blueprint.CamelBlueprintTestSupport;
import org.apache.camel.util.KeyValueHolder;
import org.junit.Test;

public class MllpConfigurationBeanBlueprintTest extends CamelBlueprintTestSupport {
    @EndpointInject(uri = "mock://target")
    MockEndpoint received;

    @Override
    protected void addServicesOnStartup(Map<String, KeyValueHolder<Object, Dictionary>> services) {
        ComponentResolver testResolver = new DefaultComponentResolver();

        services.put(ComponentResolver.class.getName(), asService(testResolver, "component", "mllp"));
    }

    @Override
    protected String getBlueprintDescriptor() {
        return "OSGI-INF/blueprint/mllp-configuration-bean-test.xml";
    }

    @Test
    public void testReceiveMultipleMessages() throws Exception {
        int sendMessageCount = 5;
        received.expectedMinimumMessageCount(5);

        for (int i = 1; i <= sendMessageCount; ++i) {
            template.sendBody("direct://source", "Message " + i);
        }

        assertMockEndpointsSatisfied(10, TimeUnit.SECONDS);
    }

}
