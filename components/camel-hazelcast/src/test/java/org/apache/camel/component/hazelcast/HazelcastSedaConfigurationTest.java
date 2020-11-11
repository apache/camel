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
package org.apache.camel.component.hazelcast;

import org.apache.camel.PropertyBindingException;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.component.hazelcast.seda.HazelcastSedaEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.apache.camel.test.junit5.TestSupport.assertIsInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class HazelcastSedaConfigurationTest extends CamelTestSupport {

    @Test
    public void createEndpointWithTransferExchange() throws Exception {
        HazelcastSedaEndpoint hzlqEndpoint
                = (HazelcastSedaEndpoint) context.getEndpoint("hazelcast-seda:foo?transferExchange=true");

        assertEquals("foo", hzlqEndpoint.getConfiguration().getQueueName(), "Invalid queue name");
        assertTrue(hzlqEndpoint.getConfiguration().isTransferExchange(), "Default value of concurrent consumers is invalid");

        hzlqEndpoint = (HazelcastSedaEndpoint) context.getEndpoint("hazelcast-seda:foo?transferExchange=false");

        assertEquals("foo", hzlqEndpoint.getConfiguration().getQueueName(), "Invalid queue name");
        assertFalse(hzlqEndpoint.getConfiguration().isTransferExchange(), "Default value of concurrent consumers is invalid");
    }

    @Test
    public void createEndpointWithNoParams() throws Exception {
        HazelcastSedaEndpoint hzlqEndpoint = (HazelcastSedaEndpoint) context.getEndpoint("hazelcast-seda:foo");

        assertEquals("foo", hzlqEndpoint.getConfiguration().getQueueName(), "Invalid queue name");
        assertEquals(1, hzlqEndpoint.getConfiguration().getConcurrentConsumers(),
                "Default value of concurrent consumers is invalid");
        assertEquals(1000, hzlqEndpoint.getConfiguration().getPollTimeout(), "Default value of pool timeout is invalid");
        assertEquals(1000, hzlqEndpoint.getConfiguration().getOnErrorDelay(), "Default value of on error delay is invalid");
    }

    @Test
    public void createEndpointWithConcurrentConsumersParam() throws Exception {
        HazelcastSedaEndpoint hzlqEndpoint
                = (HazelcastSedaEndpoint) context.getEndpoint("hazelcast-seda:foo?concurrentConsumers=4");

        assertEquals("foo", hzlqEndpoint.getConfiguration().getQueueName(), "Invalid queue name");
        assertEquals(4, hzlqEndpoint.getConfiguration().getConcurrentConsumers(), "Value of concurrent consumers is invalid");
        assertEquals(1000, hzlqEndpoint.getConfiguration().getPollTimeout(), "Default value of pool timeout is invalid");
        assertEquals(1000, hzlqEndpoint.getConfiguration().getOnErrorDelay(), "Default value of on error delay is invalid");
    }

    @Test
    public void createEndpointWithPoolIntevalParam() throws Exception {
        HazelcastSedaEndpoint hzlqEndpoint = (HazelcastSedaEndpoint) context.getEndpoint("hazelcast-seda:foo?pollTimeout=4000");

        assertEquals("foo", hzlqEndpoint.getConfiguration().getQueueName(), "Invalid queue name");
        assertEquals(1, hzlqEndpoint.getConfiguration().getConcurrentConsumers(),
                "Default value of concurrent consumers is invalid");
        assertEquals(4000, hzlqEndpoint.getConfiguration().getPollTimeout(), "Invalid pool timeout");
        assertEquals(1000, hzlqEndpoint.getConfiguration().getOnErrorDelay(), "Default value of on error delay is invalid");
    }

    @Test
    public void createEndpointWithOnErrorDelayParam() throws Exception {
        HazelcastSedaEndpoint hzlqEndpoint
                = (HazelcastSedaEndpoint) context.getEndpoint("hazelcast-seda:foo?onErrorDelay=5000");

        assertEquals("foo", hzlqEndpoint.getConfiguration().getQueueName(), "Invalid queue name");
        assertEquals(1, hzlqEndpoint.getConfiguration().getConcurrentConsumers(),
                "Default value of concurrent consumers is invalid");
        assertEquals(1000, hzlqEndpoint.getConfiguration().getPollTimeout(), "Default value of pool timeout is invalid");
        assertEquals(5000, hzlqEndpoint.getConfiguration().getOnErrorDelay(), "Value of on error delay is invalid");
    }

    @Test
    public void createEndpointWithIllegalOnErrorDelayParam() throws Exception {
        try {
            context.getEndpoint("hazelcast-seda:foo?onErrorDelay=-1");
            fail("Should have thrown exception");
        } catch (ResolveEndpointFailedException e) {
            assertIsInstanceOf(PropertyBindingException.class, e.getCause());
        }
    }

}
