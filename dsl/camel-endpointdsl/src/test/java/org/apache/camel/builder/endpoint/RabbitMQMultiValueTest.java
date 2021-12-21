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
package org.apache.camel.builder.endpoint;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.builder.endpoint.dsl.RabbitMQEndpointBuilderFactory;
import org.apache.camel.component.rabbitmq.RabbitMQEndpoint;
import org.junit.jupiter.api.Test;

import static org.apache.camel.test.junit5.TestSupport.assertIsInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class RabbitMQMultiValueTest extends BaseEndpointDslTest {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testMultiValue() throws Exception {
        context.start();

        context.addRoutes(new EndpointRouteBuilder() {
            @Override
            public void configure() throws Exception {
                RabbitMQEndpointBuilderFactory.RabbitMQEndpointBuilder builder = rabbitmq("mytopic").advanced()
                        .args("foo", "123")
                        .args("bar", "456")
                        .args("beer", "yes").basic();

                Endpoint endpoint = builder.resolve(context);
                assertNotNull(endpoint);
                assertEquals("rabbitmq://mytopic?arg.bar=456&arg.beer=yes&arg.foo=123", endpoint.getEndpointUri());
                RabbitMQEndpoint re = assertIsInstanceOf(RabbitMQEndpoint.class, endpoint);
                assertEquals("mytopic", re.getExchangeName());
                Map<String, Object> args = re.getArgs();
                assertNotNull(args);
                assertEquals(3, args.size());
                assertEquals("123", args.get("foo"));
                assertEquals("456", args.get("bar"));
                assertEquals("yes", args.get("beer"));
            }
        });

        context.stop();
    }

    @Test
    public void testMultiValueMap() throws Exception {
        context.start();

        context.addRoutes(new EndpointRouteBuilder() {
            @Override
            public void configure() throws Exception {
                Map map = new HashMap();
                map.put("foo", "123");
                map.put("bar", "456");
                map.put("beer", "yes");

                RabbitMQEndpointBuilderFactory.RabbitMQEndpointBuilder builder = rabbitmq("mytopic").advanced()
                        .args(map).basic();

                Endpoint endpoint = builder.resolve(context);
                assertNotNull(endpoint);
                assertEquals("rabbitmq://mytopic?arg.bar=456&arg.beer=yes&arg.foo=123", endpoint.getEndpointUri());
                RabbitMQEndpoint re = assertIsInstanceOf(RabbitMQEndpoint.class, endpoint);
                assertEquals("mytopic", re.getExchangeName());
                Map<String, Object> args = re.getArgs();
                assertNotNull(args);
                assertEquals(3, args.size());
                assertEquals("123", args.get("foo"));
                assertEquals("456", args.get("bar"));
                assertEquals("yes", args.get("beer"));
            }
        });

        context.stop();
    }

}
