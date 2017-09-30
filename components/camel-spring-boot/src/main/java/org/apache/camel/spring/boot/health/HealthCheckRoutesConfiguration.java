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

import java.util.Map;

import org.apache.camel.util.ObjectHelper;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = HealthConstants.HEALTH_CHECK_ROUTES_PREFIX)
public class HealthCheckRoutesConfiguration {
    /**
     * Global option to enable/disable this ${@link org.apache.camel.health.HealthCheckRepository}, default is false.
     */
    private boolean enabled;

    /**
     * configuration
     */
    private ThresholdsConfiguration thresholds = new ThresholdsConfiguration();

    /**
     * configurations
     */
    private Map<String, RouteThresholdsConfiguration> threshold;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public ThresholdsConfiguration getThresholds() {
        return thresholds;
    }

    public Map<String, RouteThresholdsConfiguration> getThreshold() {
        return threshold;
    }

    public void setThreshold(Map<String, RouteThresholdsConfiguration> threshold) {
        this.threshold = threshold;
    }

    public ThresholdsConfiguration getThreshold(String id) {
        ThresholdsConfiguration cfg;

        if (this.threshold == null) {
            cfg = this.thresholds.copy();
        } else {
            RouteThresholdsConfiguration routeCfg = this.threshold.get(id);
            //cfg = this.threshold.get(id);

            if (routeCfg == null) {
                cfg = this.thresholds.copy();
            } else {
                cfg = routeCfg;

                if (routeCfg.isInherit()) {
                    routeCfg.exchangesFailed = ObjectHelper.supplyIfEmpty(cfg.exchangesFailed, thresholds::getExchangesFailed);
                    routeCfg.exchangesInflight = ObjectHelper.supplyIfEmpty(cfg.exchangesInflight, thresholds::getExchangesInflight);
                    routeCfg.redeliveries = ObjectHelper.supplyIfEmpty(cfg.redeliveries, thresholds::getRedeliveries);
                    routeCfg.externalRedeliveries = ObjectHelper.supplyIfEmpty(cfg.externalRedeliveries, thresholds::getExternalRedeliveries);
                    routeCfg.externalRedeliveries = ObjectHelper.supplyIfEmpty(cfg.externalRedeliveries, thresholds::getExternalRedeliveries);
                    routeCfg.lastProcessingTime = ObjectHelper.supplyIfEmpty(cfg.lastProcessingTime, thresholds::getLastProcessingTime);
                    routeCfg.minProcessingTime = ObjectHelper.supplyIfEmpty(cfg.minProcessingTime, thresholds::getMinProcessingTime);
                    routeCfg.meanProcessingTime = ObjectHelper.supplyIfEmpty(cfg.meanProcessingTime, thresholds::getMeanProcessingTime);
                    routeCfg.maxProcessingTime = ObjectHelper.supplyIfEmpty(cfg.maxProcessingTime, thresholds::getMaxProcessingTime);
                }
            }
        }

        return cfg;
    }

    public static class ThresholdsConfiguration implements Cloneable {
        /**
         * Number of failed exchanges.
         */
        protected Long exchangesFailed;

        /**
         * Number of inflight exchanges.
         */
        protected Long exchangesInflight;

        /**
         * Number of redeliveries (internal only).
         */
        protected Long redeliveries;

        /**
         * Number of external initiated redeliveries (such as from JMS broker).
         */
        protected Long externalRedeliveries;

        /**
         * Last processing time
         */
        protected ThresholdsWithFailuresConfiguration lastProcessingTime;

        /**
         * Min processing time
         */
        protected ThresholdsWithFailuresConfiguration minProcessingTime;

        /**
         * Mean processing time
         */
        protected ThresholdsWithFailuresConfiguration meanProcessingTime;

        /**
         * Max processing time
         */
        protected ThresholdsWithFailuresConfiguration maxProcessingTime;


        public Long getExchangesFailed() {
            return exchangesFailed;
        }

        public void setExchangesFailed(Long exchangesFailed) {
            this.exchangesFailed = exchangesFailed;
        }

        public Long getExchangesInflight() {
            return exchangesInflight;
        }

        public void setExchangesInflight(Long exchangesInflight) {
            this.exchangesInflight = exchangesInflight;
        }

        public Long getRedeliveries() {
            return redeliveries;
        }

        public void setRedeliveries(Long redeliveries) {
            this.redeliveries = redeliveries;
        }

        public Long getExternalRedeliveries() {
            return externalRedeliveries;
        }

        public void setExternalRedeliveries(Long externalRedeliveries) {
            this.externalRedeliveries = externalRedeliveries;
        }

        public ThresholdsWithFailuresConfiguration getLastProcessingTime() {
            return lastProcessingTime;
        }

        public void setLastProcessingTime(ThresholdsWithFailuresConfiguration lastProcessingTime) {
            this.lastProcessingTime = lastProcessingTime;
        }

        public ThresholdsWithFailuresConfiguration getMinProcessingTime() {
            return minProcessingTime;
        }

        public void setMinProcessingTime(ThresholdsWithFailuresConfiguration minProcessingTime) {
            this.minProcessingTime = minProcessingTime;
        }

        public ThresholdsWithFailuresConfiguration getMeanProcessingTime() {
            return meanProcessingTime;
        }

        public void setMeanProcessingTime(ThresholdsWithFailuresConfiguration meanProcessingTime) {
            this.meanProcessingTime = meanProcessingTime;
        }

        public ThresholdsWithFailuresConfiguration getMaxProcessingTime() {
            return maxProcessingTime;
        }

        public void setMaxProcessingTime(ThresholdsWithFailuresConfiguration maxProcessingTime) {
            this.maxProcessingTime = maxProcessingTime;
        }

        public ThresholdsConfiguration copy() {
            try {
                return (ThresholdsConfiguration)super.clone();
            } catch (CloneNotSupportedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static class RouteThresholdsConfiguration extends ThresholdsConfiguration {
        /**
         * Inherit from global from global configuration;
         */
        private boolean inherit = true;

        public boolean isInherit() {
            return inherit;
        }

        public void setInherit(boolean inherit) {
            this.inherit = inherit;
        }
    }

    public static class ThresholdsWithFailuresConfiguration {
        /**
         * The Threshold
         */
        private String threshold;

        /**
         * Failures
         */
        private Integer failures;

        public String getThreshold() {
            return threshold;
        }

        public void setThreshold(String threshold) {
            this.threshold = threshold;
        }

        public Integer getFailures() {
            return failures;
        }

        public void setFailures(Integer failures) {
            this.failures = failures;
        }
    }
}
