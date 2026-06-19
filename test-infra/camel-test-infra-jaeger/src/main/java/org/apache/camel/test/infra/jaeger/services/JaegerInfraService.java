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
package org.apache.camel.test.infra.jaeger.services;

import org.apache.camel.test.infra.common.services.InfrastructureService;

/**
 * Test infra service for Jaeger distributed tracing
 *
 * @since 4.21
 */
public interface JaegerInfraService extends InfrastructureService {

    String host();

    int collectorGrpcPort();

    int collectorHttpPort();

    int queryUiPort();

    // OTLP gRPC exporters expect an http(s):// URL per the OTel spec, not a grpc:// scheme.
    default String collectorGrpcEndpoint() {
        return String.format("http://%s:%d", host(), collectorGrpcPort());
    }

    default String collectorHttpEndpoint() {
        return String.format("http://%s:%d", host(), collectorHttpPort());
    }

    default String queryUiUrl() {
        return String.format("http://%s:%d", host(), queryUiPort());
    }
}
