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
package org.apache.camel.component.torchserve.client;

import java.util.Optional;

import org.apache.camel.component.torchserve.client.impl.DefaultInference;
import org.apache.camel.component.torchserve.client.impl.DefaultManagement;
import org.apache.camel.component.torchserve.client.impl.DefaultMetrics;

public class TorchServeClient {

    private final Inference inference;
    private final Management management;
    private final Metrics metrics;

    private TorchServeClient(Inference inference, Management management, Metrics metrics) {
        this.inference = inference;
        this.management = management;
        this.metrics = metrics;
    }

    public static TorchServeClient newInstance() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public Inference inference() {
        return inference;
    }

    public Management management() {
        return management;
    }

    public Metrics metrics() {
        return metrics;
    }

    public static class Builder {

        private Optional<String> inferenceKey = Optional.empty();
        private Optional<String> inferenceAddress = Optional.empty();
        private Optional<Integer> inferencePort = Optional.empty();

        private Optional<String> managementKey = Optional.empty();
        private Optional<String> managementAddress = Optional.empty();
        private Optional<Integer> managementPort = Optional.empty();

        private Optional<String> metricsAddress = Optional.empty();
        private Optional<Integer> metricsPort = Optional.empty();

        public Builder inferenceKey(String key) {
            this.inferenceKey = Optional.of(key);
            return this;
        }

        public Builder inferenceAddress(String address) {
            this.inferenceAddress = Optional.of(address);
            return this;
        }

        public Builder inferencePort(int port) {
            this.inferencePort = Optional.of(port);
            return this;
        }

        public Builder managementKey(String key) {
            this.managementKey = Optional.of(key);
            return this;
        }

        public Builder managementAddress(String address) {
            this.managementAddress = Optional.of(address);
            return this;
        }

        public Builder managementPort(int port) {
            this.managementPort = Optional.of(port);
            return this;
        }

        public Builder metricsAddress(String address) {
            this.metricsAddress = Optional.of(address);
            return this;
        }

        public Builder metricsPort(Integer port) {
            this.metricsPort = Optional.of(port);
            return this;
        }

        public TorchServeClient build() {
            DefaultInference inference = inferenceAddress.map(DefaultInference::new)
                    .or(() -> inferencePort.map(DefaultInference::new))
                    .orElse(new DefaultInference());
            inferenceKey.ifPresent(inference::setAuthToken);

            DefaultManagement management = managementAddress.map(DefaultManagement::new)
                    .or(() -> managementPort.map(DefaultManagement::new))
                    .orElse(new DefaultManagement());
            managementKey.ifPresent(management::setAuthToken);

            DefaultMetrics metrics = metricsAddress.map(DefaultMetrics::new)
                    .or(() -> metricsPort.map(DefaultMetrics::new))
                    .orElse(new DefaultMetrics());
            return new TorchServeClient(inference, management, metrics);
        }
    }
}
