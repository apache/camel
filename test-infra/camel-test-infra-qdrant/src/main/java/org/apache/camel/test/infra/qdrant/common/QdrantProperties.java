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
package org.apache.camel.test.infra.qdrant.common;

public class QdrantProperties {
    public static final String INFRA_TYPE = "qdrant";
    public static final String QDRANT_HTTP_HOST = "qdrant.http.host";
    public static final String QDRANT_HTTP_PORT = "qdrant.http.port";
    public static final String QDRANT_GRPC_HOST = "qdrant.grpc.host";
    public static final String QDRANT_GRPC_PORT = "qdrant.grpc.port";
    public static final String QDRANT_API_KEY = "qdrant.apiKey";
    public static final String QDRANT_CONTAINER = "qdrant.container";

    private QdrantProperties() {
    }
}
