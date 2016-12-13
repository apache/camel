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

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.SimpleRegistry;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

public class HazelcastConfigurationTest {
    @After
    public void tearDown() throws Exception {
        Hazelcast.shutdownAll();
    }

    @Test
    public void testCustomConfigurationUri() throws Exception {
        DefaultCamelContext context = null;

        try {
            context = new DefaultCamelContext();
            context.start();
            context.getEndpoint("hazelcast:map:my-cache?hazelcastConfigUri=classpath:hazelcast-custom.xml");

            HazelcastComponent component = context.getComponent("hazelcast", HazelcastComponent.class);
            HazelcastInstance hz = component.getHazelcastInstance();

            Assert.assertFalse(hz.getConfig().getNetworkConfig().getJoin().getAwsConfig().isEnabled());
            Assert.assertFalse(hz.getConfig().getNetworkConfig().getJoin().getMulticastConfig().isEnabled());
            Assert.assertFalse(hz.getConfig().getNetworkConfig().getJoin().getTcpIpConfig().isEnabled());
            Assert.assertEquals(9876, hz.getConfig().getNetworkConfig().getPort());

        } finally {
            if (context != null) {
                context.stop();
            }
        }
    }

    @Test
    public void testCustomConfiguration() throws Exception {
        DefaultCamelContext context = null;

        try {
            Config config = new Config();
            config.getNetworkConfig().setPort(6789);
            config.getNetworkConfig().getJoin().getAwsConfig().setEnabled(false);
            config.getNetworkConfig().getJoin().getMulticastConfig().setEnabled(true);
            config.getNetworkConfig().getJoin().getTcpIpConfig().setEnabled(false);

            SimpleRegistry reg = new SimpleRegistry();
            reg.put("my-config", config);

            context = new DefaultCamelContext(reg);
            context.start();
            context.getEndpoint("hazelcast:map:my-cache?hazelcastConfig=#my-config");

            HazelcastComponent component = context.getComponent("hazelcast", HazelcastComponent.class);
            HazelcastInstance hz = component.getHazelcastInstance();

            Assert.assertFalse(hz.getConfig().getNetworkConfig().getJoin().getAwsConfig().isEnabled());
            Assert.assertTrue(hz.getConfig().getNetworkConfig().getJoin().getMulticastConfig().isEnabled());
            Assert.assertFalse(hz.getConfig().getNetworkConfig().getJoin().getTcpIpConfig().isEnabled());
            Assert.assertEquals(6789, hz.getConfig().getNetworkConfig().getPort());

        } finally {
            if (context != null) {
                context.stop();
            }
        }
    }
}
