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

package org.apache.camel.blueprint;

import java.util.concurrent.TimeUnit;

import org.apache.camel.EndpointInject;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.blueprint.CamelBlueprintTestSupport;
import org.junit.Test;


public class BlueprintResolveComponentFromCamelContextTest extends CamelBlueprintTestSupport {

    @EndpointInject(uri = "mock://result")
    MockEndpoint result;

    @Override
    protected String getBlueprintDescriptor() {
        return "OSGI-INF/blueprint/test-resolve-component-from-camel-context.xml";
    }

    @Test
    public void testResolveComponentFromCamelContext() throws Exception {
        result.expectedMinimumMessageCount(1);

        // The route is driven by a timer, so we should receive at least one message within 5 seconds
        assertMockEndpointsSatisfied(5, TimeUnit.SECONDS);
    }
}
