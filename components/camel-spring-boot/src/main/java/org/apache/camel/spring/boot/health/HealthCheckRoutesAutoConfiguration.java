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
package org.apache.camel.spring.boot.health;


import org.apache.camel.converter.TimePatternConverter;
import org.apache.camel.health.HealthCheckRepository;
import org.apache.camel.impl.health.RoutePerformanceCounterEvaluators;
import org.apache.camel.impl.health.RoutesHealthCheckRepository;
import org.apache.camel.spring.boot.CamelAutoConfiguration;
import org.apache.camel.spring.boot.util.GroupCondition;
import org.apache.camel.util.ObjectHelper;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

@Configuration
@AutoConfigureBefore(CamelAutoConfiguration.class)
@Conditional(HealthCheckRoutesAutoConfiguration.Condition.class)
@EnableConfigurationProperties(HealthCheckRoutesConfiguration.class)
public class HealthCheckRoutesAutoConfiguration {

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_SINGLETON)
    @ConditionalOnMissingBean(RoutesHealthCheckRepository.class)
    public HealthCheckRepository routesHealthCheckRepository(HealthCheckRoutesConfiguration configuration) {
        final RoutesHealthCheckRepository repository = new RoutesHealthCheckRepository();
        final HealthCheckRoutesConfiguration.ThresholdsConfiguration thresholds = configuration.getThresholds();

        if (thresholds.getExchangesFailed() != null) {
            repository.addEvaluator(RoutePerformanceCounterEvaluators.exchangesFailed(thresholds.getExchangesFailed()));
        }
        if (thresholds.getExchangesInflight() != null) {
            repository.addEvaluator(RoutePerformanceCounterEvaluators.exchangesInflight(thresholds.getExchangesInflight()));
        }
        if (thresholds.getRedeliveries() != null) {
            repository.addEvaluator(RoutePerformanceCounterEvaluators.redeliveries(thresholds.getRedeliveries()));
        }
        if (thresholds.getExternalRedeliveries() != null) {
            repository.addEvaluator(RoutePerformanceCounterEvaluators.redeliveries(thresholds.getExternalRedeliveries()));
        }
        if (thresholds.getLastProcessingTime() != null) {
            final String time = thresholds.getLastProcessingTime().getThreshold();
            final Integer failures = thresholds.getLastProcessingTime().getFailures();

            if (ObjectHelper.isNotEmpty(time) && ObjectHelper.isNotEmpty(failures)) {
                repository.addEvaluator(RoutePerformanceCounterEvaluators.lastProcessingTime(TimePatternConverter.toMilliSeconds(time), failures));
            }
        }
        if (thresholds.getMinProcessingTime() != null) {
            final String time = thresholds.getMinProcessingTime().getThreshold();
            final Integer failures = thresholds.getMinProcessingTime().getFailures();

            if (ObjectHelper.isNotEmpty(time) && ObjectHelper.isNotEmpty(failures)) {
                repository.addEvaluator(RoutePerformanceCounterEvaluators.minProcessingTime(TimePatternConverter.toMilliSeconds(time), failures));
            }
        }
        if (thresholds.getMeanProcessingTime() != null) {
            final String time = thresholds.getMeanProcessingTime().getThreshold();
            final Integer failures = thresholds.getMeanProcessingTime().getFailures();

            if (ObjectHelper.isNotEmpty(time) && ObjectHelper.isNotEmpty(failures)) {
                repository.addEvaluator(RoutePerformanceCounterEvaluators.meanProcessingTime(TimePatternConverter.toMilliSeconds(time), failures));
            }
        }
        if (thresholds.getMaxProcessingTime() != null) {
            final String time = thresholds.getMaxProcessingTime().getThreshold();
            final Integer failures = thresholds.getMaxProcessingTime().getFailures();

            if (ObjectHelper.isNotEmpty(time) && ObjectHelper.isNotEmpty(failures)) {
                repository.addEvaluator(RoutePerformanceCounterEvaluators.maxProcessingTime(TimePatternConverter.toMilliSeconds(time), failures));
            }
        }

        if (configuration.getThreshold() != null) {
            for (String key: configuration.getThreshold().keySet()) {

                final HealthCheckRoutesConfiguration.ThresholdsConfiguration threshold = configuration.getThreshold(key);

                if (threshold.getExchangesFailed() != null) {
                    repository.addRouteEvaluator(key, RoutePerformanceCounterEvaluators.exchangesFailed(threshold.getExchangesFailed()));
                }
                if (threshold.getExchangesInflight() != null) {
                    repository.addRouteEvaluator(key, RoutePerformanceCounterEvaluators.exchangesInflight(threshold.getExchangesInflight()));
                }
                if (threshold.getRedeliveries() != null) {
                    repository.addRouteEvaluator(key, RoutePerformanceCounterEvaluators.redeliveries(threshold.getRedeliveries()));
                }
                if (threshold.getExternalRedeliveries() != null) {
                    repository.addRouteEvaluator(key, RoutePerformanceCounterEvaluators.redeliveries(threshold.getExternalRedeliveries()));
                }

                if (threshold.getLastProcessingTime() != null) {
                    final String time = threshold.getLastProcessingTime().getThreshold();
                    final Integer failures = threshold.getLastProcessingTime().getFailures();

                    if (ObjectHelper.isNotEmpty(time) && ObjectHelper.isNotEmpty(failures)) {
                        repository.addRouteEvaluator(key, RoutePerformanceCounterEvaluators.lastProcessingTime(TimePatternConverter.toMilliSeconds(time), failures));
                    }
                }
                if (threshold.getMinProcessingTime() != null) {
                    final String time = threshold.getMinProcessingTime().getThreshold();
                    final Integer failures = threshold.getMinProcessingTime().getFailures();

                    if (ObjectHelper.isNotEmpty(time) && ObjectHelper.isNotEmpty(failures)) {
                        repository.addRouteEvaluator(key, RoutePerformanceCounterEvaluators.minProcessingTime(TimePatternConverter.toMilliSeconds(time), failures));
                    }
                }
                if (threshold.getMeanProcessingTime() != null) {
                    final String time = threshold.getMeanProcessingTime().getThreshold();
                    final Integer failures = threshold.getMeanProcessingTime().getFailures();

                    if (ObjectHelper.isNotEmpty(time) && ObjectHelper.isNotEmpty(failures)) {
                        repository.addRouteEvaluator(key, RoutePerformanceCounterEvaluators.meanProcessingTime(TimePatternConverter.toMilliSeconds(time), failures));
                    }
                }
                if (threshold.getMaxProcessingTime() != null) {
                    final String time = threshold.getMaxProcessingTime().getThreshold();
                    final Integer failures = threshold.getMaxProcessingTime().getFailures();

                    if (ObjectHelper.isNotEmpty(time) && ObjectHelper.isNotEmpty(failures)) {
                        repository.addRouteEvaluator(key, RoutePerformanceCounterEvaluators.maxProcessingTime(TimePatternConverter.toMilliSeconds(time), failures));
                    }
                }
            }
        }

        return repository;
    }

    // ***************************************
    // Condition
    // ***************************************

    public static class Condition extends GroupCondition {
        public Condition() {
            super(
                HealthConstants.HEALTH_PREFIX,
                HealthConstants.HEALTH_CHECK_ROUTES_PREFIX
            );
        }
    }
}
