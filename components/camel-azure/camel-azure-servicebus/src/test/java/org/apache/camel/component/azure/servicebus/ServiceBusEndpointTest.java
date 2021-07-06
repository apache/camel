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
package org.apache.camel.component.azure.servicebus;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ServiceBusEndpointTest extends CamelTestSupport {

    @Test
    void testCreateWithInvalidData() throws Exception {
        assertThrows(ResolveEndpointFailedException.class,
                () -> context.getEndpoint("azure-servicebus:test//?"));

        assertThrows(ResolveEndpointFailedException.class,
                () -> context.getEndpoint("azure-servicebus://?connectionString=test"));
    }

    @Test
    void testCreateEndpointWithConfig() throws Exception {
        final String uri = "azure-servicebus://testTopicOrQueue";
        final String remaining = "testTopicOrQueue";
        final Map<String, Object> params = new HashMap<>();
        params.put("serviceBusType", ServiceBusType.topic);
        params.put("prefetchCount", 10);
        params.put("connectionString", "testString");

        final ServiceBusEndpoint endpoint
                = (ServiceBusEndpoint) context.getComponent("azure-servicebus", ServiceBusComponent.class)
                        .createEndpoint(uri, remaining, params);

        assertEquals(ServiceBusType.topic, endpoint.getConfiguration().getServiceBusType());
        assertEquals("testTopicOrQueue", endpoint.getConfiguration().getTopicOrQueueName());
        assertEquals(10, endpoint.getConfiguration().getPrefetchCount());
        assertEquals("testString", endpoint.getConfiguration().getConnectionString());
    }

}
