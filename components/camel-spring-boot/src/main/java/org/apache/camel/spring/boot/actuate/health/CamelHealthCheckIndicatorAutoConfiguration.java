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
package org.apache.camel.spring.boot.actuate.health;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.camel.CamelContext;
import org.apache.camel.health.HealthCheckFilter;
import org.apache.camel.spring.boot.CamelAutoConfiguration;
import org.apache.camel.spring.boot.health.HealthConstants;
import org.apache.camel.spring.boot.util.GroupCondition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnClass({CamelContext.class, HealthIndicator.class})
@Conditional(CamelHealthCheckIndicatorAutoConfiguration.Condition.class)
@ConditionalOnBean({CamelAutoConfiguration.class, CamelContext.class})
@AutoConfigureAfter(CamelAutoConfiguration.class)
@EnableConfigurationProperties(CamelHealthCheckIndicatorConfiguration.class)
public class CamelHealthCheckIndicatorAutoConfiguration {
    @Autowired
    private CamelContext camelContext;
    @Autowired
    private CamelHealthCheckIndicatorConfiguration configuration;
    @Autowired(required = false)
    private List<HealthCheckFilter> filterList = Collections.emptyList();

    @Bean(name = "camel-health-checks")
    @ConditionalOnMissingBean(CamelHealthCheckIndicator.class)
    public HealthIndicator camelHealthChecksIndicator() {
        // Collect filters from the environment first so user defined filter
        // take precedence over platform ones.
        final List<HealthCheckFilter> filters = new ArrayList<>(this.filterList);

        // ids
        for (String exclusion: configuration.getExclusion().getIds()) {
            // "cache" the pattern
            final Pattern pattern = Pattern.compile(exclusion);

            filters.add(check -> exclusion.equals(check.getId()) || pattern.matcher(check.getId()).matches());
        }

        // groups
        for (String exclusion: configuration.getExclusion().getGroups()) {
            // "cache" the pattern
            final Pattern pattern = Pattern.compile(exclusion);

            filters.add(check -> exclusion.equals(check.getGroup()) || pattern.matcher(check.getGroup()).matches());
        }

        return new CamelHealthCheckIndicator(camelContext, filters);
    }

    // ***************************************
    // Condition
    // ***************************************

    public static class Condition extends GroupCondition {
        public Condition() {
            super(
                HealthConstants.HEALTH_PREFIX,
                HealthConstants.HEALTH_CHECK_INDICATOR_PREFIX
            );
        }
    }
}
