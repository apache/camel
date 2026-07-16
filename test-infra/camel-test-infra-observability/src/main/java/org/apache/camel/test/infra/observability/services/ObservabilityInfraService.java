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
package org.apache.camel.test.infra.observability.services;

import org.apache.camel.test.infra.common.services.InfrastructureService;

public interface ObservabilityInfraService extends InfrastructureService {

    String host();

    int prometheusPort();

    int victoriaTracesPort();

    int victoriaLogsPort();

    int persesPort();

    default String prometheusUrl() {
        return String.format("http://%s:%d", host(), prometheusPort());
    }

    default String victoriaTracesUrl() {
        return String.format("http://%s:%d/select/vmui", host(), victoriaTracesPort());
    }

    default String victoriaTracesOtlpEndpoint() {
        return String.format("http://%s:%d/insert/opentelemetry/v1/traces", host(), victoriaTracesPort());
    }

    default String victoriaLogsUrl() {
        return String.format("http://%s:%d", host(), victoriaLogsPort());
    }

    default String victoriaLogsOtlpEndpoint() {
        return String.format("http://%s:%d/insert/opentelemetry/v1/logs", host(), victoriaLogsPort());
    }

    default String persesUrl() {
        return String.format("http://%s:%d", host(), persesPort());
    }

    default String metricsTarget() {
        return "http://host.docker.internal:9876/observe/metrics";
    }

    default String uiUrl() {
        return persesUrl();
    }
}
