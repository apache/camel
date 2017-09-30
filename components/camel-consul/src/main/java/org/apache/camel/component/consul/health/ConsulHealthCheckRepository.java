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
package org.apache.camel.component.consul.health;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;
import java.util.stream.Stream;

import com.orbitz.consul.Consul;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheckConfiguration;
import org.apache.camel.health.HealthCheckRepository;
import org.apache.camel.health.HealthCheckResultBuilder;
import org.apache.camel.impl.health.AbstractHealthCheck;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.function.Suppliers;

public class ConsulHealthCheckRepository implements HealthCheckRepository, CamelContextAware {
    private final Supplier<Consul> client;
    private final ConcurrentMap<String, HealthCheck> checks;
    private ConsulHealthCheckRepositoryConfiguration configuration;
    private CamelContext camelContext;

    public ConsulHealthCheckRepository() {
        this(null);
    }

    private ConsulHealthCheckRepository(ConsulHealthCheckRepositoryConfiguration configuration) {
        this.checks = new ConcurrentHashMap<>();
        this.configuration = configuration;
        this.client = Suppliers.memorize(this::createConsul, ObjectHelper::wrapRuntimeCamelException);
    }

    // *************************************
    //
    // *************************************

    public ConsulHealthCheckRepositoryConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(ConsulHealthCheckRepositoryConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public Stream<HealthCheck> stream() {
        final Set<String> ids = configuration.getChecks();

        if (ObjectHelper.isNotEmpty(ids)) {
            return ids.stream()
                .map(checkId -> checks.computeIfAbsent(checkId, ConsulHealthCheck::new))
                .filter(check -> check.getConfiguration().isEnabled());
        }

        return Stream.empty();
    }

    // *************************************
    //
    // *************************************

    private Consul createConsul() throws Exception {
        ConsulHealthCheckRepositoryConfiguration conf = configuration;

        if (conf == null) {
            conf = new ConsulHealthCheckRepositoryConfiguration();
        }

        return conf.createConsulClient(camelContext);
    }

    private class ConsulHealthCheck extends AbstractHealthCheck {
        private final String checkId;

        ConsulHealthCheck(String checkId) {
            super("consul-" + checkId.replaceAll(":", "-"));

            this.checkId = checkId;

            HealthCheckConfiguration conf = configuration.getConfigurations().get(getId());
            if (conf == null) {
                conf = HealthCheckConfiguration.builder()
                    .complete(configuration.getDefaultConfiguration())
                    .build();
            }

            setConfiguration(conf);
        }

        @Override
        protected void doCall(HealthCheckResultBuilder builder, Map<String, Object> options) {
            builder.unknown();

            com.orbitz.consul.model.health.HealthCheck check = client.get().agentClient().getChecks().get(checkId);
            if (check != null) {

                // From Consul sources:
                // https://github.com/hashicorp/consul/blob/master/api/health.go#L8-L16
                //
                // States are:
                //
                // const (
                //     HealthAny is special, and is used as a wild card,
                //     not as a specific state.
                //     HealthAny      = "any"
                //     HealthPassing  = "passing"
                //     HealthWarning  = "warning"
                //     HealthCritical = "critical"
                //     HealthMaint    = "maintenance"
                // )
                if (ObjectHelper.equalIgnoreCase(check.getStatus(), "passing")) {
                    builder.up();
                }
                if (ObjectHelper.equalIgnoreCase(check.getStatus(), "warning")) {
                    builder.down();
                }
                if (ObjectHelper.equalIgnoreCase(check.getStatus(), "critical")) {
                    builder.down();
                }

                builder.detail("consul.service.name", check.getServiceName().orNull());
                builder.detail("consul.service.id", check.getServiceId().orNull());
                builder.detail("consul.check.status", check.getStatus());
                builder.detail("consul.check.id", check.getCheckId());
            }
        }
    }



    // ****************************************
    // Builder
    // ****************************************

    public static final class Builder implements org.apache.camel.Builder<ConsulHealthCheckRepository> {
        private HealthCheckConfiguration defaultConfiguration;
        private Set<String> checks;
        private Map<String, HealthCheckConfiguration> configurations;

        public Builder configuration(HealthCheckConfiguration defaultConfiguration) {
            this.defaultConfiguration = defaultConfiguration;
            return this;
        }

        public Builder configuration(String id, HealthCheckConfiguration configuration) {
            if (this.configurations == null) {
                this.configurations = new HashMap<>();
            }

            this.configurations.put(id, configuration);

            return this;
        }

        public Builder configurations(Map<String, HealthCheckConfiguration> configurations) {
            if (ObjectHelper.isNotEmpty(configurations)) {
                configurations.forEach(this::configuration);
            }

            return this;
        }

        public Builder check(String id) {
            if (ObjectHelper.isNotEmpty(id)) {
                if (this.checks == null) {
                    this.checks = new HashSet<>();
                }
                this.checks.add(id);
            }

            return this;
        }

        public Builder checks(Collection<String> ids) {
            if (ObjectHelper.isNotEmpty(ids)) {
                ids.forEach(this::check);
            }

            return this;
        }

        @Override
        public ConsulHealthCheckRepository build() {
            ConsulHealthCheckRepositoryConfiguration configuration = new ConsulHealthCheckRepositoryConfiguration();
            configuration.setDefaultConfiguration(defaultConfiguration);

            if (checks != null) {
                checks.forEach(configuration::addCheck);
            }
            if (configurations != null) {
                configurations.forEach(configuration::addConfiguration);
            }

            return new ConsulHealthCheckRepository(configuration);
        }
    }
}
