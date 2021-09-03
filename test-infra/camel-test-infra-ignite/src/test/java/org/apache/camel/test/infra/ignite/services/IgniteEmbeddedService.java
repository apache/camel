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

package org.apache.camel.test.infra.ignite.services;

import java.util.Collections;
import java.util.UUID;

import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCheckedException;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.events.EventType;
import org.apache.ignite.logger.log4j2.Log4J2Logger;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.TcpDiscoveryIpFinder;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IgniteEmbeddedService implements IgniteService {
    private static final Logger LOG = LoggerFactory.getLogger(IgniteEmbeddedService.class);

    private static final TcpDiscoveryIpFinder LOCAL_IP_FINDER = new TcpDiscoveryVmIpFinder(false) {
        {
            setAddresses(Collections.singleton("127.0.0.1:47500..47509"));
        }
    };

    private Ignite ignite;

    @Override
    public void registerProperties() {

    }

    @Override
    public void initialize() {

    }

    @Override
    public void shutdown() {

    }

    @Override
    public IgniteConfiguration createConfiguration() {
        System.setProperty("IGNITE_QUIET", "true");
        System.setProperty("IGNITE_PERFORMANCE_SUGGESTIONS_DISABLED", "true");
        System.setProperty("IGNITE_NO_ASCII", "true");
        System.setProperty("IGNITE_CONSOLE_APPENDER", "true");

        IgniteConfiguration config = new IgniteConfiguration();
        config.setIgniteInstanceName(UUID.randomUUID().toString());
        config.setIncludeEventTypes(EventType.EVT_JOB_FINISHED, EventType.EVT_JOB_RESULTED);
        config.setDiscoverySpi(new TcpDiscoverySpi().setIpFinder(LOCAL_IP_FINDER));

        Log4J2Logger serviceLogger;
        try {
            serviceLogger = new Log4J2Logger(this.getClass().getResource("log4j2.properties"));

            config.setGridLogger(serviceLogger);
        } catch (IgniteCheckedException e) {
            LOG.warn("Unable to configure Ignite log");
        }

        return config;
    }

}
