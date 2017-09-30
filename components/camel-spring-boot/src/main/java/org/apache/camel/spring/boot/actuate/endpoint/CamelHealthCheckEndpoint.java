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
package org.apache.camel.spring.boot.actuate.endpoint;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

import static java.util.stream.Collectors.toList;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.apache.camel.CamelContext;
import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheckConfiguration;
import org.apache.camel.health.HealthCheckHelper;
import org.apache.camel.util.ObjectHelper;
import org.springframework.boot.actuate.endpoint.Endpoint;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@link Endpoint} to expose {@link org.apache.camel.health.HealthCheck} information.
 */
@ConfigurationProperties(prefix = "endpoints." + CamelHealthCheckEndpoint.ENDPOINT_ID)
public class CamelHealthCheckEndpoint extends AbstractCamelEndpoint<Collection<CamelHealthCheckEndpoint.HealthCheckResult>> {
    public static final String ENDPOINT_ID = "camelhealthcheck";

    public CamelHealthCheckEndpoint(CamelContext camelContext) {
        super(ENDPOINT_ID, camelContext);
    }

    @Override
    public Collection<HealthCheckResult> invoke() {
        return HealthCheckHelper.invoke(getCamelContext()).stream()
            .map(result -> new HealthCheckResult(result, new Check(result)))
            .collect(toList());
    }

    // ****************************************
    // Used by CamelHealthCheckMvcEndpoint
    // ****************************************

    Optional<HealthCheckResult> query(String id, Map<String, Object> options) {
        return HealthCheckHelper.query(getCamelContext(), id, options)
            .map(result -> new DetailedHealthCheckResult(result, new DetailedCheck(result)));
    }

    Optional<HealthCheckResult> invoke(String id, Map<String, Object> options) {
        return HealthCheckHelper.invoke(getCamelContext(), id, options)
            .map(result -> new DetailedHealthCheckResult(result, new DetailedCheck(result)));
    }

    // ****************************************
    // Wrappers
    // ****************************************

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonPropertyOrder({"enabled", "interval", "failureThreshold"})
    public static class CheckConfiguration {
        protected final HealthCheckConfiguration configuration;

        public CheckConfiguration(HealthCheckConfiguration configuration) {
            this.configuration = ObjectHelper.supplyIfEmpty(configuration, HealthCheckConfiguration::new);
        }

        @JsonProperty("enabled")
        public Boolean isEnabled() {
            return configuration.isEnabled();
        }

        @JsonProperty("interval")
        public String getDuration() {
            Duration interval = configuration.getInterval();
            return interval != null ? interval.toString() : null;
        }

        @JsonProperty("failureThreshold")
        public Integer getFailureThreshold() {
            return configuration.getFailureThreshold();
        }
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonPropertyOrder({"id", "group"})
    public static class Check {
        protected final HealthCheck.Result result;

        public Check(HealthCheck.Result result) {
            this.result = result;
        }

        @JsonProperty("id")
        public String getId() {
            return result.getCheck().getId();
        }

        @JsonProperty("group")
        public String getGroup() {
            return result.getCheck().getGroup();
        }
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonPropertyOrder({"status", "message", "check"})
    public static class HealthCheckResult {
        protected final HealthCheck.Result result;
        protected final Check check;

        public HealthCheckResult(HealthCheck.Result result, Check check) {
            this.result = result;
            this.check = check;
        }

        @JsonProperty("status")
        public String getStatus() {
            return result.getState().name();
        }

        @JsonProperty("message")
        public String getMessage() {
            return result.getMessage().orElse(null);
        }

        @JsonProperty("check")
        public Check getCheck() {
            return this.check;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonPropertyOrder({"id", "group", "metaData"})
    public static class DetailedCheck extends Check {
        private CheckConfiguration configuration;

        public DetailedCheck(HealthCheck.Result result) {
            super(result);

            this.configuration = new CheckConfiguration(result.getCheck().getConfiguration());
        }

        @JsonProperty("configuration")
        public CheckConfiguration getConfiguration() {
            return this.configuration;
        }

        @JsonProperty("metaData")
        public Map<String, Object> getMeta() {
            return result.getCheck().getMetaData();
        }
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonPropertyOrder({"status", "message", "details", "check"})
    public static class DetailedHealthCheckResult extends HealthCheckResult {
        public DetailedHealthCheckResult(HealthCheck.Result result, Check check) {
            super(result, check);
        }

        @JsonProperty("details")
        public Map<String, Object> getDetails() {
            return result.getDetails();
        }
    }
}
