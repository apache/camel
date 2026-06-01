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

import org.apache.camel.test.infra.jaeger.common.JaegerProperties;

public class JaegerRemoteInfraService implements JaegerInfraService {

    @Override
    public void registerProperties() {
        // NO-OP
    }

    @Override
    public void initialize() {
        registerProperties();
    }

    @Override
    public void shutdown() {
        // NO-OP
    }

    @Override
    public String host() {
        return System.getProperty(JaegerProperties.HOST);
    }

    @Override
    public int collectorGrpcPort() {
        String port = System.getProperty(JaegerProperties.COLLECTOR_GRPC_PORT);
        return port == null ? JaegerProperties.DEFAULT_COLLECTOR_GRPC_PORT : Integer.parseInt(port);
    }

    @Override
    public int collectorHttpPort() {
        String port = System.getProperty(JaegerProperties.COLLECTOR_HTTP_PORT);
        return port == null ? JaegerProperties.DEFAULT_COLLECTOR_HTTP_PORT : Integer.parseInt(port);
    }

    @Override
    public int queryUiPort() {
        String port = System.getProperty(JaegerProperties.QUERY_UI_PORT);
        return port == null ? JaegerProperties.DEFAULT_QUERY_UI_PORT : Integer.parseInt(port);
    }
}
