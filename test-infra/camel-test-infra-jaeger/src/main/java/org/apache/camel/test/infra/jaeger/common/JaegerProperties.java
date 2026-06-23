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
package org.apache.camel.test.infra.jaeger.common;

/**
 * @since 4.21
 */
public final class JaegerProperties {
    public static final String JAEGER_CONTAINER = "jaeger.container";
    public static final String HOST = "jaeger.host";
    public static final String COLLECTOR_GRPC_PORT = "jaeger.collector.grpc.port";
    public static final String COLLECTOR_HTTP_PORT = "jaeger.collector.http.port";
    public static final String QUERY_UI_PORT = "jaeger.query.ui.port";

    public static final int DEFAULT_COLLECTOR_GRPC_PORT = 4317;
    public static final int DEFAULT_COLLECTOR_HTTP_PORT = 4318;
    public static final int DEFAULT_QUERY_UI_PORT = 16686;

    private JaegerProperties() {
    }
}
