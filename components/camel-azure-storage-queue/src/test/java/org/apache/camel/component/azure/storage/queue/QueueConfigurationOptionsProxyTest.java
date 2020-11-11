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
package org.apache.camel.component.azure.storage.queue;

import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class QueueConfigurationOptionsProxyTest extends CamelTestSupport {

    @Test
    public void testIfCorrectOptionsReturnedCorrectly() {
        final QueueConfiguration configuration = new QueueConfiguration();

        // first case: when exchange is set
        final Exchange exchange = new DefaultExchange(context);
        final QueueConfigurationOptionsProxy configurationOptionsProxy = new QueueConfigurationOptionsProxy(configuration);

        exchange.getIn().setHeader(QueueConstants.QUEUE_NAME, "testQueueExchange");
        configuration.setQueueName("testQueueConfig");

        assertEquals("testQueueExchange", configurationOptionsProxy.getQueueName(exchange));

        // second class: exchange is empty
        exchange.getIn().setHeader(QueueConstants.QUEUE_NAME, null);

        assertEquals("testQueueConfig", configurationOptionsProxy.getQueueName(exchange));

        // third class: if exchange is null
        assertEquals("testQueueConfig", configurationOptionsProxy.getQueueName(null));

        // fourth class: if no option at all
        configuration.setQueueName(null);

        assertNull(configurationOptionsProxy.getQueueName(exchange));
    }
}
