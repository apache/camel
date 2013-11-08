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
package org.apache.camel.component.rabbitmq;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;

public class RabbitMQComponentTest {

    private CamelContext context = Mockito.mock(CamelContext.class);

    @Test
    public void testDefaultProperties() throws Exception {
        RabbitMQEndpoint endpoint = createEndpoint(new HashMap<String, Object>());

        assertEquals(14, endpoint.getPortNumber());
        assertEquals(10, endpoint.getThreadPoolSize());
        assertEquals(true, endpoint.isAutoAck());
        assertEquals(true, endpoint.isAutoDelete());
        assertEquals(true, endpoint.isDurable());
        assertEquals("direct", endpoint.getExchangeType());
    }

    @Test
    public void testPropertiesSet() throws Exception {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("username", "coldplay");
        params.put("password", "chrism");
        params.put("autoAck", true);
        params.put("vhost", "vman");
        params.put("threadPoolSize", 515);
        params.put("portNumber", 14123);
        params.put("hostname", "special.host");
        params.put("queue", "queuey");
        params.put("exchangeType", "topic");

        RabbitMQEndpoint endpoint = createEndpoint(params);

        assertEquals("chrism", endpoint.getPassword());
        assertEquals("coldplay", endpoint.getUsername());
        assertEquals("queuey", endpoint.getQueue());
        assertEquals("vman", endpoint.getVhost());
        assertEquals("special.host", endpoint.getHostname());
        assertEquals(14123, endpoint.getPortNumber());
        assertEquals(515, endpoint.getThreadPoolSize());
        assertEquals(true, endpoint.isAutoAck());
        assertEquals(true, endpoint.isAutoDelete());
        assertEquals(true, endpoint.isDurable());
        assertEquals("topic", endpoint.getExchangeType());
    }

    private RabbitMQEndpoint createEndpoint(Map<String, Object> params) throws Exception {
        String uri = "rabbitmq:special.host:14/queuey";
        String remaining = "special.host:14/queuey";

        return new RabbitMQComponent(context).createEndpoint(uri, remaining, params);
    }
}
