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

package org.apache.camel.test.infra.activemq.services;

import java.io.IOException;
import java.net.URISyntaxException;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.broker.jmx.DestinationViewMBean;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.fail;

public class ActiveMQEmbeddedService implements ActiveMQService, BeforeEachCallback, AfterEachCallback {
    private static final Logger LOG = LoggerFactory.getLogger(ActiveMQEmbeddedService.class);
    private final BrokerService brokerService;
    private final boolean recycle;

    public ActiveMQEmbeddedService() {
        this(ActiveMQEmbeddedServiceBuilder.defaultBroker().brokerService());
    }

    public ActiveMQEmbeddedService(BrokerService brokerService) {
        this.brokerService = brokerService;
        this.recycle = false;
    }

    public ActiveMQEmbeddedService(BrokerService brokerService, boolean recycle) {
        this.brokerService = brokerService;
        this.recycle = recycle;
    }

    @Override
    public void initialize() {
        LOG.info("Trying to start the embedded ActiveMQ");
        try {
            brokerService.start();
            brokerService.waitUntilStarted();
            LOG.info("Embedded ActiveMQ running at {}", serviceAddress());
        } catch (Exception e) {
            LOG.warn("Unable to start embedded ActiveMQ broker: {}", e.getMessage(), e);
            fail(e.getMessage());
        }
    }

    @Override
    public void shutdown() {
        LOG.debug("Trying to stop the embedded ActiveMQ");
        try {
            brokerService.stop();
            brokerService.waitUntilStopped();
            LOG.debug("Embedded ActiveMQ stopped");
        } catch (Exception e) {
            LOG.warn("Error stopping embedded ActiveMQ broker: {}", e.getMessage(), e);
        }
    }

    public void restart() {
        shutdown();

        LOG.info("Trying to start the restart ActiveMQ");
        try {
            brokerService.start(true);
            brokerService.waitUntilStarted();
            LOG.info("Embedded ActiveMQ running at {}", serviceAddress());
        } catch (Exception e) {
            LOG.warn("Unable to start embedded ActiveMQ broker: {}", e.getMessage(), e);
            fail(e.getMessage());
        }
    }

    @Override
    public String serviceAddress() {
        return getBrokerUri(brokerService, 0);
    }

    @Override
    public String userName() {
        return null;
    }

    @Override
    public String password() {
        return null;
    }

    public int getConnectionCount() {
        return brokerService.getTransportConnectors().get(0).getConnections().size();
    }

    public BrokerService getBrokerService() {
        return brokerService;
    }

    public static String getBrokerUri(BrokerService broker, int connector) {
        try {
            return broker.getTransportConnectors().get(connector).getPublishableConnectString();
        } catch (Exception e) {
            LOG.warn("Unable to get ActiveMQ broker address: {}", e.getMessage(), e);
            return null;
        }
    }

    public int getPort() {
        try {
            return brokerService.getTransportConnectors().get(0).getServer().getSocketAddress().getPort();
        } catch (URISyntaxException | IOException e) {
            LOG.error("Error getting the port: {}", e.getMessage());
            throw new RuntimeException("Error getting the port", e);
        }
    }

    @Override
    public void afterEach(ExtensionContext extensionContext) throws Exception {
        if (recycle) {
            shutdown();
        }

    }

    @Override
    public void beforeEach(ExtensionContext extensionContext) throws Exception {
        if (recycle) {
            initialize();
        }
    }

    public String getVmURL() {
        return getVmURL(true);
    }

    public String getVmURL(boolean failoverURL) {
        return failoverURL
                ? String.format("failover:(%s?create=false)", brokerService.getVmConnectorURI().toString())
                : this.brokerService.getVmConnectorURI().toString() + "?create=false";
    }

    public DestinationViewMBean getQueueMBean(String queueName) throws MalformedObjectNameException {
        return getDestinationMBean(queueName, false);
    }

    public DestinationViewMBean getDestinationMBean(String destinationName, boolean topic) throws MalformedObjectNameException {
        String domain = "org.apache.activemq";
        String destinationType = topic ? "Topic" : "Queue";
        ObjectName name = new ObjectName(
                String.format("%s:type=Broker,brokerName=localhost,destinationType=%s,destinationName=%s",
                        domain, destinationType, destinationName));
        return (DestinationViewMBean) brokerService.getManagementContext().newProxyInstance(name,
                DestinationViewMBean.class, true);
    }
}
