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
package org.apache.camel.test.blueprint;

import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.component.mock.MockComponent;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.ComponentResolver;
import org.apache.camel.util.KeyValueHolder;
import org.junit.Test;

public class BlueprintMultipleServiceTest extends CamelBlueprintTestSupport {

    @EndpointInject(uri = "fakeservice1:mock")
    private MockEndpoint fakeServiceOneMock;

    @EndpointInject(uri = "fakeservice2:mock")
    private MockEndpoint fakeServiceTwoMock;

    private MockComponent mockComponentOne = new MockComponent();
    private MockComponent mockComponentTwo = new MockComponent();

    @Override
    protected String getBlueprintDescriptor() {
        return "org/apache/camel/test/blueprint/BlueprintMultipleServiceTest.xml";
    }

    @Override
    @SuppressWarnings("rawtypes")
    protected void addServicesOnStartup(List<KeyValueHolder<String, KeyValueHolder<Object, Dictionary>>> services) {
        Dictionary<String, String> dict1 = new Hashtable<String, String>();
        dict1.put("component", "fakeservice1");

        Dictionary<String, String> dict2 = new Hashtable<String, String>();
        dict2.put("component", "fakeservice2");

        services.add(asKeyValueService(ComponentResolver.class.getName(), mockComponentOne, dict1));
        services.add(asKeyValueService(ComponentResolver.class.getName(), mockComponentTwo, dict2));

    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.addComponent("fakeservice1", mockComponentOne);
        context.addComponent("fakeservice2", mockComponentTwo);

        return context;
    }

    @Test
    public void testMultipleService() throws Exception {

        template.sendBody("direct:start", "Camel");

        fakeServiceOneMock.expectedMessageCount(1);
        fakeServiceTwoMock.expectedMessageCount(1);

        assertMockEndpointsSatisfied();

    }

}
