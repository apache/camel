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

import com.rabbitmq.client.ConnectionFactory;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.SimpleRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class RabbitMQLookupConnectionFactoryTest extends CamelTestSupport {

    private ConnectionFactory myConnectionFactory;

    @Override
    protected Registry createCamelRegistry() throws Exception {
        SimpleRegistry registry = new SimpleRegistry();
        myConnectionFactory = new ConnectionFactory();
        myConnectionFactory.setHost("myhost");

        registry.bind("myConnectionFactory", myConnectionFactory);
        return registry;
    }

    @Test
    public void testLookupConnectionFactory() throws Exception {
        RabbitMQEndpoint endpoint = context.getEndpoint("rabbitmq:myexchange", RabbitMQEndpoint.class);
        assertNotNull(endpoint);
        assertSame(endpoint.getConnectionFactory(), myConnectionFactory);
    }

}
