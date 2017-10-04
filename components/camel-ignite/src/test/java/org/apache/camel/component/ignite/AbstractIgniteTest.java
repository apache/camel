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
package org.apache.camel.component.ignite;

import java.util.Collections;
import java.util.UUID;

import org.apache.camel.CamelContext;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.ignite.Ignite;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.events.EventType;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.TcpDiscoveryIpFinder;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;

public abstract class AbstractIgniteTest extends CamelTestSupport {
    
    /** Ip finder for TCP discovery. */
    private static final TcpDiscoveryIpFinder LOCAL_IP_FINDER = new TcpDiscoveryVmIpFinder(false) { {
            setAddresses(Collections.singleton("127.0.0.1:47500..47509"));
        } };
    
    private Ignite ignite;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.addComponent(getScheme(), createComponent());
        return context;
    }

    protected IgniteConfiguration createConfiguration() {
        IgniteConfiguration config = new IgniteConfiguration();
        config.setIgniteInstanceName(UUID.randomUUID().toString());
        config.setIncludeEventTypes(EventType.EVT_JOB_FINISHED, EventType.EVT_JOB_RESULTED);
        config.setDiscoverySpi(new TcpDiscoverySpi().setIpFinder(LOCAL_IP_FINDER));
        return config;
    }

    protected abstract String getScheme();

    protected abstract AbstractIgniteComponent createComponent();

    protected Ignite ignite() {
        if (ignite == null) {
            ignite = context.getComponent(getScheme(), AbstractIgniteComponent.class).getIgnite();
        }
        return ignite;
    }

}
