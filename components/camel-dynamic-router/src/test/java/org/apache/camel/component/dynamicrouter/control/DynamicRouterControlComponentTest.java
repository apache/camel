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
package org.apache.camel.component.dynamicrouter.control;

import java.util.Collections;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.component.dynamicrouter.control.DynamicRouterControlEndpoint.DynamicRouterControlEndpointFactory;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class DynamicRouterControlComponentTest {

    @RegisterExtension
    static CamelContextExtension contextExtension = new DefaultCamelContextExtension();

    @Mock
    protected DynamicRouterControlEndpoint endpoint;

    CamelContext context;

    DynamicRouterControlEndpointFactory endpointFactory;

    DynamicRouterControlComponent component;

    @BeforeEach
    protected void setup() {
        context = contextExtension.getContext();
        endpointFactory = new DynamicRouterControlEndpointFactory() {
            @Override
            public DynamicRouterControlEndpoint getInstance(
                    final String uri,
                    final DynamicRouterControlComponent component,
                    final String controlAction,
                    final DynamicRouterControlConfiguration configuration) {
                return endpoint;
            }
        };
        component = new DynamicRouterControlComponent(context, () -> endpointFactory);
    }

    @Test
    void testCreateEndpoint() throws Exception {
        component.setCamelContext(context);
        Endpoint actualEndpoint = component.createEndpoint(
                "dynamic-router-control:testname", "remaining", Collections.emptyMap());
        assertEquals(endpoint, actualEndpoint);
    }
}
