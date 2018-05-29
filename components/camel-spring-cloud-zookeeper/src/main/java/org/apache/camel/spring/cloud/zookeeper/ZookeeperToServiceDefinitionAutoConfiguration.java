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
package org.apache.camel.spring.cloud.zookeeper;

import org.apache.camel.cloud.ServiceDefinition;
import org.apache.camel.spring.boot.util.GroupCondition;
import org.apache.camel.spring.cloud.CamelSpringCloudServiceLoadBalancerAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.cloud.zookeeper.ConditionalOnZookeeperEnabled;
import org.springframework.cloud.zookeeper.discovery.ZookeeperServer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;

@Configuration
@AutoConfigureBefore(CamelSpringCloudServiceLoadBalancerAutoConfiguration.class)
@ConditionalOnZookeeperEnabled
@Conditional(ZookeeperToServiceDefinitionAutoConfiguration.Condition.class)
public class ZookeeperToServiceDefinitionAutoConfiguration {

    @Bean(name = "zookeeper-server-to-service-definition")
    public Converter<ZookeeperServer, ServiceDefinition> zookeeperServerToServiceDefinition() {
        return new ZookeeperServerToServiceDefinition();
    }

    // *******************************
    // Condition
    // *******************************

    public static class Condition extends GroupCondition {
        public Condition() {
            super(
                "camel.cloud",
                "camel.cloud.zookeeper"
            );
        }
    }
}
