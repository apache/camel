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

package org.apache.camel.test.infra.hazelcast.services;

import com.hazelcast.config.Config;
import org.apache.camel.spi.annotations.InfraService;

@InfraService(service = HazelcastInfraService.class,
              description = "In Memory Database Hazelcast",
              serviceAlias = { "hazelcast" })
public class HazelcastEmbeddedInfraService implements HazelcastInfraService {

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
    public Config createConfiguration(String name, int port, String instanceName, String componentName) {
        Config config = new Config();

        if (componentName == "configuration") {
            if (name == null) {
                if (instanceName != null) {
                    config.setInstanceName(instanceName);
                }
                config.getNetworkConfig().setPort(port);
                config.getNetworkConfig().getJoin().getAwsConfig().setEnabled(false);
                config.getNetworkConfig().getJoin().getMulticastConfig().setEnabled(true);
                config.getNetworkConfig().getJoin().getTcpIpConfig().setEnabled(false);
            } else {
                config.setInstanceName(name + "-" + instanceName);
                config.getMetricsConfig().setEnabled(false);
                config.getNetworkConfig().setPort(port);
                config.getNetworkConfig().getJoin().getAutoDetectionConfig().setEnabled(false);
            }
        } else if ((componentName == "list") || (componentName == "seda") || (componentName == "set")) {
            config.getNetworkConfig().getJoin().getAutoDetectionConfig().setEnabled(false);
        } else if (componentName == "idempotent") {
            config.getNetworkConfig().getJoin().getTcpIpConfig().setEnabled(false);
            config.getNetworkConfig().getJoin().getAutoDetectionConfig().setEnabled(false);
        } else if (componentName == "aggregation") {
            config.setInstanceName(instanceName);
            config.getMetricsConfig().setEnabled(false);
            config.getNetworkConfig().getJoin().getMulticastConfig().setEnabled(false);
            config.getNetworkConfig().getJoin().getTcpIpConfig().setEnabled(true).addMember("127.0.0.1");
        }
        return config;
    }
}
