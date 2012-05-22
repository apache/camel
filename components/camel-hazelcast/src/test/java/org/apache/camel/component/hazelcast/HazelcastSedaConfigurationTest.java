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
package org.apache.camel.component.hazelcast;

import org.apache.camel.component.hazelcast.seda.HazelcastSedaEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class HazelcastSedaConfigurationTest extends CamelTestSupport {

    @Test
    public void createEndpointWithTransferExchange() throws Exception {
        HazelcastSedaEndpoint hzlqEndpoint = (HazelcastSedaEndpoint) context.getEndpoint("hazelcast:seda:foo?transferExchange=true");

        assertEquals("Invalid queue name", "foo", hzlqEndpoint.getConfiguration().getQueueName());
        assertTrue("Default value of concurrent consumers is invalid", hzlqEndpoint.getConfiguration().isTransferExchange());

        hzlqEndpoint = (HazelcastSedaEndpoint) context.getEndpoint("hazelcast:seda:foo?transferExchange=false");

        assertEquals("Invalid queue name", "foo", hzlqEndpoint.getConfiguration().getQueueName());
        assertFalse("Default value of concurrent consumers is invalid", hzlqEndpoint.getConfiguration().isTransferExchange());
    }

    @Test
    public void createEndpointWithNoParams() throws Exception {
        HazelcastSedaEndpoint hzlqEndpoint = (HazelcastSedaEndpoint) context.getEndpoint("hazelcast:seda:foo");

        assertEquals("Invalid queue name", "foo", hzlqEndpoint.getConfiguration().getQueueName());
        assertEquals("Default value of concurrent consumers is invalid", 1, hzlqEndpoint.getConfiguration().getConcurrentConsumers());
        assertEquals("Default value of pool interval is invalid", 1000, hzlqEndpoint.getConfiguration().getPollInterval());
    }

    @Test
    public void createEndpointWithConcurrentConsumersParam() throws Exception {
        HazelcastSedaEndpoint hzlqEndpoint = (HazelcastSedaEndpoint) context.getEndpoint("hazelcast:seda:foo?concurrentConsumers=4");

        assertEquals("Invalid queue name", "foo", hzlqEndpoint.getConfiguration().getQueueName());
        assertEquals("Value of concurrent consumers is invalid", 4, hzlqEndpoint.getConfiguration().getConcurrentConsumers());
        assertEquals("Default value of pool interval is invalid", 1000, hzlqEndpoint.getConfiguration().getPollInterval());
    }

    @Test
    public void createEndpointWithPoolIntevalParam() throws Exception {
        HazelcastSedaEndpoint hzlqEndpoint = (HazelcastSedaEndpoint) context.getEndpoint("hazelcast:seda:foo?pollInterval=4000");

        assertEquals("Invalid queue name", "foo", hzlqEndpoint.getConfiguration().getQueueName());
        assertEquals("Default value of concurrent consumers is invalid", 1, hzlqEndpoint.getConfiguration().getConcurrentConsumers());
        assertEquals("Invalid pool interval", 4000, hzlqEndpoint.getConfiguration().getPollInterval());
    }

}
