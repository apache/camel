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

package org.apache.camel.spring.cloud;

import java.util.List;
import java.util.Map;

import org.apache.camel.cloud.ServiceFilter;
import org.apache.camel.impl.cloud.BlacklistServiceFilter;
import org.apache.camel.impl.cloud.ChainedServiceFilter;
import org.apache.camel.impl.cloud.HealthyServiceFilter;
import org.apache.camel.spring.boot.util.GroupCondition;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Scope;

@Configuration
@EnableConfigurationProperties(ServiceCallConfigurationProperties.class)
public class ServiceCallServiceFilterAutoConfiguration {
    @Lazy
    @Scope("prototype")
    @Bean(name = "service-filter-chained")
    @Conditional(ServiceCallServiceFilterAutoConfiguration.ServiceFilterCondition.class)
    public ServiceFilter chainedServiceFilter(ServiceCallConfigurationProperties properties) {
        BlacklistServiceFilter blacklist = new BlacklistServiceFilter();

        Map<String, List<String>> services = properties.getServiceFilter().getBlacklist();
        for (Map.Entry<String, List<String>> entry : services.entrySet()) {
            for (String part : entry.getValue()) {
                String host = StringHelper.before(part, ":");
                String port = StringHelper.after(part, ":");

                if (ObjectHelper.isNotEmpty(host) && ObjectHelper.isNotEmpty(port)) {
                    blacklist.addServer(entry.getKey(), host, Integer.parseInt(port));
                }
            }
        }

        return ChainedServiceFilter.wrap(new HealthyServiceFilter(), blacklist);
    }

    public static class ServiceFilterCondition extends GroupCondition {
        public ServiceFilterCondition() {
            super(
                "camel.cloud.servicecall",
                "camel.cloud.servicecall.service-filter"
            );
        }
    }
}
