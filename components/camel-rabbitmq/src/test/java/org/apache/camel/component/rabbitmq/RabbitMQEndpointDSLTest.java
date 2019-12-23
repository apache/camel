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
package org.apache.camel.component.rabbitmq;

import java.util.Collections;
import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.endpoint.EndpointRouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class RabbitMQEndpointDSLTest extends CamelTestSupport {

    @Test
    public void testRabbitMQEndpointDsl() throws Exception {
        Endpoint e = context.getEndpoints().stream().filter(RabbitMQEndpoint.class::isInstance).findFirst().get();
        assertNotNull(e);

        RabbitMQEndpoint re = (RabbitMQEndpoint) e;
        assertNotNull(re.getArgs());

        Map map = re.getArgs();
        assertEquals(1, map.size());
        assertEquals("queue.x-max-priority", map.keySet().iterator().next());
        assertEquals("10", map.values().iterator().next());
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new EndpointRouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(direct("start"))
                    .to(rabbitmq("foo").advanced().args(Collections.singletonMap("queue.x-max-priority", "10")));
            }
        };
    }
}
