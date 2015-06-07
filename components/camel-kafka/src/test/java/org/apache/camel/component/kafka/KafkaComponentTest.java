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
package org.apache.camel.component.kafka;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class KafkaComponentTest {

    private CamelContext context = Mockito.mock(CamelContext.class);

    @Test
    public void testPropertiesSet() throws Exception {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("zookeeperHost", "somehost");
        params.put("zookeeperPort", 2987);
        params.put("portNumber", 14123);
        params.put("consumerStreams", "3");
        params.put("topic", "mytopic");
        params.put("partitioner", "com.class.Party");

        String uri = "kafka:broker1:12345,broker2:12566";
        String remaining = "broker1:12345,broker2:12566";

        KafkaEndpoint endpoint = new KafkaComponent(context).createEndpoint(uri, remaining, params);
        assertEquals("somehost:2987", endpoint.getZookeeperConnect());
        assertEquals("somehost", endpoint.getZookeeperHost());
        assertEquals(2987, endpoint.getZookeeperPort());
        assertEquals("broker1:12345,broker2:12566", endpoint.getBrokers());
        assertEquals("mytopic", endpoint.getTopic());
        assertEquals(3, endpoint.getConsumerStreams());
        assertEquals("com.class.Party", endpoint.getPartitioner());
    }

    @Test
    public void testZookeeperConnectPropertyOverride() throws Exception {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("zookeeperConnect", "thehost:2181/chroot");
        params.put("zookeeperHost", "somehost");
        params.put("zookeeperPort", 2987);
        params.put("portNumber", 14123);
        params.put("consumerStreams", "3");
        params.put("topic", "mytopic");
        params.put("partitioner", "com.class.Party");

        String uri = "kafka:broker1:12345,broker2:12566";
        String remaining = "broker1:12345,broker2:12566";

        KafkaEndpoint endpoint = new KafkaComponent(context).createEndpoint(uri, remaining, params);
        assertEquals("thehost:2181/chroot", endpoint.getZookeeperConnect());
        assertNull(endpoint.getZookeeperHost());
        assertEquals(-1, endpoint.getZookeeperPort());
        assertEquals("broker1:12345,broker2:12566", endpoint.getBrokers());
        assertEquals("mytopic", endpoint.getTopic());
        assertEquals(3, endpoint.getConsumerStreams());
        assertEquals("com.class.Party", endpoint.getPartitioner());
    }
}
